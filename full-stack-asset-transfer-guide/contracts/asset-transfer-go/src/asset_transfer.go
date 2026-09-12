/*
 * SPDX-License-Identifier: Apache-2.0
 */

package main

import (
	"encoding/json"
	"fmt"
	"log"

	"github.com/hyperledger/fabric-chaincode-go/v2/pkg/cid"
	"github.com/hyperledger/fabric-contract-api-go/v2/contractapi"
)

// SmartContract provides functions for managing assets.
type SmartContract struct {
	contractapi.Contract
}

// CreateAsset issues a new asset to the world state with given details.
func (s *SmartContract) CreateAsset(
	ctx contractapi.TransactionContextInterface,
	state *Asset,
) error {

	if state == nil {
		return fmt.Errorf("asset cannot be nil")
	}

	ownerID, err := clientIdentifier(ctx, state.Owner)
	if err != nil {
		return err
	}

	ownerJSON, err := json.Marshal(ownerID)
	if err != nil {
		return err
	}
	state.Owner = string(ownerJSON)

	asset, err := NewAsset(*state)
	if err != nil {
		return err
	}

	exists, err := s.AssetExists(ctx, asset.ID)
	if err != nil {
		return err
	}
	if exists {
		return fmt.Errorf("the asset %s already exists", asset.ID)
	}

	assetBytes, err := json.Marshal(asset)
	if err != nil {
		return err
	}

	if err := ctx.GetStub().PutState(asset.ID, assetBytes); err != nil {
		return err
	}

	mspID, err := cid.GetMSPID(ctx.GetStub())
	if err != nil {
		return err
	}

	if err := setEndorsingOrgs(ctx, asset.ID, mspID); err != nil {
		return err
	}

	return ctx.GetStub().SetEvent("CreateAsset", assetBytes)
}

// ReadAsset returns an existing asset stored in the world state.
func (s *SmartContract) ReadAsset(
	ctx contractapi.TransactionContextInterface,
	id string,
) (*Asset, error) {

	assetBytes, err := readAsset(ctx, id)
	if err != nil {
		return nil, err
	}

	var asset Asset
	if err := json.Unmarshal(assetBytes, &asset); err != nil {
		return nil, err
	}

	return &asset, nil
}

// readAsset returns the raw asset bytes.
func readAsset(
	ctx contractapi.TransactionContextInterface,
	id string,
) ([]byte, error) {

	assetBytes, err := ctx.GetStub().GetState(id)
	if err != nil {
		return nil, fmt.Errorf("failed to read from world state: %v", err)
	}

	if assetBytes == nil {
		return nil, fmt.Errorf("sorry, asset %s has not been created", id)
	}

	return assetBytes, nil
}

// UpdateAsset updates an existing asset in the world state with the
// provided partial asset data. The asset ID must be specified.
// The Owner field cannot be changed here; use TransferAsset instead.
func (s *SmartContract) UpdateAsset(
	ctx contractapi.TransactionContextInterface,
	assetUpdate *Asset,
) error {

	if assetUpdate == nil {
		return fmt.Errorf("asset cannot be nil")
	}

	if assetUpdate.ID == "" {
		return fmt.Errorf("no asset ID specified")
	}

	existingAssetBytes, err := readAsset(ctx, assetUpdate.ID)
	if err != nil {
		return err
	}

	var existingAsset Asset
	if err := json.Unmarshal(existingAssetBytes, &existingAsset); err != nil {
		return err
	}

	ok, err := hasWritePermission(ctx, &existingAsset)
	if err != nil {
		return err
	}
	if !ok {
		return fmt.Errorf("only owner can update assets")
	}

	// Merge the update into the existing asset.
	// Preserve the owner; ownership changes must go through TransferAsset.
	if assetUpdate.Color != "" {
		existingAsset.Color = assetUpdate.Color
	}

	if assetUpdate.Size != 0 {
		existingAsset.Size = assetUpdate.Size
	}

	if assetUpdate.AppraisedValue != 0 {
		existingAsset.AppraisedValue = assetUpdate.AppraisedValue
	}

	updatedAsset, err := NewAsset(existingAsset)
	if err != nil {
		return err
	}

	updatedBytes, err := json.Marshal(updatedAsset)
	if err != nil {
		return err
	}

	if err := ctx.GetStub().PutState(updatedAsset.ID, updatedBytes); err != nil {
		return err
	}

	mspID, err := cid.GetMSPID(ctx.GetStub())
	if err != nil {
		return err
	}

	if err := setEndorsingOrgs(ctx, updatedAsset.ID, mspID); err != nil {
		return err
	}

	return ctx.GetStub().SetEvent("UpdateAsset", updatedBytes)
}

