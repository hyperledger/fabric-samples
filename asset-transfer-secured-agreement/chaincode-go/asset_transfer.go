/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"time"

	"github.com/hyperledger/fabric-chaincode-go/pkg/statebased"
	"github.com/hyperledger/fabric-chaincode-go/shim"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

const (
	typeAssetForSale     = "S"
	typeAssetBid         = "B"
	typeAssetSaleReceipt = "SR"
	typeAssetBuyReceipt  = "BR"
)

type SmartContract struct {
	contractapi.Contract
}

// Asset struct and properties must be exported (start with capitals) to work with contract api metadata
type Asset struct {
	ObjectType        string `json:"object_type"` // ObjectType is used to distinguish different object types in the same chaincode namespace
	ID                string `json:"asset_id"`
	OwnerOrg          string `json:"owner_org"`
	PublicDescription string `json:"public_description"`
}

type receipt struct {
	price     int
	timestamp time.Time
}

// CreateAsset creates a asset and sets it as owned by the client's org
func (s *SmartContract) CreateAsset(ctx contractapi.TransactionContextInterface, assetID, publicDescription string) error {

	transMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return fmt.Errorf("Error getting transient: " + err.Error())
	}

	// Asset properties are private, therefore they get passed in transient field
	immutablePropertiesJSON, ok := transMap["asset_properties"]
	if !ok {
		return fmt.Errorf("asset_properties key not found in the transient map")
	}

	// Get client org id and verify it matches peer org id.
	// In this scenario, client is only authorized to read/write private data from its own peer.
	clientOrgID, err := getClientOrgID(ctx, true)
	if err != nil {
		return fmt.Errorf("failed to get verified OrgID: %s", err.Error())
	}

	// Create and persist asset
	asset := Asset{
		ObjectType:        "asset",
		ID:                assetID,
		OwnerOrg:          clientOrgID,
		PublicDescription: publicDescription,
	}

	assetJSON, err := json.Marshal(asset)
	if err != nil {
		return fmt.Errorf("failed to create asset JSON: %s", err.Error())
	}

	err = ctx.GetStub().PutState(asset.ID, assetJSON)
	if err != nil {
		return fmt.Errorf("failed to put Asset in public data: %s", err.Error())
	}

	// Set the endorsement policy such that an owner org peer is required to endorse future updates
	err = setAssetStateBasedEndorsement(ctx, asset.ID, clientOrgID)
	if err != nil {
		return fmt.Errorf("failed setting state based endorsement for owner: %s", err.Error())
	}

	// Persist private immutable asset properties to owner's private data collection
	collection := buildCollectionName(clientOrgID)
	err = ctx.GetStub().PutPrivateData(collection, asset.ID, []byte(immutablePropertiesJSON))
	if err != nil {
		return fmt.Errorf("failed to put Asset private details: %s", err.Error())
	}

	return nil
}

// ChangePublicDescription updates the asset public description. Only the current owner can update the public description
func (s *SmartContract) ChangePublicDescription(ctx contractapi.TransactionContextInterface, assetID string, newDescription string) error {

	// Get client org id
	// No need to check client org id matches peer org id, rely on the asset ownership check instead.
	clientOrgID, err := getClientOrgID(ctx, false)
	if err != nil {
		return fmt.Errorf("failed to get verified OrgID: %s", err.Error())
	}

	asset, err := s.ReadAsset(ctx, assetID)
	if err != nil {
		return fmt.Errorf("failed to get asset: %s", err.Error())
	}

	// auth check to ensure that client's org actually owns the asset
	if clientOrgID != asset.OwnerOrg {
		return fmt.Errorf("a client from %s cannot update the description of a asset owned by %s", clientOrgID, asset.OwnerOrg)
	}

	asset.PublicDescription = newDescription

	updatedAssetJSON, err := json.Marshal(asset)
	if err != nil {
		return fmt.Errorf("failed to marshal asset: %s", err.Error())
	}

	return ctx.GetStub().PutState(assetID, updatedAssetJSON)
}

