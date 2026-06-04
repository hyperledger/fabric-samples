package main

import (
	"encoding/json"
	"fmt"

	"github.com/hyperledger/fabric-chaincode-go/v2/pkg/statebased"
	"github.com/hyperledger/fabric-contract-api-go/v2/contractapi"
)

type SmartContract struct {
	contractapi.Contract
}

type Asset struct {
	AppraisedValue int    `json:"AppraisedValue"`
	Color          string `json:"Color"`
	ID             string `json:"ID"`
	Owner          string `json:"Owner"`
	Size           int    `json:"Size"`
}

type OwnerIdentifier struct {
	Org  string `json:"org"`
	User string `json:"user"`
}

type AssetCreateInput struct {
	ID             string  `json:"ID"`
	Color          string  `json:"Color,omitempty"`
	Owner          *string `json:"Owner,omitempty"`
	AppraisedValue *int    `json:"AppraisedValue,omitempty"`
	Size           *int    `json:"Size,omitempty"`
}

type AssetUpdateInput struct {
	ID             string  `json:"ID"`
	Color          *string `json:"Color,omitempty"`
	AppraisedValue *int    `json:"AppraisedValue,omitempty"`
	Size           *int    `json:"Size,omitempty"`
	Owner          *string `json:"Owner,omitempty"`
}

