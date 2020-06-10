/*
Copyright IBM Corp. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"log"

	"github.com/hyperledger/fabric-chaincode-go/shim"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

// Asset describes main asset details that are visible to all organizations
type Asset struct {
	Type  string `json:"objectType"` //Type is used to distinguish the various types of objects in state database
	ID    string `json:"assetID"`
	Color string `json:"color"`
	Size  int    `json:"size"`
	Owner string `json:"owner"`
}

// AssetPrivateDetails describes details that are private to owners
type AssetPrivateDetails struct {
	ID             string `json:"assetID"`
	AppraisedValue int    `json:"appraisedValue"`
}

const assetCollection = "assetCollection"

const transferAgreementObjectType = "transferAgreement"

type SmartContract struct {
	contractapi.Contract
}

// CreateAsset creates a new asset by placing the main asset details in the assetCollection
// that can be read by both organizations. The appraisal value is stored in the owners org specific collection.
func (s *SmartContract) CreateAsset(ctx contractapi.TransactionContextInterface) error {

	// Get new asset from transient map
	transientMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return fmt.Errorf("error getting transient: %v", err)
	}

	// Asset properties are private, therefore they get passed in transient field
	transientAssetJSON, ok := transientMap["asset_properties"]
	if !ok {
		return fmt.Errorf("asset not found in the transient map")
	}

	type assetTransientInput struct {
		Type           string `json:"objectType"` //Type is used to distinguish the various types of objects in state database
		ID             string `json:"assetID"`
		Color          string `json:"color"`
		Size           int    `json:"size"`
		AppraisedValue int    `json:"appraisedValue"`
	}

	var assetInput assetTransientInput
	err = json.Unmarshal(transientAssetJSON, &assetInput)
	if err != nil {
		return fmt.Errorf("failed to unmarshal JSON: %v", err)
	}

	if len(assetInput.Type) == 0 {
		return fmt.Errorf("objectType field must be a non-empty string")
	}
	if len(assetInput.ID) == 0 {
		return fmt.Errorf("assetID field must be a non-empty string")
	}
	if len(assetInput.Color) == 0 {
		return fmt.Errorf("color field must be a non-empty string")
	}
	if assetInput.Size <= 0 {
		return fmt.Errorf("size field must be a positive integer")
	}
	if assetInput.AppraisedValue <= 0 {
		return fmt.Errorf("appraisedValue field must be a positive integer")
	}

	// Check if asset already exists
	assetAsBytes, err := ctx.GetStub().GetPrivateData(assetCollection, assetInput.ID)
	if err != nil {
		return fmt.Errorf("failed to get asset: %v", err)
	} else if assetAsBytes != nil {
		fmt.Println("this asset already exists: " + assetInput.ID)
		return fmt.Errorf("this asset already exists: " + assetInput.ID)
	}

	// Get ID of submitting client identity
	clientID, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return fmt.Errorf("failed to get verified OrgID: %v", err)
	}

	// Make submitting client the owner
	asset := &Asset{
		Type:  assetInput.Type,
		ID:    assetInput.ID,
		Color: assetInput.Color,
		Size:  assetInput.Size,
		Owner: clientID,
	}
	assetJSONasBytes, err := json.Marshal(asset)
	if err != nil {
		return fmt.Errorf("failed to marshal into JSON: %v", err)
	}

	// Save asset to private data collection
	err = ctx.GetStub().PutPrivateData(assetCollection, assetInput.ID, assetJSONasBytes)
	if err != nil {
		return fmt.Errorf("failed to put asset into private data collection: %v", err)
	}

	// Save asset details to collection visible to owning organization
	assetPrivateDetails := &AssetPrivateDetails{
		ID:             assetInput.ID,
		AppraisedValue: assetInput.AppraisedValue,
	}

	assetPrivateDetailsAsBytes, err := json.Marshal(assetPrivateDetails) // marshal asset details to JSON
	if err != nil {
		return fmt.Errorf("failed to marshal into JSON: %v", err)
	}

	// Get collection name for this organization. Needs to be read by a member of the organization.
	orgCollection, err := getCollectionName(ctx, true)

	// Put asset appraised value into owners org specific private data collection
	err = ctx.GetStub().PutPrivateData(orgCollection, assetInput.ID, assetPrivateDetailsAsBytes)
	if err != nil {
		return fmt.Errorf("failed to put asset private details: %v", err)
	}
	return nil
}

// AgreeToTransfer is used by the potential buyer of the asset to agree to the
// asset value. The agreed to appraisal value is stored in the buying orgs
// org specifc collection, while the the buyer client ID is stored in the asset collection
// using a composite key
func (s *SmartContract) AgreeToTransfer(ctx contractapi.TransactionContextInterface) error {

	// Get ID of submitting client identity
	clientID, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return fmt.Errorf("failed to get verified OrgID: %v", err)
	}

	// Value is private, therefore it gets passed in transient field
	transientMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return fmt.Errorf("error getting transient: %v", err)
	}

	// Persist the JSON bytes as-is so that there is no risk of nondeterministic marshaling.
	valueJSONasBytes, ok := transientMap["asset_value"]
	if !ok {
		return fmt.Errorf("asset_value key not found in the transient map")
	}

	// Unmarshal the tranisent map to get the asset ID.
	var valueJSON AssetPrivateDetails
	err = json.Unmarshal(valueJSONasBytes, &valueJSON)
	if err != nil {
		return fmt.Errorf("failed to unmarshal JSON: %v", err)
	}

	// Do some error checking since we get the chance
	if len(valueJSON.ID) == 0 {
		return fmt.Errorf("assetID field must be a non-empty string")
	}
	if valueJSON.AppraisedValue <= 0 {
		return fmt.Errorf("appraisedValue field must be a positive integer")
	}

	// Get collection name for this organization. Needs to be read by a member of the organization.
	orgCollection, err := getCollectionName(ctx, true)

	// Put agreed value in the org specifc private data collection
	err = ctx.GetStub().PutPrivateData(orgCollection, valueJSON.ID, valueJSONasBytes)
	if err != nil {
		return fmt.Errorf("failed to put asset bid: %v", err)
	}

	// Create agreeement that indicates which identity has agreed to purchase
	// In a more realistic transfer scenario, a transfer agreement would be secured to ensure that it cannot
	// be overwritten by another channel member
	transferAgreeKey, err := ctx.GetStub().CreateCompositeKey(transferAgreementObjectType, []string{valueJSON.ID})
	if err != nil {
		return fmt.Errorf("failed to create composite key: %v", err)
	}

	err = ctx.GetStub().PutPrivateData(assetCollection, transferAgreeKey, []byte(clientID))
	if err != nil {
		return fmt.Errorf("failed to put asset bid: %v", err)
	}

	return nil
}

// TransferAsset transfers the asset to the new owner by setting a new owner ID
func (s *SmartContract) TransferAsset(ctx contractapi.TransactionContextInterface) error {

	transientMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return fmt.Errorf("error getting transient %v", err)
	}

	// Asset properties are private, therefore they get passed in transient field
	transientTransferJSON, ok := transientMap["asset_owner"]
	if !ok {
		return fmt.Errorf("asset owner not found in the transient map")
	}

	type assetTransferTransientInput struct {
		ID       string `json:"assetID"`
		BuyerMSP string `json:"buyerMSP"`
	}

	var assetTransferInput assetTransferTransientInput
	err = json.Unmarshal(transientTransferJSON, &assetTransferInput)
	if err != nil {
		return fmt.Errorf("failed to unmarshal JSON: %v", err)
	}

	if len(assetTransferInput.ID) == 0 {
		return fmt.Errorf("assetID field must be a non-empty string")
	}
	if len(assetTransferInput.BuyerMSP) == 0 {
		return fmt.Errorf("buyerMSP field must be a non-empty string")
	}

	// Read asset from the private data collection
	asset, err := s.ReadAsset(ctx, assetTransferInput.ID)
	if err != nil {
		return fmt.Errorf("failed to get asset: %v", err)
	}

	// Verify transfer details and transfer owner
	err = s.verifyAgreement(ctx, assetTransferInput.ID, asset.Owner, assetTransferInput.BuyerMSP)
	if err != nil {
		return fmt.Errorf("failed transfer verification: %v", err)
	}

	buyerID, err := s.ReadTransferAgreement(ctx, assetTransferInput.ID)

	// Transfer asset in private data collection to new owner
	asset.Owner = buyerID

	assetJSONasBytes, _ := json.Marshal(asset)
	err = ctx.GetStub().PutPrivateData(assetCollection, assetTransferInput.ID, assetJSONasBytes) //rewrite the asset
	if err != nil {
		return err
	}

	// Get collection name for this organization
	ownersCollection, err := getCollectionName(ctx, false)

	// Delete the marble appraised value from this organiztion's private data collection
	err = ctx.GetStub().DelPrivateData(ownersCollection, assetTransferInput.ID)
	if err != nil {
		return err
	}

	// Delete the transfer agreement from the asset collection
	transferAgreeKey, err := ctx.GetStub().CreateCompositeKey(transferAgreementObjectType, []string{assetTransferInput.ID})
	if err != nil {
		return fmt.Errorf("failed to create composite key: %v", err)
	}

	err = ctx.GetStub().DelPrivateData(assetCollection, transferAgreeKey)
	if err != nil {
		return err
	}

	return nil

}

// verifyAgreement is an internal helper function used by TransferAsset to verify
// that the transfer is being initiated by the owner and that the buyer has agreed
// to the same appraisal value as the owner
func (s *SmartContract) verifyAgreement(ctx contractapi.TransactionContextInterface, assetID string, owner string, buyerMSP string) error {

	// Check 1: verify that the transfer is being initiatied by the owner

	// Get ID of submitting client identity
	clientID, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return fmt.Errorf("failed to get verified OrgID: %v", err)
	}

	if clientID != owner {
		return fmt.Errorf("error: submitting client identity does not own asset")
	}

	// Check 2: verify that the buyer has agreed to the appraised value

	// Get collection names

	collectionOwner, err := getCollectionName(ctx, false) // get buyers collection

	collectionBuyer := buyerMSP + "PrivateCollection" // get buyers collection

	// Get hash of owners agreed to value
	ownerAppraisedValueHash, err := ctx.GetStub().GetPrivateDataHash(collectionOwner, assetID)
	if err != nil {
		return fmt.Errorf("failed to get hash of appraised value from owners collection %v: %v", collectionOwner, err)
	}
	if ownerAppraisedValueHash == nil {
		return fmt.Errorf("hash of appraised value for %v does not exist in collection %v", assetID, collectionOwner)
	}

	// Get hash of buyers agreed to value
	buyerAppraisedValueHash, err := ctx.GetStub().GetPrivateDataHash(collectionBuyer, assetID)
	if err != nil {
		return fmt.Errorf("failed to get hash of appraised value from buyer collection %v: %v", collectionBuyer, err)
	}
	if buyerAppraisedValueHash == nil {
		return fmt.Errorf("hash of appraised value for %v does not exist in collection %v", assetID, collectionBuyer)
	}

	// Verify that the two hashes match
	if !bytes.Equal(ownerAppraisedValueHash, buyerAppraisedValueHash) {
		return fmt.Errorf("hash for appraised value for owner %x does not value for seller %x", ownerAppraisedValueHash, buyerAppraisedValueHash)
	}

	return nil
}

// DeleteAsset can be used by the owner of the asset to delete the asset
func (s *SmartContract) DeleteAsset(ctx contractapi.TransactionContextInterface) error {

	transientMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return fmt.Errorf("Error getting transient: %v", err)
	}

	// Asset properties are private, therefore they get passed in transient field
	transientDeleteJSON, ok := transientMap["asset_delete"]
	if !ok {
		return fmt.Errorf("asset to delete not found in the transient map")
	}

	type assetDelete struct {
		ID string `json:"assetID"`
	}

	var assetDeleteInput assetDelete
	err = json.Unmarshal(transientDeleteJSON, &assetDeleteInput)
	if err != nil {
		return fmt.Errorf("failed to unmarshal JSON: %v", err)
	}

	if len(assetDeleteInput.ID) == 0 {
		return fmt.Errorf("assetID field must be a non-empty string")
	}

	valAsbytes, err := ctx.GetStub().GetPrivateData(assetCollection, assetDeleteInput.ID) //get the asset from chaincode state
	if err != nil {
		return fmt.Errorf("failed to read asset: %v", err)
	}
	if valAsbytes == nil {
		return fmt.Errorf("asset private details does not exist: %v", assetDeleteInput.ID)
	}

	var assetToDelete Asset
	err = json.Unmarshal([]byte(valAsbytes), &assetToDelete)
	if err != nil {
		return fmt.Errorf("failed to unmarshal JSON: %v", err)
	}

	// delete the asset from state
	err = ctx.GetStub().DelPrivateData(assetCollection, assetDeleteInput.ID)
	if err != nil {
		return fmt.Errorf("failed to delete state: %v", err)
	}

	// Finally, delete private details of asset

	ownerCollection, err := getCollectionName(ctx, true) // Get owners collection. Needs to be read by a member of the organization.

	err = ctx.GetStub().DelPrivateData(ownerCollection, assetDeleteInput.ID) // Delete the asset
	if err != nil {
		return err
	}

	return nil

}

// DeleteTranferAgreement can be used by the buyer to withdraw a proposal from
// the asset collection and from his own collection.
func (s *SmartContract) DeleteTranferAgreement(ctx contractapi.TransactionContextInterface) error {

	transientMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return fmt.Errorf("error getting transient: %v", err)
	}

	// Asset properties are private, therefore they get passed in transient field
	transientDeleteJSON, ok := transientMap["agree_delete"]
	if !ok {
		return fmt.Errorf("asset to delete not found in the transient map")
	}

	type assetDelete struct {
		ID string `json:"assetID"`
	}

	var assetDeleteInput assetDelete
	err = json.Unmarshal(transientDeleteJSON, &assetDeleteInput)
	if err != nil {
		return fmt.Errorf("failed to unmarshal JSON: %v", err)
	}

	if len(assetDeleteInput.ID) == 0 {
		return fmt.Errorf("ID field must be a non-empty string")
	}

	// Delete private details of agreement

	orgCollection, err := getCollectionName(ctx, true) // Get proposers collection. Needs to be read by a member of the organization.

	err = ctx.GetStub().DelPrivateData(orgCollection, assetDeleteInput.ID) // Delete the asset
	if err != nil {
		return err
	}

	// Delete transfer agreement record

	tranferAgreeKey, err := ctx.GetStub().CreateCompositeKey(transferAgreementObjectType, []string{assetDeleteInput.ID}) // Create composite key
	if err != nil {
		return fmt.Errorf("failed to create composite key: %v", err)
	}

	err = ctx.GetStub().DelState(tranferAgreeKey) // remove agreement from state
	if err != nil {
		return err
	}

	return nil

}

// getCollectionName is an internal helper function to get collection of submitting client identity.
// The collection name can optionally be verified against the peer org ID, to ensure that a
// client from another org doesn't attempt to read or write private data from this peer.
func getCollectionName(ctx contractapi.TransactionContextInterface, verifyOrg bool) (string, error) {

	// Get the MSP ID of submitting client identity
	clientMSPID, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return "", fmt.Errorf("failed to get verified OrgID: %v", err)
	}

	// Verify that the client is submitting request to peer in their organization
	if verifyOrg {
		err = verifyClientOrgMatchesPeerOrg(clientMSPID)
		if err != nil {
			return "", err
		}
	}

	// Create the collection name
	orgCollection := clientMSPID + "PrivateCollection"

	return orgCollection, nil
}

// verifyClientOrgMatchesPeerOrg is an internal function used verify client org id and matches peer org id.
func verifyClientOrgMatchesPeerOrg(clientMSPID string) error {
	peerMSPID, err := shim.GetMSPID()
	if err != nil {
		return fmt.Errorf("failed getting peer's orgID: %v", err)
	}

	if clientMSPID != peerMSPID {
		return fmt.Errorf("client from org %v is not authorized to read or write private data from an org %v peer", clientMSPID, peerMSPID)
	}

	return nil
}

func main() {

	chaincode, err := contractapi.NewChaincode(new(SmartContract))

	if err != nil {
		log.Panicf("error creating private mables chaincode: %v", err)
		return
	}

	if err := chaincode.Start(); err != nil {
		log.Panicf("error starting private mables chaincode: %v", err)
	}
}