// AgreeToSell adds seller's asking price to seller's implicit private data collection
func (s *SmartContract) AgreeToSell(ctx contractapi.TransactionContextInterface, assetID string) error {
	// Query asset and verify that this clientOrgId actually owns the asset.
	asset, err := s.ReadAsset(ctx, assetID)
	if err != nil {
		return err
	}

	clientOrgID, err := getClientOrgID(ctx, true)
	if err != nil {
		return fmt.Errorf("failed to get verified OrgID: %s", err.Error())
	}

	if clientOrgID != asset.OwnerOrg {
		return fmt.Errorf("a client from %s cannot sell a asset owned by %s", clientOrgID, asset.OwnerOrg)
	}

	return agreeToPrice(ctx, assetID, typeAssetForSale)
}

// AgreeToBuy adds buyer's bid price to buyer's implicit private data collection
func (s *SmartContract) AgreeToBuy(ctx contractapi.TransactionContextInterface, assetID string) error {
	return agreeToPrice(ctx, assetID, typeAssetBid)
}

// agreeToPrice adds a bid or ask price to caller's implicit private data collection
func agreeToPrice(ctx contractapi.TransactionContextInterface, assetID string, priceType string) error {

	// Get client org id and verify it matches peer org id.
	// In this scenario, client is only authorized to read/write private data from its own peer.
	clientOrgID, err := getClientOrgID(ctx, true)
	if err != nil {
		return fmt.Errorf("failed to get verified OrgID: %s", err.Error())
	}

	// price is private, therefore it gets passed in transient field
	transMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return fmt.Errorf("Error getting transient: " + err.Error())
	}

	// Price hash will get verfied later, therefore always pass and persist the JSON bytes as-is,
	// so that there is no risk of nondeterministic marshaling.
	priceJSON, ok := transMap["asset_price"]
	if !ok {
		return fmt.Errorf("asset_price key not found in the transient map")
	}

	collection := buildCollectionName(clientOrgID)

	// Persist the agreed to price in a collection sub-namespace based on priceType key prefix,
	// to avoid collisions between private asset properties, sell price, and buy price
	assetPriceKey, err := ctx.GetStub().CreateCompositeKey(priceType, []string{assetID})
	if err != nil {
		return fmt.Errorf("failed to create composite key: %s", err.Error())
	}

	err = ctx.GetStub().PutPrivateData(collection, assetPriceKey, priceJSON)
	if err != nil {
		return fmt.Errorf("failed to put asset bid: %s", err.Error())
	}

	return nil
}

// VerifyAssetProperties implement function to verify asset properties using the hash
// Allows a buyer to validate the properties of an asset against the owner's implicit private data collection
func (s *SmartContract) VerifyAssetProperties(ctx contractapi.TransactionContextInterface, assetID string) (bool, error) {
	transMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return false, fmt.Errorf("Error getting transient: " + err.Error())
	}

	// Asset properties are private, therefore they get passed in transient field
	immutablePropertiesJSON, ok := transMap["asset_properties"]
	if !ok {
		return false, fmt.Errorf("asset_properties key not found in the transient map")
	}

	asset, err := s.ReadAsset(ctx, assetID)
	if err != nil {
		return false, fmt.Errorf("failed to get asset: %s", err.Error())
	}

	collectionOwner := buildCollectionName(asset.OwnerOrg)
	immutablePropertiesOnChainHash, err := ctx.GetStub().GetPrivateDataHash(collectionOwner, assetID)
	if err != nil {
		return false, fmt.Errorf("failed to read asset private properties hash from seller's collection: %s", err.Error())
	}
	if immutablePropertiesOnChainHash == nil {
		return false, fmt.Errorf("asset private properties hash does not exist: %s", assetID)
	}

	// get sha256 hash of passed immutable properties
	hash := sha256.New()
	hash.Write(immutablePropertiesJSON)
	calculatedPropertiesHash := hash.Sum(nil)

	// verify that the hash of the passed immutable properties matches the on-chain hash
	if !bytes.Equal(immutablePropertiesOnChainHash, calculatedPropertiesHash) {
		return false, fmt.Errorf("hash %x for passed immutable properties %s does not match on-chain hash %x", calculatedPropertiesHash, immutablePropertiesJSON, immutablePropertiesOnChainHash)
	}

	return true, nil
}

