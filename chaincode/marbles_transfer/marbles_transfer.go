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

	"github.com/hyperledger/fabric-chaincode-go/pkg/statebased"
	"github.com/hyperledger/fabric-chaincode-go/shim"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

const (
	typeMarbleForSale = "S"
	typeMarbleBid     = "B"
)

type SmartContract struct {
	contractapi.Contract
}

// Marble struct and properties must be exported (start with capitals) to work with contract api metadata
type Marble struct {
	ObjectType        string `json:"object_type"` // ObjectType is used to distinguish different object types in the same chaincode namespace
	ID                string `json:"marble_id"`
	OwnerOrg          string `json:"owner_org"`
	PublicDescription string `json:"public_description"`
}

// IssueAsset creates a marble and sets it as owned by the client's org
func (s *SmartContract) IssueAsset(ctx contractapi.TransactionContextInterface, marbleID string) error {

	transMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return fmt.Errorf("Error getting transient: " + err.Error())
	}

	// Marble properties are private, therefore they get passed in transient field
	immutablePropertiesJSON, ok := transMap["marble_properties"]
	if !ok {
		return fmt.Errorf("marble_properties key not found in the transient map")
	}

	// Get client org id and verify it matches peer org id.
	// In this scenario, client is only authorized to read/write private data from its own peer.
	clientOrgID, err := getClientOrgID(ctx, true)
	if err != nil {
		return fmt.Errorf("failed to get verified OrgID: %s", err.Error())
	}

	// Create and persit marble

	marble := Marble{
		ObjectType:        "marble",
		ID:                marbleID,
		OwnerOrg:          clientOrgID,
		PublicDescription: "A new marble for " + clientOrgID,
	}

	marbleJSON, err := json.Marshal(marble)
	if err != nil {
		return fmt.Errorf("failed to create marble JSON: %s", err.Error())
	}

	err = ctx.GetStub().PutState(marble.ID, marbleJSON)
	if err != nil {
		return fmt.Errorf("failed to put Marble in public data: %s", err.Error())
	}

	// Set the endorsement policy such that an owner org peer is required to endorse future updates
	err = setMarbleStateBasedEndorsement(ctx, marble.ID, clientOrgID)
	if err != nil {
		return fmt.Errorf("failed setting state based endorsement for owner: %s", err.Error())
	}

	// Persist private immutable marble properties to owner's private data collection
	collection := "_implicit_org_" + clientOrgID
	err = ctx.GetStub().PutPrivateData(collection, marble.ID, []byte(immutablePropertiesJSON))
	if err != nil {
		return fmt.Errorf("failed to put Marble private details: %s", err.Error())
	}

	return nil
}

// ChangePublicDescription updates the marble public description. Only the current owner can update the public description
func (s *SmartContract) ChangePublicDescription(ctx contractapi.TransactionContextInterface, marbleID string, newDescription string) error {

	// Get client org id
	// No need to check client org id matches peer org id, rely on the marble ownership check instead.
	clientOrgID, err := getClientOrgID(ctx, false)
	if err != nil {
		return fmt.Errorf("failed to get verified OrgID: %s", err.Error())
	}

	marble, err := s.GetAsset(ctx, marbleID)
	if err != nil {
		return fmt.Errorf("failed to get marble: %s", err.Error())
	}

	// auth check to ensure that client's org actually owns the marble
	if clientOrgID != marble.OwnerOrg {
		return fmt.Errorf("a client from %s cannot update the description of a marble owned by %s", clientOrgID, marble.OwnerOrg)
	}

	marble.PublicDescription = newDescription

	updatedMarbleJSON, err := json.Marshal(marble)
	if err != nil {
		return fmt.Errorf("failed to marshal marble: %s", err.Error())
	}

	return ctx.GetStub().PutState(marbleID, updatedMarbleJSON)
}

// AgreeToSell adds seller's asking price to seller's implicit private data collection
func (s *SmartContract) AgreeToSell(ctx contractapi.TransactionContextInterface, marbleID string) error {
	// Query marble and verify that this clientOrgId actually owns the marble.
	marble, err := s.GetAsset(ctx, marbleID)
	if err != nil {
		return err
	}

	clientOrgID, err := getClientOrgID(ctx, true)
	if err != nil {
		return fmt.Errorf("failed to get verified OrgID: %s", err.Error())
	}

	if clientOrgID != marble.OwnerOrg {
		return fmt.Errorf("a client from %s cannot sell a marble owned by %s", clientOrgID, marble.OwnerOrg)
	}

	return agreeToPrice(ctx, marbleID, typeMarbleForSale)
}

