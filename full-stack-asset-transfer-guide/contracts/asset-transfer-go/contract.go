package main

import (
	"encoding/json"
	"fmt"
	"regexp"

	"github.com/hyperledger/fabric-chaincode-go/v2/pkg/statebased"
	"github.com/hyperledger/fabric-contract-api-go/v2/contractapi"
)

type SmartContract struct {
	contractapi.Contract
}

func (s *SmartContract) CreateAsset(ctx contractapi.TransactionContextInterface, asset Asset) error {
	if asset.ID == "" {
		return fmt.Errorf("Missing ID")
	}
	if asset.Owner == "" {
		return fmt.Errorf("Missing Owner")
	}

	exists, err := s.AssetExists(ctx, asset.ID)
	if err != nil {
		return err
	}
	if exists {
		return fmt.Errorf("The asset %s already exists", asset.ID)
	}

	ownerID, err := clientIdentifier(ctx, asset.Owner)
	if err != nil {
		return err
	}
	asset.Owner, err = toJSON(ownerID)
	if err != nil {
		return err
	}

	assetBytes, err := json.Marshal(asset)
	if err != nil {
		return fmt.Errorf("failed to marshal asset: %v", err)
	}

	if err := ctx.GetStub().PutState(asset.ID, assetBytes); err != nil {
		return fmt.Errorf("failed to put asset state: %v", err)
	}

	mspID, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return fmt.Errorf("failed to get client MSPID: %v", err)
	}
	if err := setEndorsingOrgs(ctx, asset.ID, mspID); err != nil {
		return err
	}

	return ctx.GetStub().SetEvent("CreateAsset", assetBytes)
}

func (s *SmartContract) ReadAsset(ctx contractapi.TransactionContextInterface, id string) (*Asset, error) {
	asset, err := s.readAsset(ctx, id)
	if err != nil {
		return nil, err
	}
	return asset, nil
}

func (s *SmartContract) readAsset(ctx contractapi.TransactionContextInterface, id string) (*Asset, error) {
	assetBytes, err := ctx.GetStub().GetState(id)
	if err != nil {
		return nil, fmt.Errorf("failed to read asset: %v", err)
	}
	if len(assetBytes) == 0 {
		return nil, fmt.Errorf("Sorry, asset %s has not been created", id)
	}

	var asset Asset
	if err := json.Unmarshal(assetBytes, &asset); err != nil {
		return nil, fmt.Errorf("failed to unmarshal asset: %v", err)
	}

	return &asset, nil
}

func (s *SmartContract) UpdateAsset(ctx contractapi.TransactionContextInterface, assetUpdate Asset) error {
	if assetUpdate.ID == "" {
		return fmt.Errorf("No asset ID specified")
	}

	existingAsset, err := s.readAsset(ctx, assetUpdate.ID)
	if err != nil {
		return err
	}

	permission, err := hasWritePermission(ctx, existingAsset)
	if err != nil {
		return err
	}
	if !permission {
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
	updatedAsset.Owner = existingAsset.Owner

	updatedBytes, err := json.Marshal(updatedAsset)
	if err != nil {
		return fmt.Errorf("failed to marshal updated asset: %v", err)
	}

	if err := ctx.GetStub().PutState(updatedAsset.ID, updatedBytes); err != nil {
		return fmt.Errorf("failed to update asset state: %v", err)
	}

	mspID, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return fmt.Errorf("failed to get client MSPID: %v", err)
	}
	if err := setEndorsingOrgs(ctx, updatedAsset.ID, mspID); err != nil {
		return err
	}

	return ctx.GetStub().SetEvent("UpdateAsset", updatedBytes)
}

func (s *SmartContract) DeleteAsset(ctx contractapi.TransactionContextInterface, id string) error {
	asset, err := s.readAsset(ctx, id)
	if err != nil {
		return err
	}

	permission, err := hasWritePermission(ctx, asset)
	if err != nil {
		return err
	}
	if !permission {
		return fmt.Errorf("Only owner can delete assets")
	}

	assetBytes, err := json.Marshal(asset)
	if err != nil {
		return fmt.Errorf("failed to marshal deleted asset: %v", err)
	}

	if err := ctx.GetStub().DelState(id); err != nil {
		return fmt.Errorf("failed to delete asset state: %v", err)
	}

	if err := ctx.GetStub().SetEvent("DeleteAsset", assetBytes); err != nil {
		return fmt.Errorf("failed to set delete event: %v", err)
	}

	return nil
}