// TransferAsset checks transfer conditions and then transfers asset state to buyer.
// TransferAsset can only be called by current owner
func (s *SmartContract) TransferAsset(ctx contractapi.TransactionContextInterface, assetID string, buyerOrgID string) error {

	// Get client org id and verify it matches peer org id.
	// For a transfer, selling client must get endorsement from their own peer and from buyer peer, therefore don't verify client org id matches peer org id
	clientOrgID, err := getClientOrgID(ctx, false)
	if err != nil {
		return fmt.Errorf("failed to get verified OrgID: %s", err.Error())
	}

	transMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return fmt.Errorf("Error getting transient: " + err.Error())
	}

	immutablePropertiesJSON, ok := transMap["asset_properties"]
	if !ok {
		return fmt.Errorf("asset_properties key not found in the transient map")
	}

	priceJSON, ok := transMap["asset_price"]
	if !ok {
		return fmt.Errorf("asset_price key not found in the transient map")
	}

	var agreement Agreement
	err = json.Unmarshal([]byte(priceJSON), &agreement)
	if err != nil {
		return fmt.Errorf("failed to unmarshal price JSON: %s", err.Error())
	}

	asset, err := s.ReadAsset(ctx, assetID)
	if err != nil {
		return fmt.Errorf("failed to get asset: %s", err.Error())
	}

	err = verifyTransferConditions(ctx, asset, immutablePropertiesJSON, clientOrgID, buyerOrgID, priceJSON)
	if err != nil {
		return fmt.Errorf("failed transfer verification: %s", err.Error())
	}

	err = transferAssetState(ctx, asset, immutablePropertiesJSON, clientOrgID, buyerOrgID, agreement.Price)
	if err != nil {
		return fmt.Errorf("failed asset transfer: %s", err.Error())
	}

	return nil

}

// verifyTransferConditions checks that client org currently owns asset and that both parties have agreed on price
func verifyTransferConditions(ctx contractapi.TransactionContextInterface, asset *Asset, immutablePropertiesJSON []byte, clientOrgID string, buyerOrgID string, priceJSON []byte) error {

	// CHECK1: auth check to ensure that client's org actually owns the asset

	if clientOrgID != asset.OwnerOrg {
		return fmt.Errorf("a client from %s cannot transfer a asset owned by %s", clientOrgID, asset.OwnerOrg)
	}

	// CHECK2: verify that the hash of the passed immutable properties matches the on-chain hash

	// get on chain hash
	collectionSeller := buildCollectionName(clientOrgID)
	immutablePropertiesOnChainHash, err := ctx.GetStub().GetPrivateDataHash(collectionSeller, asset.ID)
	if err != nil {
		return fmt.Errorf("failed to read asset private properties hash from seller's collection: %s", err.Error())
	}
	if immutablePropertiesOnChainHash == nil {
		return fmt.Errorf("asset private properties hash does not exist: %s", asset.ID)
	}

	// get sha256 hash of passed immutable properties
	hash := sha256.New()
	hash.Write(immutablePropertiesJSON)
	calculatedPropertiesHash := hash.Sum(nil)

	// verify that the hash of the passed immutable properties matches the on-chain hash
	if !bytes.Equal(immutablePropertiesOnChainHash, calculatedPropertiesHash) {
		return fmt.Errorf("hash %x for passed immutable properties %s does not match on-chain hash %x", calculatedPropertiesHash, immutablePropertiesJSON, immutablePropertiesOnChainHash)
	}

	// CHECK3: verify that seller and buyer agreed on the same price

	// get seller (current owner) asking price
	assetForSaleKey, err := ctx.GetStub().CreateCompositeKey(typeAssetForSale, []string{asset.ID})
	if err != nil {
		return fmt.Errorf("failed to create composite key: %s", err.Error())
	}
	sellerPriceHash, err := ctx.GetStub().GetPrivateDataHash(collectionSeller, assetForSaleKey)
	if err != nil {
		return fmt.Errorf("failed to get seller price hash: %s", err.Error())
	}
	if sellerPriceHash == nil {
		return fmt.Errorf("seller price for %s does not exist", asset.ID)
	}

	// get buyer bid price
	collectionBuyer := buildCollectionName(buyerOrgID)
	assetBidKey, err := ctx.GetStub().CreateCompositeKey(typeAssetBid, []string{asset.ID})
	if err != nil {
		return fmt.Errorf("failed to create composite key: %s", err.Error())
	}
	buyerPriceHash, err := ctx.GetStub().GetPrivateDataHash(collectionBuyer, assetBidKey)
	if err != nil {
		return fmt.Errorf("failed to get buyer price hash: %s", err.Error())
	}
	if buyerPriceHash == nil {
		return fmt.Errorf("buyer price for %s does not exist", asset.ID)
	}

	// get sha256 hash of passed price
	hash = sha256.New()
	hash.Write(priceJSON)
	calculatedPriceHash := hash.Sum(nil)

	// verify that the hash of the passed price matches the on-chain seller price hash
	if !bytes.Equal(calculatedPriceHash, sellerPriceHash) {
		return fmt.Errorf("hash %x for passed price JSON %s does not match on-chain hash %x, seller hasn't agreed to the passed trade id and price", calculatedPriceHash, priceJSON, sellerPriceHash)
	}

	// verify that the hash of the passed price matches the on-chain buyer price hash
	if !bytes.Equal(calculatedPriceHash, buyerPriceHash) {
		return fmt.Errorf("hash %x for passed price JSON %s does not match on-chain hash %x, buyer hasn't agreed to the passed trade id and price", calculatedPriceHash, priceJSON, buyerPriceHash)
	}

	// since all checks passed, return without an error
	return nil
}