// AgreeToBuy adds buyer's bid price to buyer's implicit private data collection
func (s *SmartContract) AgreeToBuy(ctx contractapi.TransactionContextInterface, marbleID string) error {
	return agreeToPrice(ctx, marbleID, typeMarbleBid)
}

// agreeToPrice adds a bid or ask price to caller's implicit private data collection
func agreeToPrice(ctx contractapi.TransactionContextInterface, marbleID string, priceType string) error {

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
	priceJSON, ok := transMap["marble_price"]
	if !ok {
		return fmt.Errorf("marble_price key not found in the transient map")
	}

	collection := "_implicit_org_" + clientOrgID

	// Persist the agreed to price in a collection sub-namespace based on priceType key prefix,
	// to avoid collisions between private marble properties, sell price, and buy price
	marblePriceKey, err := ctx.GetStub().CreateCompositeKey(priceType, []string{marbleID})
	if err != nil {
		return fmt.Errorf("failed to create composite key: %s", err.Error())
	}

	err = ctx.GetStub().PutPrivateData(collection, marblePriceKey, priceJSON)
	if err != nil {
		return fmt.Errorf("failed to put marble bid: %s", err.Error())
	}

	return nil
}

// TODO implement function to verify marble properties
// For example, Org1 may tell Org2 about the properties and salt.
// Org2 would want to verify the properties before agreeing to buy.
// Org2 would call a verify function on his peer.
// The properties and salt would passed in, get hashed in the chaincode, and compared with the on-chain hash of the marble properties (queried via GetPrivateDataHash).

// TransferAsset checks transfer conditions and then transfers marble state to buyer.
// TransferAsset can only be called by current owner
func (s *SmartContract) TransferAsset(ctx contractapi.TransactionContextInterface, marbleID string, buyerOrgID string) error {

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

	immutablePropertiesJSON, ok := transMap["marble_properties"]
	if !ok {
		return fmt.Errorf("marble_properties key not found in the transient map")
	}

	priceJSON, ok := transMap["marble_price"]
	if !ok {
		return fmt.Errorf("marble_price key not found in the transient map")
	}

	marble, err := s.GetAsset(ctx, marbleID)
	if err != nil {
		return fmt.Errorf("failed to get marble: %s", err.Error())
	}

	err = verifyTransferConditions(ctx, marble, immutablePropertiesJSON, clientOrgID, buyerOrgID, priceJSON)
	if err != nil {
		return fmt.Errorf("failed transfer verification: %s", err.Error())
	}

	err = transferMarbleState(ctx, marble, immutablePropertiesJSON, clientOrgID, buyerOrgID)
	if err != nil {
		return fmt.Errorf("failed marble transfer: %s", err.Error())
	}

	return nil

}