func (s *SmartContract) CreateAsset(ctx contractapi.TransactionContextInterface, assetJSON string) error {
	input, err := parseCreateInput(assetJSON)
	if err != nil {
		return err
	}

	exists, err := s.AssetExists(ctx, input.ID)
	if err != nil {
		return err
	}
	if exists {
		return fmt.Errorf("the asset %s already exists", input.ID)
	}

	owner, err := clientIdentifier(ctx, input.Owner)
	if err != nil {
		return err
	}

	asset := Asset{
		ID:             input.ID,
		Color:          input.Color,
		Size:           valueOrDefault(input.Size, 0),
		Owner:          marshalOwner(owner),
		AppraisedValue: valueOrDefault(input.AppraisedValue, 0),
	}

	assetBytes, err := json.Marshal(asset)
	if err != nil {
		return err
	}

	if err := ctx.GetStub().PutState(asset.ID, assetBytes); err != nil {
		return fmt.Errorf("failed to put asset in world state: %w", err)
	}

	if err := setEndorsingOrgs(ctx, asset.ID, owner.Org); err != nil {
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

func (s *SmartContract) UpdateAsset(ctx contractapi.TransactionContextInterface, assetJSON string) error {
	input, err := parseUpdateInput(assetJSON)
	if err != nil {
		return err
	}

	existingAsset, err := s.readAsset(ctx, input.ID)
	if err != nil {
		return err
	}

	if err := hasWritePermission(ctx, existingAsset); err != nil {
		return err
	}

	updatedAsset := *existingAsset
	if input.Color != nil {
		updatedAsset.Color = *input.Color
	}
	if input.Size != nil {
		updatedAsset.Size = *input.Size
	}
	if input.AppraisedValue != nil {
		updatedAsset.AppraisedValue = *input.AppraisedValue
	}
	// Owner cannot be updated via UpdateAsset. TransferAsset must be used for ownership changes.

	assetBytes, err := json.Marshal(updatedAsset)
	if err != nil {
		return err
	}

	if err := ctx.GetStub().PutState(updatedAsset.ID, assetBytes); err != nil {
		return fmt.Errorf("failed to put updated asset in world state: %w", err)
	}

	clientOrg, err := getClientOrg(ctx)
	if err != nil {
		return err
	}
	if err := setEndorsingOrgs(ctx, updatedAsset.ID, clientOrg); err != nil {
		return err
	}

	if err := ctx.GetStub().SetEvent("UpdateAsset", assetBytes); err != nil {
		return err
	}

	return nil
}

func (s *SmartContract) DeleteAsset(ctx contractapi.TransactionContextInterface, id string) error {
	asset, err := s.readAsset(ctx, id)
	if err != nil {
		return err
	}

	if err := hasWritePermission(ctx, asset); err != nil {
		return err
	}

	if err := ctx.GetStub().DelState(id); err != nil {
		return fmt.Errorf("failed to delete asset %s: %w", id, err)
	}

	return ctx.GetStub().SetEvent("DeleteAsset", []byte(id))
}

func (s *SmartContract) AssetExists(ctx contractapi.TransactionContextInterface, id string) (bool, error) {
	assetBytes, err := ctx.GetStub().GetState(id)
	if err != nil {
		return false, fmt.Errorf("failed to read world state: %w", err)
	}

	return len(assetBytes) > 0, nil
}

func (s *SmartContract) TransferAsset(ctx contractapi.TransactionContextInterface, id string, newOwner string, newOwnerOrg string) error {
	asset, err := s.readAsset(ctx, id)
	if err != nil {
		return err
	}

	if err := hasWritePermission(ctx, asset); err != nil {
		return err
	}

	owner := ownerIdentifier(newOwner, newOwnerOrg)
	ownerJSON, err := json.Marshal(owner)
	if err != nil {
		return err
	}
	asset.Owner = string(ownerJSON)

	assetBytes, err := json.Marshal(asset)
	if err != nil {
		return err
	}

	if err := ctx.GetStub().PutState(id, assetBytes); err != nil {
		return fmt.Errorf("failed to update asset owner in world state: %w", err)
	}

	if err := setEndorsingOrgs(ctx, id, newOwnerOrg); err != nil {
		return err
	}

	return ctx.GetStub().SetEvent("TransferAsset", assetBytes)
}

func (s *SmartContract) GetAllAssets(ctx contractapi.TransactionContextInterface) ([]Asset, error) {
	resultsIterator, err := ctx.GetStub().GetStateByRange("", "")
	if err != nil {
		return nil, err
	}
	defer resultsIterator.Close()

	var assets []Asset
	for resultsIterator.HasNext() {
		queryResponse, err := resultsIterator.Next()
		if err != nil {
			return nil, err
		}

		var asset Asset
		if err := json.Unmarshal(queryResponse.Value, &asset); err != nil {
			continue
		}
		assets = append(assets, asset)
	}

	return assets, nil
}

func (s *SmartContract) readAsset(ctx contractapi.TransactionContextInterface, id string) (*Asset, error) {
	assetBytes, err := ctx.GetStub().GetState(id)
	if err != nil {
		return nil, fmt.Errorf("failed to read world state: %w", err)
	}
	if assetBytes == nil || len(assetBytes) == 0 {
		return nil, fmt.Errorf("sorry, asset %s has not been created", id)
	}

	var asset Asset
	if err := json.Unmarshal(assetBytes, &asset); err != nil {
		return nil, fmt.Errorf("failed to unmarshal asset %s: %w", id, err)
	}

	return &asset, nil
}

func parseCreateInput(assetJSON string) (*AssetCreateInput, error) {
	var input AssetCreateInput
	if err := json.Unmarshal([]byte(assetJSON), &input); err != nil {
		return nil, fmt.Errorf("failed to parse asset JSON: %w", err)
	}

	if input.ID == "" {
		return nil, fmt.Errorf("missing ID")
	}

	return &input, nil
}

func parseUpdateInput(assetJSON string) (*AssetUpdateInput, error) {
	var input AssetUpdateInput
	if err := json.Unmarshal([]byte(assetJSON), &input); err != nil {
		return nil, fmt.Errorf("failed to parse asset JSON: %w", err)
	}

	if input.ID == "" {
		return nil, fmt.Errorf("no asset ID specified")
	}

	return &input, nil
}

func valueOrDefault(value *int, def int) int {
	if value == nil {
		return def
	}

	return *value
}

func hasWritePermission(ctx contractapi.TransactionContextInterface, asset *Asset) error {
	clientOrg, err := getClientOrg(ctx)
	if err != nil {
		return err
	}

	var owner OwnerIdentifier
	if err := json.Unmarshal([]byte(asset.Owner), &owner); err != nil {
		return fmt.Errorf("invalid owner identity: %w", err)
	}

	if clientOrg != owner.Org {
		return fmt.Errorf("only owner can update assets")
	}

	return nil
}

func getClientOrg(ctx contractapi.TransactionContextInterface) (string, error) {
	return ctx.GetClientIdentity().GetMSPID()
}

func clientIdentifier(ctx contractapi.TransactionContextInterface, user *string) (OwnerIdentifier, error) {
	clientOrg, err := getClientOrg(ctx)
	if err != nil {
		return OwnerIdentifier{}, err
	}

	userName := ""
	if user != nil {
		userName = *user
	} else {
		userName, err = clientCommonName(ctx)
		if err != nil {
			return OwnerIdentifier{}, err
		}
	}

	return OwnerIdentifier{
		Org:  clientOrg,
		User: userName,
	}, nil
}

func clientCommonName(ctx contractapi.TransactionContextInterface) (string, error) {
	cert, err := ctx.GetClientIdentity().GetX509Certificate()
	if err != nil {
		return "", fmt.Errorf("unable to get client certificate: %w", err)
	}

	if cert.Subject.CommonName == "" {
		return "", fmt.Errorf("unable to identify client identity common name")
	}

	return cert.Subject.CommonName, nil
}

func marshalOwner(owner OwnerIdentifier) string {
	ownerBytes, _ := json.Marshal(owner)
	return string(ownerBytes)
}

func ownerIdentifier(user string, org string) OwnerIdentifier {
	return OwnerIdentifier{Org: org, User: user}
}

func setEndorsingOrgs(ctx contractapi.TransactionContextInterface, ledgerKey string, orgs ...string) error {
	policy, err := statebased.NewStateEP(nil)
	if err != nil {
		return err
	}

	if err = policy.AddOrgs(statebased.RoleTypePeer, orgs...); err != nil {
		return err
	}

	policyBytes, err := policy.Policy()
	if err != nil {
		return err
	}

	return ctx.GetStub().SetStateValidationParameter(ledgerKey, policyBytes)
}