// DeleteAsset deletes an asset from the world state.
func (s *SmartContract) DeleteAsset(
	ctx contractapi.TransactionContextInterface,
	id string,
) error {

	assetBytes, err := readAsset(ctx, id)
	if err != nil {
		return err
	}

	var asset Asset
	if err := json.Unmarshal(assetBytes, &asset); err != nil {
		return err
	}

	ok, err := hasWritePermission(ctx, &asset)
	if err != nil {
		return err
	}
	if !ok {
		return fmt.Errorf("only owner can delete assets")
	}

	if err := ctx.GetStub().DelState(id); err != nil {
		return err
	}

	// Matches the TypeScript event name (including its typo).
	return ctx.GetStub().SetEvent("DeletaAsset", assetBytes)
}

// AssetExists returns true when an asset with the given ID exists.
func (s *SmartContract) AssetExists(
	ctx contractapi.TransactionContextInterface,
	id string,
) (bool, error) {

	assetBytes, err := ctx.GetStub().GetState(id)
	if err != nil {
		return false, err
	}

	return assetBytes != nil, nil
}

// TransferAsset updates the owner field of an asset.
func (s *SmartContract) TransferAsset(
	ctx contractapi.TransactionContextInterface,
	id string,
	newOwner string,
	newOwnerOrg string,
) error {

	assetBytes, err := readAsset(ctx, id)
	if err != nil {
		return err
	}

	var asset Asset
	if err := json.Unmarshal(assetBytes, &asset); err != nil {
		return err
	}

	ok, err := hasWritePermission(ctx, &asset)
	if err != nil {
		return err
	}
	if !ok {
		return fmt.Errorf("only owner can transfer assets")
	}

	ownerID := ownerIdentifier(newOwner, newOwnerOrg)

	ownerJSON, err := json.Marshal(ownerID)
	if err != nil {
		return err
	}

	asset.Owner = string(ownerJSON)

	updatedBytes, err := json.Marshal(asset)
	if err != nil {
		return err
	}

	if err := ctx.GetStub().PutState(id, updatedBytes); err != nil {
		return err
	}

	// Subsequent updates must be endorsed by the new owning organization.
	if err := setEndorsingOrgs(ctx, id, newOwnerOrg); err != nil {
		return err
	}

	return ctx.GetStub().SetEvent("TransferAsset", updatedBytes)
}

// GetAllAssets returns a list of all assets in the world state.
func (s *SmartContract) GetAllAssets(
	ctx contractapi.TransactionContextInterface,
) (string, error) {

	resultsIterator, err := ctx.GetStub().GetStateByRange("", "")
	if err != nil {
		return "", err
	}
	defer resultsIterator.Close()

	var assets []Asset

	for resultsIterator.HasNext() {
		queryResponse, err := resultsIterator.Next()
		if err != nil {
			return "", err
		}

		var asset Asset
		if err := json.Unmarshal(queryResponse.Value, &asset); err != nil {
			log.Printf("failed to unmarshal asset: %v", err)
			continue
		}

		assets = append(assets, asset)
	}

	assetBytes, err := marshal(assets)
	if err != nil {
		return "", err
	}

	return string(assetBytes), nil
}

// unmarshal parses JSON into the supplied destination.
func unmarshal(data []byte, v any) error {
	if len(data) == 0 {
		return fmt.Errorf("empty JSON")
	}

	return json.Unmarshal(data, v)
}

// marshal serializes an object into JSON.
func marshal(v any) ([]byte, error) {
	return json.Marshal(v)
}

// toJSON returns a JSON string representation.
func toJSON(v any) (string, error) {
	bytes, err := marshal(v)
	if err != nil {
		return "", err
	}

	return string(bytes), nil
}