func (s *SmartContract) AssetExists(ctx contractapi.TransactionContextInterface, id string) (bool, error) {
	assetJSON, err := ctx.GetStub().GetState(id)
	if err != nil {
		return false, fmt.Errorf("failed to read asset state: %v", err)
	}

	return len(assetJSON) > 0, nil
}

func (s *SmartContract) TransferAsset(ctx contractapi.TransactionContextInterface, id string, newOwner string, newOwnerOrg string) error {
	asset, err := s.readAsset(ctx, id)
	if err != nil {
		return err
	}

	permission, err := hasWritePermission(ctx, asset)
	if err != nil {
		return err
	}
	if !permission {
		return fmt.Errorf("Only owner can transfer assets")
	}

	ownerID := ownerIdentifier{Org: newOwnerOrg, User: newOwner}
	asset.Owner, err = toJSON(ownerID)
	if err != nil {
		return err
	}

	assetBytes, err := json.Marshal(asset)
	if err != nil {
		return fmt.Errorf("failed to marshal transferred asset: %v", err)
	}

	if err := ctx.GetStub().PutState(id, assetBytes); err != nil {
		return fmt.Errorf("failed to update asset state: %v", err)
	}

	if err := setEndorsingOrgs(ctx, id, newOwnerOrg); err != nil {
		return err
	}

	return ctx.GetStub().SetEvent("TransferAsset", assetBytes)
}

func (s *SmartContract) GetAllAssets(ctx contractapi.TransactionContextInterface) ([]*Asset, error) {
	iterator, err := ctx.GetStub().GetStateByRange("", "")
	if err != nil {
		return nil, fmt.Errorf("failed to get assets by range: %v", err)
	}
	defer iterator.Close()

	var assets []*Asset
	for iterator.HasNext() {
		queryResponse, err := iterator.Next()
		if err != nil {
			return nil, fmt.Errorf("failed to iterate assets: %v", err)
		}

		var asset Asset
		if err := json.Unmarshal(queryResponse.Value, &asset); err != nil {
			continue
		}
		assets = append(assets, &asset)
	}

	return assets, nil
}

func hasWritePermission(ctx contractapi.TransactionContextInterface, asset *Asset) (bool, error) {
	clientID, err := clientIdentifier(ctx, "")
	if err != nil {
		return false, err
	}

	var owner ownerIdentifier
	if err := json.Unmarshal([]byte(asset.Owner), &owner); err != nil {
		return false, fmt.Errorf("failed to unmarshal owner identifier: %v", err)
	}

	return clientID.Org == owner.Org, nil
}

func clientIdentifier(ctx contractapi.TransactionContextInterface, user string) (ownerIdentifier, error) {
	mspID, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return ownerIdentifier{}, fmt.Errorf("failed to get client MSPID: %v", err)
	}

	if user == "" {
		commonName, err := clientCommonName(ctx)
		if err != nil {
			return ownerIdentifier{}, err
		}
		user = commonName
	}

	return ownerIdentifier{Org: mspID, User: user}, nil
}

func clientCommonName(ctx contractapi.TransactionContextInterface) (string, error) {
	cert, err := ctx.GetClientIdentity().GetX509Certificate()
	if err != nil {
		return "", fmt.Errorf("failed to read client certificate: %v", err)
	}

	if cert.Subject.CommonName != "" {
		return cert.Subject.CommonName, nil
	}

	matches := regexp.MustCompile(`^CN=(.*)$`).FindStringSubmatch(cert.Subject.String())
	if len(matches) != 2 {
		return "", fmt.Errorf("unable to identify client identity common name: %s", cert.Subject.String())
	}

	return matches[1], nil
}

func ownerIdentifier(user string, org string) ownerIdentifier {
	return ownerIdentifier{Org: org, User: user}
}

func toJSON(o interface{}) (string, error) {
	bytes, err := json.Marshal(o)
	if err != nil {
		return "", fmt.Errorf("failed to marshal owner identifier: %v", err)
	}
	return string(bytes), nil
}

func setEndorsingOrgs(ctx contractapi.TransactionContextInterface, ledgerKey string, orgs ...string) error {
	policy, err := statebased.NewStateEP(nil)
	if err != nil {
		return fmt.Errorf("failed to create state endorsement policy: %v", err)
	}

	if err := policy.AddOrgs(statebased.RoleTypePeer, orgs...); err != nil {
		return fmt.Errorf("failed to add orgs to endorsement policy: %v", err)
	}

	policyBytes, err := policy.Policy()
	if err != nil {
		return fmt.Errorf("failed to marshal endorsement policy: %v", err)
	}

	return ctx.GetStub().SetStateValidationParameter(ledgerKey, policyBytes)
}