// verifyTransferConditions checks that client org currently owns marble and that both parties have agreed on price
func verifyTransferConditions(ctx contractapi.TransactionContextInterface, marble *Marble, immutablePropertiesJSON []byte, clientOrgID string, buyerOrgID string, priceJSON []byte) error {

	// CHECK1: auth check to ensure that client's org actually owns the marble

	if clientOrgID != marble.OwnerOrg {
		return fmt.Errorf("a client from %s cannot transfer a marble owned by %s", clientOrgID, marble.OwnerOrg)
	}

	// CHECK2: verify that the hash of the passed immutable properties matches the on-chain hash

	// get on chain hash
	collectionSeller := "_implicit_org_" + clientOrgID
	immutablePropertiesOnChainHash, err := ctx.GetStub().GetPrivateDataHash(collectionSeller, marble.ID)
	if err != nil {
		return fmt.Errorf("failed to read marble private properties hash from seller's collection: %s", err.Error())
	}
	if immutablePropertiesOnChainHash == nil {
		return fmt.Errorf("marble private properties hash does not exist: %s", marble.ID)
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
	marbleForSaleKey, err := ctx.GetStub().CreateCompositeKey(typeMarbleForSale, []string{marble.ID})
	if err != nil {
		return fmt.Errorf("failed to create composite key: %s", err.Error())
	}
	sellerPriceHash, err := ctx.GetStub().GetPrivateDataHash(collectionSeller, marbleForSaleKey)
	if err != nil {
		return fmt.Errorf("failed to get seller price hash: %s", err.Error())
	}
	if sellerPriceHash == nil {
		return fmt.Errorf("seller price for %s does not exist", marble.ID)
	}

	// get buyer bid price
	collectionBuyer := "_implicit_org_" + buyerOrgID
	marbleBidKey, err := ctx.GetStub().CreateCompositeKey(typeMarbleBid, []string{marble.ID})
	if err != nil {
		return fmt.Errorf("failed to create composite key: %s", err.Error())
	}
	buyerPriceHash, err := ctx.GetStub().GetPrivateDataHash(collectionBuyer, marbleBidKey)
	if err != nil {
		return fmt.Errorf("failed to get buyer price hash: %s", err.Error())
	}
	if buyerPriceHash == nil {
		return fmt.Errorf("buyer price for %s does not exist", marble.ID)
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

// transferMarbleState makes the public and private state updates for the transferred marble
func transferMarbleState(ctx contractapi.TransactionContextInterface, marble *Marble, immutablePropertiesJSON []byte, clientOrgID string, buyerOrgID string) error {

	// save the marble with the new owner
	marble.OwnerOrg = buyerOrgID

	updatedMarbleJSON, _ := json.Marshal(marble)

	err := ctx.GetStub().PutState(marble.ID, updatedMarbleJSON)
	if err != nil {
		return fmt.Errorf("failed to write marble for buyer: %s", err.Error())
	}

	// Change the endorsement policy to the new owner
	err = setMarbleStateBasedEndorsement(ctx, marble.ID, buyerOrgID)
	if err != nil {
		return fmt.Errorf("failed setting state based endorsement for new owner: %s", err.Error())
	}

	// Transfer the private properties (delete from seller collection, create in buyer collection)

	collectionSeller := "_implicit_org_" + clientOrgID
	err = ctx.GetStub().DelPrivateData(collectionSeller, marble.ID)
	if err != nil {
		return fmt.Errorf("failed to delete Marble private details from seller: %s", err.Error())
	}

	collectionBuyer := "_implicit_org_" + buyerOrgID
	err = ctx.GetStub().PutPrivateData(collectionBuyer, marble.ID, immutablePropertiesJSON)
	if err != nil {
		return fmt.Errorf("failed to put Marble private properties for buyer: %s", err.Error())
	}

	// TODO delete the price records for buyer and seller

	// TODO add a state record for a 'receipt' in both buyer and seller private data collection to record the sales price and date

	return nil
}

// getClientOrgID gets the client org ID.
// The client org ID can optionally be verified against the peer org ID, to ensure that a client from another org doesn't attempt to read or write private data from this peer.
// The only exception in this scenario is for TransferMarble, since the current owner needs to get an endorsement from the buyer's peer.
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

// setMarbleStateBasedEndorsement adds an endorsement policy to a marble so that only a peer from an owning org can update or transfer the marble.
func setMarbleStateBasedEndorsement(ctx contractapi.TransactionContextInterface, marbleID string, orgToEndorse string) error {

	endorsementPolicy, err := statebased.NewStateEP(nil)

	err = endorsementPolicy.AddOrgs(statebased.RoleTypePeer, orgToEndorse)
	if err != nil {
		return fmt.Errorf("failed to add org to endorsement policy: %s", err.Error())
	}
	epBytes, err := endorsementPolicy.Policy()
	if err != nil {
		return fmt.Errorf("failed to create endorsement policy bytes from org: %s", err.Error())
	}
	err = ctx.GetStub().SetStateValidationParameter(marbleID, epBytes)
	if err != nil {
		return fmt.Errorf("failed to set validation parameter on marble: %s", err.Error())
	}

	return nil
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

	collection := "_implicit_org_" + clientOrgID
	return collection, nil
}

func main() {

	chaincode, err := contractapi.NewChaincode(new(SmartContract))

	if err != nil {
		fmt.Printf("Error create transfer marble chaincode: %s", err.Error())
		return
	}

	if err := chaincode.Start(); err != nil {
		fmt.Printf("Error starting marble chaincode: %s", err.Error())
	}
}