// transferAssetState makes the public and private state updates for the transferred asset
func transferAssetState(ctx contractapi.TransactionContextInterface, asset *Asset, immutablePropertiesJSON []byte, clientOrgID string, buyerOrgID string, price int) error {

	// save the asset with the new owner
	asset.OwnerOrg = buyerOrgID

	updatedAssetJSON, _ := json.Marshal(asset)

	err := ctx.GetStub().PutState(asset.ID, updatedAssetJSON)
	if err != nil {
		return fmt.Errorf("failed to write asset for buyer: %s", err.Error())
	}

	// Change the endorsement policy to the new owner
	err = setAssetStateBasedEndorsement(ctx, asset.ID, buyerOrgID)
	if err != nil {
		return fmt.Errorf("failed setting state based endorsement for new owner: %s", err.Error())
	}

	// Transfer the private properties (delete from seller collection, create in buyer collection)
	collectionSeller := buildCollectionName(clientOrgID)
	err = ctx.GetStub().DelPrivateData(collectionSeller, asset.ID)
	if err != nil {
		return fmt.Errorf("failed to delete Asset private details from seller: %s", err.Error())
	}

	collectionBuyer := buildCollectionName(buyerOrgID)
	err = ctx.GetStub().PutPrivateData(collectionBuyer, asset.ID, immutablePropertiesJSON)
	if err != nil {
		return fmt.Errorf("failed to put Asset private properties for buyer: %s", err.Error())
	}

	// Delete the price records for seller
	assetPriceKey, err := ctx.GetStub().CreateCompositeKey(typeAssetForSale, []string{asset.ID})
	if err != nil {
		return fmt.Errorf("failed to create composite key for seller: %s", err.Error())
	}

	err = ctx.GetStub().DelPrivateData(collectionSeller, assetPriceKey)
	if err != nil {
		return fmt.Errorf("failed to delete asset price from implicit private data collection for seller: %s", err.Error())
	}

	// Delete the price records for buyer
	assetPriceKey, err = ctx.GetStub().CreateCompositeKey(typeAssetBid, []string{asset.ID})
	if err != nil {
		return fmt.Errorf("failed to create composite key for buyer: %s", err.Error())
	}

	err = ctx.GetStub().DelPrivateData(collectionBuyer, assetPriceKey)
	if err != nil {
		return fmt.Errorf("failed to delete asset price from implicit private data collection for buyer: %s", err.Error())
	}

	// Keep record for a 'receipt' in both buyer and seller private data collection to record the sales price and date
	// Persist the agreed to price in a collection sub-namespace based on receipt key prefix
	receiptBuyKey, err := ctx.GetStub().CreateCompositeKey(typeAssetBuyReceipt, []string{asset.ID, ctx.GetStub().GetTxID()})
	if err != nil {
		return fmt.Errorf("failed to create composite key for receipt: %s", err.Error())
	}

	timestmp, err := ctx.GetStub().GetTxTimestamp()
	if err != nil {
		return fmt.Errorf("failed to create timestamp for receipt: %s", err.Error())
	}

	assetReceipt := receipt{
		price:     price,
		timestamp: time.Unix(timestmp.Seconds, int64(timestmp.Nanos)),
	}

	receiptJSON, err := json.Marshal(assetReceipt)
	if err != nil {
		return fmt.Errorf("failed to marshal receipt: %s", err.Error())
	}

	err = ctx.GetStub().PutPrivateData(collectionBuyer, receiptBuyKey, receiptJSON)
	if err != nil {
		return fmt.Errorf("failed to put private asset receipt for buyer: %s", err.Error())
	}

	receiptSaleKey, err := ctx.GetStub().CreateCompositeKey(typeAssetSaleReceipt, []string{ctx.GetStub().GetTxID(), asset.ID})
	if err != nil {
		return fmt.Errorf("failed to create composite key for receipt: %s", err.Error())
	}

	err = ctx.GetStub().PutPrivateData(collectionSeller, receiptSaleKey, receiptJSON)
	if err != nil {
		return fmt.Errorf("failed to put private asset receipt for seller: %s", err.Error())
	}

	return nil
}

