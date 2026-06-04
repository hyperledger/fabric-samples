/*
SPDX-License-Identifier: Apache-2.0
*/

package smartcontract

import (
	"encoding/json"
	"fmt"
	"log"

	"github.com/hyperledger/fabric-contract-api-go/v2/contractapi"
)

type SmartContract struct {
	contractapi.Contract
}

func (s *SmartContract) CreateAsset(ctx contractapi.TransactionContextInterface, asset *Asset) error {
	if asset == nil {
		return fmt.Errorf("asset cannot be nil")
	}

	if asset.ID == "" {
		return fmt.Errorf("Missing ID")
	}

	ownerIdentifier, err := clientIdentifierWithUser(ctx, asset.Owner)
	if err != nil {
		return err
	}

	asset.Owner, err = marshalOwnerIdentifier(ownerIdentifier)
	if err != nil {
		return err
	}

	exists, err := s.AssetExists(ctx, asset.ID)
	if err != nil {
		return err
	}
	if exists {
		return fmt.Errorf("The asset %s already exists", asset.ID)
	}

	assetBytes, err := json.Marshal(asset)
	if err != nil {
		return fmt.Errorf("failed to marshal asset: %v", err)
	}

	if err := ctx.GetStub().PutState(asset.ID, assetBytes); err != nil {
		return fmt.Errorf("failed to put asset in world state: %v", err)
	}

	clientMSP, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return fmt.Errorf("failed to get client MSP ID: %v", err)
	}

	if err := setStateBasedEndorsement(ctx, asset.ID, clientMSP); err != nil {
		return err
	}

	if err := ctx.GetStub().SetEvent("CreateAsset", assetBytes); err != nil {
		return fmt.Errorf("failed to set event CreateAsset: %v", err)
	}

	return nil
}

func (s *SmartContract) ReadAsset(ctx contractapi.TransactionContextInterface, id string) (*Asset, error) {
	assetBytes, err := s.readAsset(ctx, id)
	if err != nil {
		return nil, err
	}

	var asset Asset
	if err := json.Unmarshal(assetBytes, &asset); err != nil {
		return nil, fmt.Errorf("failed to unmarshal asset: %v", err)
	}

	return &asset, nil
}

func (s *SmartContract) UpdateAsset(ctx contractapi.TransactionContextInterface, assetUpdate *Asset) error {
	if assetUpdate == nil {
		return fmt.Errorf("asset cannot be nil")
	}
	if assetUpdate.ID == "" {
		return fmt.Errorf("No asset ID specified")
	}

	existingAsset, err := s.ReadAsset(ctx, assetUpdate.ID)
	if err != nil {
		return err
	}

	allowed, err := hasWritePermission(ctx, existingAsset)
	if err != nil {
		return err
	}
	if !allowed {
		return fmt.Errorf("Only owner can update assets")
	}

	updatedAsset := *existingAsset
	if assetUpdate.Color != "" {
		updatedAsset.Color = assetUpdate.Color
	}
	if assetUpdate.Size != 0 {
		updatedAsset.Size = assetUpdate.Size
	}
	if assetUpdate.AppraisedValue != 0 {
		updatedAsset.AppraisedValue = assetUpdate.AppraisedValue
	}

	assetBytes, err := json.Marshal(&updatedAsset)
	if err != nil {
		return fmt.Errorf("failed to marshal updated asset: %v", err)
	}

	if err := ctx.GetStub().PutState(updatedAsset.ID, assetBytes); err != nil {
		return fmt.Errorf("failed to put asset in world state: %v", err)
	}

	clientMSP, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return fmt.Errorf("failed to get client MSP ID: %v", err)
	}

	if err := setStateBasedEndorsement(ctx, updatedAsset.ID, clientMSP); err != nil {
		return err
	}

	if err := ctx.GetStub().SetEvent("UpdateAsset", assetBytes); err != nil {
		return fmt.Errorf("failed to set event UpdateAsset: %v", err)
	}

	return nil
}

func (s *SmartContract) DeleteAsset(ctx contractapi.TransactionContextInterface, id string) error {
	asset, err := s.ReadAsset(ctx, id)
	if err != nil {
		return err
	}

	allowed, err := hasWritePermission(ctx, asset)
	if err != nil {
		return err
	}
	if !allowed {
		return fmt.Errorf("Only owner can delete assets")
	}

	if err := ctx.GetStub().DelState(id); err != nil {
		return fmt.Errorf("failed to delete asset from world state: %v", err)
	}

	assetBytes, err := json.Marshal(asset)
	if err != nil {
		return fmt.Errorf("failed to marshal asset event payload: %v", err)
	}

	if err := ctx.GetStub().SetEvent("DeleteAsset", assetBytes); err != nil {
		return fmt.Errorf("failed to set event DeleteAsset: %v", err)
	}

	return nil
}

func (s *SmartContract) AssetExists(ctx contractapi.TransactionContextInterface, id string) (bool, error) {
	assetBytes, err := ctx.GetStub().GetState(id)
	if err != nil {
		return false, fmt.Errorf("failed to read from world state: %v", err)
	}

	return len(assetBytes) > 0, nil
}

func (s *SmartContract) TransferAsset(ctx contractapi.TransactionContextInterface, id string, newOwner string, newOwnerOrg string) error {
	asset, err := s.ReadAsset(ctx, id)
	if err != nil {
		return err
	}

	allowed, err := hasWritePermission(ctx, asset)
	if err != nil {
		return err
	}
	if !allowed {
		return fmt.Errorf("Only owner can transfer assets")
	}

	asset.Owner, err = marshalOwnerIdentifier(ownerIdentifier(newOwner, newOwnerOrg))
	if err != nil {
		return err
	}

	assetBytes, err := json.Marshal(asset)
	if err != nil {
		return fmt.Errorf("failed to marshal transferred asset: %v", err)
	}

	if err := ctx.GetStub().PutState(id, assetBytes); err != nil {
		return fmt.Errorf("failed to put asset in world state: %v", err)
	}

	if err := setStateBasedEndorsement(ctx, id, newOwnerOrg); err != nil {
		return err
	}

	if err := ctx.GetStub().SetEvent("TransferAsset", assetBytes); err != nil {
		return fmt.Errorf("failed to set event TransferAsset: %v", err)
	}

	return nil
}

func (s *SmartContract) GetAllAssets(ctx contractapi.TransactionContextInterface) ([]*Asset, error) {
	iterator, err := ctx.GetStub().GetStateByRange("", "")
	if err != nil {
		return nil, fmt.Errorf("failed to retrieve all assets: %v", err)
	}
	defer func() {
		_ = iterator.Close()
	}()

	assets := make([]*Asset, 0)
	for iterator.HasNext() {
		queryResponse, err := iterator.Next()
		if err != nil {
			return nil, fmt.Errorf("failed retrieving next item: %v", err)
		}

		var asset Asset
		if err := json.Unmarshal(queryResponse.Value, &asset); err != nil {
			log.Printf("failed to unmarshal asset from world state: %v", err)
			continue
		}
		assets = append(assets, &asset)
	}

	return assets, nil
}

func (s *SmartContract) readAsset(ctx contractapi.TransactionContextInterface, id string) ([]byte, error) {
	assetBytes, err := ctx.GetStub().GetState(id)
	if err != nil {
		return nil, fmt.Errorf("failed to read from world state: %v", err)
	}

	if len(assetBytes) == 0 {
		return nil, fmt.Errorf("Sorry, asset %s has not been created", id)
	}

	return assetBytes, nil
}
