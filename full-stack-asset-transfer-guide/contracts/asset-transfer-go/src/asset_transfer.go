/*
 * SPDX-License-Identifier: Apache-2.0
 */

package main

import (
	"encoding/json"
	"fmt"
	"log"

	"github.com/hyperledger/fabric-chaincode-go/v2/pkg/cid"
	"github.com/hyperledger/fabric-chaincode-go/v2/pkg/statebased"
	"github.com/hyperledger/fabric-contract-api-go/v2/contractapi"
)

// SmartContract provides functions for managing assets.
type SmartContract struct {
	contractapi.Contract
}

func (s *SmartContract) CreateAsset(ctx contractapi.TransactionContextInterface, id string, color string, size int, owner string, appraisedValue int) error {
	exists, err := s.AssetExists(ctx, id)
	if err != nil {
		return err
	}
	if exists {
		return fmt.Errorf("the asset %s already exists", id)
	}

	ownerID, err := clientIdentifier(ctx, owner)
	if err != nil {
		return err
	}
	ownerJSON, err := json.Marshal(ownerID)
	if err != nil {
		return err
	}

	asset := Asset{
		AppraisedValue: appraisedValue,
		Color:          color,
		ID:             id,
		Owner:          string(ownerJSON),
		Size:           size,
	}
	assetBytes, err := json.Marshal(asset)
	if err != nil {
		return err
	}

	if err := ctx.GetStub().PutState(id, assetBytes); err != nil {
		return err
	}

	mspID, err := cid.GetMSPID(ctx.GetStub())
	if err != nil {
		return err
	}
	if err := setEndorsingOrgs(ctx, id, mspID); err != nil {
		return err
	}

	return ctx.GetStub().SetEvent("CreateAsset", assetBytes)
}

func (s *SmartContract) ReadAsset(ctx contractapi.TransactionContextInterface, id string) (*Asset, error) {
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

// UpdateAsset updates color, size, and appraised value of an existing asset.
// The asset owner cannot be changed here; use TransferAsset instead.
func (s *SmartContract) UpdateAsset(ctx contractapi.TransactionContextInterface, id string, color string, size int, appraisedValue int) error {
	assetBytes, err := readAsset(ctx, id)
	if err != nil {
		return err
	}

	var existing Asset
	if err := json.Unmarshal(assetBytes, &existing); err != nil {
		return err
	}

	ok, err := hasWritePermission(ctx, &existing)
	if err != nil {
		return err
	}
	if !ok {
		return fmt.Errorf("only owner can update assets")
	}

	// Owner is intentionally preserved; use TransferAsset to change owner.
	existing.Color = color
	existing.Size = size
	existing.AppraisedValue = appraisedValue

	updatedBytes, err := json.Marshal(existing)
	if err != nil {
		return err
	}

	if err := ctx.GetStub().PutState(id, updatedBytes); err != nil {
		return err
	}

	mspID, err := cid.GetMSPID(ctx.GetStub())
	if err != nil {
		return err
	}
	if err := setEndorsingOrgs(ctx, id, mspID); err != nil {
		return err
	}

	return ctx.GetStub().SetEvent("UpdateAsset", updatedBytes)
}

// DeleteAsset deletes an asset from the world state.
func (s *SmartContract) DeleteAsset(ctx contractapi.TransactionContextInterface, id string) error {
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

	return ctx.GetStub().SetEvent("DeleteAsset", assetBytes)
}

// AssetExists returns true when an asset with the given ID exists in the world state.
func (s *SmartContract) AssetExists(ctx contractapi.TransactionContextInterface, id string) (bool, error) {
	assetBytes, err := ctx.GetStub().GetState(id)
	if err != nil {
		return false, fmt.Errorf("failed to read from world state: %v", err)
	}

	return assetBytes != nil, nil
}

// TransferAsset updates the owner of an asset with the given ID.
// newOwner is the user identifier; newOwnerOrg is the MSP ID of the new owning organisation.
// Subsequent updates must be endorsed by the new owning organisation.
func (s *SmartContract) TransferAsset(ctx contractapi.TransactionContextInterface, id string, newOwner string, newOwnerOrg string) error {
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

	newOwnerID := OwnerIdentifier{Org: newOwnerOrg, User: newOwner}
	ownerJSON, err := json.Marshal(newOwnerID)
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

	if err := setEndorsingOrgs(ctx, id, newOwnerOrg); err != nil {
		return err
	}

	return ctx.GetStub().SetEvent("TransferAsset", updatedBytes)
}

// GetAllAssets returns all assets found in the world state.
func (s *SmartContract) GetAllAssets(ctx contractapi.TransactionContextInterface) ([]*Asset, error) {
	// Range query with empty start/end key returns all assets in the chaincode namespace.
	resultsIterator, err := ctx.GetStub().GetStateByRange("", "")
	if err != nil {
		return nil, err
	}
	defer resultsIterator.Close()

	var assets []*Asset
	for resultsIterator.HasNext() {
		queryResponse, err := resultsIterator.Next()
		if err != nil {
			return nil, err
		}

		var asset Asset
		if err := json.Unmarshal(queryResponse.Value, &asset); err != nil {
			log.Printf("skipping malformed asset entry: %v", err)
			continue
		}
		assets = append(assets, &asset)
	}

	return assets, nil
}

// --- internal helpers -----------------------

func readAsset(ctx contractapi.TransactionContextInterface, id string) ([]byte, error) {
	assetBytes, err := ctx.GetStub().GetState(id)
	if err != nil {
		return nil, fmt.Errorf("failed to read from world state: %v", err)
	}
	if assetBytes == nil {
		return nil, fmt.Errorf("sorry, asset %s has not been created", id)
	}

	return assetBytes, nil
}

func hasWritePermission(ctx contractapi.TransactionContextInterface, asset *Asset) (bool, error) {
	clientID, err := clientIdentifier(ctx, "")
	if err != nil {
		return false, err
	}

	var ownerID OwnerIdentifier
	if err := json.Unmarshal([]byte(asset.Owner), &ownerID); err != nil {
		return false, fmt.Errorf("failed to parse asset owner: %v", err)
	}

	return clientID.Org == ownerID.Org, nil
}

func clientIdentifier(ctx contractapi.TransactionContextInterface, user string) (OwnerIdentifier, error) {
	mspID, err := cid.GetMSPID(ctx.GetStub())
	if err != nil {
		return OwnerIdentifier{}, err
	}

	if user == "" {
		cn, err := clientCommonName(ctx)
		if err != nil {
			return OwnerIdentifier{}, err
		}
		user = cn
	}

	return OwnerIdentifier{Org: mspID, User: user}, nil
}

func clientCommonName(ctx contractapi.TransactionContextInterface) (string, error) {
	cert, err := cid.GetX509Certificate(ctx.GetStub())
	if err != nil {
		return "", err
	}
	if cert.Subject.CommonName == "" {
		return "", fmt.Errorf("unable to identify client identity common name")
	}

	return cert.Subject.CommonName, nil
}

func setEndorsingOrgs(ctx contractapi.TransactionContextInterface, key string, orgs ...string) error {
	ep, err := statebased.NewStateEP(nil)
	if err != nil {
		return err
	}
	if err := ep.AddOrgs(statebased.RoleTypeMember, orgs...); err != nil {
		return err
	}
	policy, err := ep.Policy()
	if err != nil {
		return err
	}

	return ctx.GetStub().SetStateValidationParameter(key, policy)
}