// getClientOrgID gets the client org ID.
// The client org ID can optionally be verified against the peer org ID, to ensure that a client from another org doesn't attempt to read or write private data from this peer.
// The only exception in this scenario is for TransferAsset, since the current owner needs to get an endorsement from the buyer's peer.
func getClientOrgID(ctx contractapi.TransactionContextInterface, verifyOrg bool) (string, error) {

	clientOrgID, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return "", fmt.Errorf("failed getting client's orgID: %s", err.Error())
	}

	if verifyOrg {
		err = verifyClientOrgMatchesPeerOrg(clientOrgID)
		if err != nil {
			return "", err
		}
	}

	return clientOrgID, nil
}

// verify client org id and matches peer org id.
func verifyClientOrgMatchesPeerOrg(clientOrgID string) error {
	peerOrgID, err := shim.GetMSPID()
	if err != nil {
		return fmt.Errorf("failed getting peer's orgID: %s", err.Error())
	}

	if clientOrgID != peerOrgID {
		return fmt.Errorf("client from org %s is not authorized to read or write private data from an org %s peer", clientOrgID, peerOrgID)
	}

	return nil
}

// setAssetStateBasedEndorsement adds an endorsement policy to a asset so that only a peer from an owning org can update or transfer the asset.
func setAssetStateBasedEndorsement(ctx contractapi.TransactionContextInterface, assetID string, orgToEndorse string) error {

	endorsementPolicy, err := statebased.NewStateEP(nil)

	err = endorsementPolicy.AddOrgs(statebased.RoleTypePeer, orgToEndorse)
	if err != nil {
		return fmt.Errorf("failed to add org to endorsement policy: %s", err.Error())
	}
	epBytes, err := endorsementPolicy.Policy()
	if err != nil {
		return fmt.Errorf("failed to create endorsement policy bytes from org: %s", err.Error())
	}
	err = ctx.GetStub().SetStateValidationParameter(assetID, epBytes)
	if err != nil {
		return fmt.Errorf("failed to set validation parameter on asset: %s", err.Error())
	}

	return nil
}

func buildCollectionName(clientOrgID string) string {
	return fmt.Sprintf("_implicit_org_%s", clientOrgID)
}

func getClientImplicitCollectionName(ctx contractapi.TransactionContextInterface) (string, error) {
	clientOrgID, err := getClientOrgID(ctx, true)
	if err != nil {
		return "", fmt.Errorf("failed to get verified OrgID: %s", err.Error())
	}

	err = verifyClientOrgMatchesPeerOrg(clientOrgID)
	if err != nil {
		return "", err
	}

	return buildCollectionName(clientOrgID), nil
}

func main() {

	chaincode, err := contractapi.NewChaincode(new(SmartContract))

	if err != nil {
		fmt.Printf("Error create transfer asset chaincode: %s", err.Error())
		return
	}

	if err := chaincode.Start(); err != nil {
		fmt.Printf("Error starting asset chaincode: %s", err.Error())
	}
}
