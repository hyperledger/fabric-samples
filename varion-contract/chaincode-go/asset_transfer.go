/*
 SPDX-License-Identifier: Apache-2.0
*/

package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"log"
	"time"

	"github.com/golang/protobuf/ptypes"
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
	ObjectType        string `json:"objectType"` // ObjectType is used to distinguish different object types in the same chaincode namespace
	ID                string `json:"assetID"`
	OwnerOrg          string `json:"ownerOrg"`
	PublicDescription string `json:"publicDescription"`
}

type receipt struct {
	price     int
	timestamp time.Time
}

// CreateAsset creates an asset, sets it as owned by the client's org and returns its id
// the id of the asset corresponds to the hash of the properties of the asset that are  passed by transiet field
func (s *SmartContract) CreateAsset(ctx contractapi.TransactionContextInterface, publicDescription string) (string, error) {
	transientMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return "", fmt.Errorf("error getting transient: %v", err)
	}

	// Asset properties must be retrieved from the transient field as they are private
	immutablePropertiesJSON, ok := transientMap["asset_properties"]
	if !ok {
		return "", fmt.Errorf("asset_properties key not found in the transient map")
	}

	// AssetID will be the hash of the asset's properties
	hash := sha256.New()
	hash.Write(immutablePropertiesJSON)
	assetID := hex.EncodeToString(hash.Sum(nil))

	// Get the clientOrgId from the input, will be used for implicit collection, owner, and state-based endorsement policy
	clientOrgID, err := getClientOrgID(ctx)
	if err != nil {
		return "", err
	}

	// In this scenario, client is only authorized to read/write private data from its own peer, therefore verify client org id matches peer org id.
	err = verifyClientOrgMatchesPeerOrg(clientOrgID)
	if err != nil {
		return "", err
	}

	asset := Asset{
		ObjectType:        "asset",
		ID:                assetID,
		OwnerOrg:          clientOrgID,
		PublicDescription: publicDescription,
	}
	assetBytes, err := json.Marshal(asset)
	if err != nil {
		return "", fmt.Errorf("failed to create asset JSON: %v", err)
	}

	err = ctx.GetStub().PutState(assetID, assetBytes)
	if err != nil {
		return "", fmt.Errorf("failed to put asset in public data: %v", err)
	}

	// Set the endorsement policy such that an owner org peer is required to endorse future updates.
	// In practice, consider additional endorsers such as a trusted third party to further secure transfers.
	endorsingOrgs := []string{clientOrgID}
	err = setAssetStateBasedEndorsement(ctx, asset.ID, endorsingOrgs)
	if err != nil {
		return "", fmt.Errorf("failed setting state based endorsement for buyer and seller: %v", err)
	}

	// Persist private immutable asset properties to owner's private data collection
	collection := buildCollectionName(clientOrgID)
	err = ctx.GetStub().PutPrivateData(collection, assetID, immutablePropertiesJSON)
	if err != nil {
		return "", fmt.Errorf("failed to put Asset private details: %v", err)
	}

	return assetID, nil
}

// ChangePublicDescription updates the assets public description. Only the current owner can update the public description
func (s *SmartContract) ChangePublicDescription(ctx contractapi.TransactionContextInterface, assetID string, newDescription string) error {

	clientOrgID, err := getClientOrgID(ctx)
	if err != nil {
		return err
	}

	asset, err := s.ReadAsset(ctx, assetID)
	if err != nil {
		return fmt.Errorf("failed to get asset: %v", err)
	}

	// Auth check to ensure that client's org actually owns the asset
	if clientOrgID != asset.OwnerOrg {
		return fmt.Errorf("a client from %s cannot update the description of a asset owned by %s", clientOrgID, asset.OwnerOrg)
	}

	asset.PublicDescription = newDescription
	updatedAssetJSON, err := json.Marshal(asset)
	if err != nil {
		return fmt.Errorf("failed to marshal asset: %v", err)
	}

	return ctx.GetStub().PutState(assetID, updatedAssetJSON)
}

// AgreeToSell adds seller's asking price to seller's implicit private data collection.
func (s *SmartContract) AgreeToSell(ctx contractapi.TransactionContextInterface, assetID string) error {
	asset, err := s.ReadAsset(ctx, assetID)
	if err != nil {
		return err
	}

	clientOrgID, err := getClientOrgID(ctx)
	if err != nil {
		return err
	}

	// Verify that this client belongs to the peer's org
	err = verifyClientOrgMatchesPeerOrg(clientOrgID)
	if err != nil {
		return err
	}

	// Verify that this clientOrgId actually owns the asset.
	if clientOrgID != asset.OwnerOrg {
		return fmt.Errorf("a client from %s cannot sell an asset owned by %s", clientOrgID, asset.OwnerOrg)
	}

	return agreeToPrice(ctx, assetID, typeAssetForSale)
}

// AgreeToBuy adds buyer's bid price and asset properties to buyer's implicit private data collection
func (s *SmartContract) AgreeToBuy(ctx contractapi.TransactionContextInterface, assetID string) error {
	transientMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return fmt.Errorf("error getting transient: %v", err)
	}

	clientOrgID, err := getClientOrgID(ctx)
	if err != nil {
		return err
	}

	// Verify that this client belongs to the peer's org
	err = verifyClientOrgMatchesPeerOrg(clientOrgID)
	if err != nil {
		return err
	}

	// Asset properties must be retrieved from the transient field as they are private
	immutablePropertiesJSON, ok := transientMap["asset_properties"]
	if !ok {
		return fmt.Errorf("asset_properties key not found in the transient map")
	}

	// Persist private immutable asset properties to seller's private data collection
	collection := buildCollectionName(clientOrgID)
	err = ctx.GetStub().PutPrivateData(collection, assetID, immutablePropertiesJSON)
	if err != nil {
		return fmt.Errorf("failed to put Asset private details: %v", err)
	}

	return agreeToPrice(ctx, assetID, typeAssetBid)
}

// agreeToPrice adds a bid or ask price to caller's implicit private data collection
func agreeToPrice(ctx contractapi.TransactionContextInterface, assetID string, priceType string) error {
	// In this scenario, both buyer and seller are authoried to read/write private about transfer after seller agrees to sell.
	clientOrgID, err := getClientOrgID(ctx)
	if err != nil {
		return err
	}

	transMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return fmt.Errorf("error getting transient: %v", err)
	}

	// Asset price must be retrieved from the transient field as they are private
	price, ok := transMap["asset_price"]
	if !ok {
		return fmt.Errorf("asset_price key not found in the transient map")
	}

	collection := buildCollectionName(clientOrgID)

	// Persist the agreed to price in a collection sub-namespace based on priceType key prefix,
	// to avoid collisions between private asset properties, sell price, and buy price
	assetPriceKey, err := ctx.GetStub().CreateCompositeKey(priceType, []string{assetID})
	if err != nil {
		return fmt.Errorf("failed to create composite key: %v", err)
	}

	// The Price hash will be verified later, therefore always pass and persist price bytes as is,
	// so that there is no risk of nondeterministic marshaling.
	err = ctx.GetStub().PutPrivateData(collection, assetPriceKey, price)
	if err != nil {
		return fmt.Errorf("failed to put asset bid: %v", err)
	}

	return nil
}

// VerifyAssetProperties allows a buyer to validate the properties of
// an asset they intend to buy against the owner's implicit private data collection
// and verifies that the asset properties never changed from the origin of the asset by checking their hash against the assetID
func (s *SmartContract) VerifyAssetProperties(ctx contractapi.TransactionContextInterface, assetID string) (bool, error) {
	transMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return false, fmt.Errorf("error getting transient: %v", err)
	}

	// Asset properties must be retrieved from the transient field as they are private
	immutablePropertiesJSON, ok := transMap["asset_properties"]
	if !ok {
		return false, fmt.Errorf("asset_properties key not found in the transient map")
	}

	asset, err := s.ReadAsset(ctx, assetID)
	if err != nil {
		return false, fmt.Errorf("failed to get asset: %v", err)
	}

	collectionOwner := buildCollectionName(asset.OwnerOrg)
	immutablePropertiesOnChainHash, err := ctx.GetStub().GetPrivateDataHash(collectionOwner, assetID)
	if err != nil {
		return false, fmt.Errorf("failed to read asset private properties hash from seller's collection: %v", err)
	}
	if immutablePropertiesOnChainHash == nil {
		return false, fmt.Errorf("asset private properties hash does not exist: %s", assetID)
	}

	hash := sha256.New()
	hash.Write(immutablePropertiesJSON)
	calculatedPropertiesHash := hash.Sum(nil)

	// verify that the hash of the passed immutable properties matches the on-chain hash
	if !bytes.Equal(immutablePropertiesOnChainHash, calculatedPropertiesHash) {
		return false, fmt.Errorf("hash %x for passed immutable properties %s does not match on-chain hash %x",
			calculatedPropertiesHash,
			immutablePropertiesJSON,
			immutablePropertiesOnChainHash,
		)
	}

	// verify that the hash of the passed immutable properties and on chain hash matches the assetID
	if !(hex.EncodeToString(immutablePropertiesOnChainHash) == assetID) {
		return false, fmt.Errorf("hash %x for passed immutable properties %s does match on-chain hash %x but do not match assetID %s: asset was altered from its initial form",
			calculatedPropertiesHash,
			immutablePropertiesJSON,
			immutablePropertiesOnChainHash,
			assetID)
	}

	return true, nil
}

// TransferAsset checks transfer conditions and then transfers asset state to buyer.
// TransferAsset can only be called by current owner
func (s *SmartContract) TransferAsset(ctx contractapi.TransactionContextInterface, assetID string, buyerOrgID string) error {
	clientOrgID, err := getClientOrgID(ctx)
	if err != nil {
		return err
	}

	transMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return fmt.Errorf("error getting transient data: %v", err)
	}

	priceJSON, ok := transMap["asset_price"]
	if !ok {
		return fmt.Errorf("asset_price key not found in the transient map")
	}

	var agreement Agreement
	err = json.Unmarshal(priceJSON, &agreement)
	if err != nil {
		return fmt.Errorf("failed to unmarshal price JSON: %v", err)
	}

	asset, err := s.ReadAsset(ctx, assetID)
	if err != nil {
		return fmt.Errorf("failed to get asset: %v", err)
	}

	err = verifyTransferConditions(ctx, asset, clientOrgID, buyerOrgID, priceJSON)
	if err != nil {
		return fmt.Errorf("failed transfer verification: %v", err)
	}

	err = transferAssetState(ctx, asset, clientOrgID, buyerOrgID, agreement.Price)
	if err != nil {
		return fmt.Errorf("failed asset transfer: %v", err)
	}

	return nil

}

// verifyTransferConditions checks that client org currently owns asset and that both parties have agreed on price
func verifyTransferConditions(ctx contractapi.TransactionContextInterface,
	asset *Asset,
	clientOrgID string,
	buyerOrgID string,
	priceJSON []byte) error {

	// CHECK1: Auth check to ensure that client's org actually owns the asset

	if clientOrgID != asset.OwnerOrg {
		return fmt.Errorf("a client from %s cannot transfer a asset owned by %s", clientOrgID, asset.OwnerOrg)
	}

	// CHECK2: Verify that buyer and seller on-chain asset defintion hash matches

	collectionSeller := buildCollectionName(clientOrgID)
	collectionBuyer := buildCollectionName(buyerOrgID)
	sellerPropertiesOnChainHash, err := ctx.GetStub().GetPrivateDataHash(collectionSeller, asset.ID)
	if err != nil {
		return fmt.Errorf("failed to read asset private properties hash from seller's collection: %v", err)
	}
	if sellerPropertiesOnChainHash == nil {
		return fmt.Errorf("asset private properties hash does not exist: %s", asset.ID)
	}
	buyerPropertiesOnChainHash, err := ctx.GetStub().GetPrivateDataHash(collectionBuyer, asset.ID)
	if err != nil {
		return fmt.Errorf("failed to read asset private properties hash from seller's collection: %v", err)
	}
	if buyerPropertiesOnChainHash == nil {
		return fmt.Errorf("asset private properties hash does not exist: %s", asset.ID)
	}

	// verify that buyer and seller on-chain asset defintion hash matches
	if !bytes.Equal(sellerPropertiesOnChainHash, buyerPropertiesOnChainHash) {
		return fmt.Errorf("on chain hash of seller %x does not match on-chain hash of buyer %x",
			sellerPropertiesOnChainHash,
			buyerPropertiesOnChainHash,
		)
	}

	// CHECK3: Verify that seller and buyer agreed on the same price

	// Get sellers asking price
	assetForSaleKey, err := ctx.GetStub().CreateCompositeKey(typeAssetForSale, []string{asset.ID})
	if err != nil {
		return fmt.Errorf("failed to create composite key: %v", err)
	}
	sellerPriceHash, err := ctx.GetStub().GetPrivateDataHash(collectionSeller, assetForSaleKey)
	if err != nil {
		return fmt.Errorf("failed to get seller price hash: %v", err)
	}
	if sellerPriceHash == nil {
		return fmt.Errorf("seller price for %s does not exist", asset.ID)
	}

	// Get buyers bid price
	assetBidKey, err := ctx.GetStub().CreateCompositeKey(typeAssetBid, []string{asset.ID})
	if err != nil {
		return fmt.Errorf("failed to create composite key: %v", err)
	}
	buyerPriceHash, err := ctx.GetStub().GetPrivateDataHash(collectionBuyer, assetBidKey)
	if err != nil {
		return fmt.Errorf("failed to get buyer price hash: %v", err)
	}
	if buyerPriceHash == nil {
		return fmt.Errorf("buyer price for %s does not exist", asset.ID)
	}

	hash := sha256.New()
	hash.Write(priceJSON)
	calculatedPriceHash := hash.Sum(nil)

	// Verify that the hash of the passed price matches the on-chain sellers price hash
	if !bytes.Equal(calculatedPriceHash, sellerPriceHash) {
		return fmt.Errorf("hash %x for passed price JSON %s does not match on-chain hash %x, seller hasn't agreed to the passed trade id and price",
			calculatedPriceHash,
			priceJSON,
			sellerPriceHash,
		)
	}

	// Verify that the hash of the passed price matches the on-chain buyer price hash
	if !bytes.Equal(calculatedPriceHash, buyerPriceHash) {
		return fmt.Errorf("hash %x for passed price JSON %s does not match on-chain hash %x, buyer hasn't agreed to the passed trade id and price",
			calculatedPriceHash,
			priceJSON,
			buyerPriceHash,
		)
	}

	return nil
}

// transferAssetState performs the public and private state updates for the transferred asset
// changes the endorsement for the transferred asset sbe to the new owner org
func transferAssetState(ctx contractapi.TransactionContextInterface, asset *Asset, clientOrgID string, buyerOrgID string, price int) error {

	// Update ownership in public state
	asset.OwnerOrg = buyerOrgID
	updatedAsset, err := json.Marshal(asset)
	if err != nil {
		return err
	}
	err = ctx.GetStub().PutState(asset.ID, updatedAsset)
	if err != nil {
		return fmt.Errorf("failed to write asset for buyer: %v", err)
	}

	// Changes the endorsement policy to the new owner org
	endorsingOrgs := []string{buyerOrgID}
	err = setAssetStateBasedEndorsement(ctx, asset.ID, endorsingOrgs)
	if err != nil {
		return fmt.Errorf("failed setting state based endorsement for new owner: %v", err)
	}

	// Delete asset description from seller collection
	collectionSeller := buildCollectionName(clientOrgID)
	err = ctx.GetStub().DelPrivateData(collectionSeller, asset.ID)
	if err != nil {
		return fmt.Errorf("failed to delete Asset private details from seller: %v", err)
	}

	// Delete the price records for seller
	assetPriceKey, err := ctx.GetStub().CreateCompositeKey(typeAssetForSale, []string{asset.ID})
	if err != nil {
		return fmt.Errorf("failed to create composite key for seller: %v", err)
	}
	err = ctx.GetStub().DelPrivateData(collectionSeller, assetPriceKey)
	if err != nil {
		return fmt.Errorf("failed to delete asset price from implicit private data collection for seller: %v", err)
	}

	// Delete the price records for buyer
	collectionBuyer := buildCollectionName(buyerOrgID)
	assetPriceKey, err = ctx.GetStub().CreateCompositeKey(typeAssetBid, []string{asset.ID})
	if err != nil {
		return fmt.Errorf("failed to create composite key for buyer: %v", err)
	}
	err = ctx.GetStub().DelPrivateData(collectionBuyer, assetPriceKey)
	if err != nil {
		return fmt.Errorf("failed to delete asset price from implicit private data collection for buyer: %v", err)
	}

	// Keep record for a 'receipt' in both buyers and sellers private data collection to record the sale price and date.
	// Persist the agreed to price in a collection sub-namespace based on receipt key prefix.
	receiptBuyKey, err := ctx.GetStub().CreateCompositeKey(typeAssetBuyReceipt, []string{asset.ID, ctx.GetStub().GetTxID()})
	if err != nil {
		return fmt.Errorf("failed to create composite key for receipt: %v", err)
	}

	txTimestamp, err := ctx.GetStub().GetTxTimestamp()
	if err != nil {
		return fmt.Errorf("failed to create timestamp for receipt: %v", err)
	}

	timestamp, err := ptypes.Timestamp(txTimestamp)
	if err != nil {
		return err
	}
	assetReceipt := receipt{
		price:     price,
		timestamp: timestamp,
	}
	receipt, err := json.Marshal(assetReceipt)
	if err != nil {
		return fmt.Errorf("failed to marshal receipt: %v", err)
	}

	err = ctx.GetStub().PutPrivateData(collectionBuyer, receiptBuyKey, receipt)
	if err != nil {
		return fmt.Errorf("failed to put private asset receipt for buyer: %v", err)
	}

	receiptSaleKey, err := ctx.GetStub().CreateCompositeKey(typeAssetSaleReceipt, []string{ctx.GetStub().GetTxID(), asset.ID})
	if err != nil {
		return fmt.Errorf("failed to create composite key for receipt: %v", err)
	}

	err = ctx.GetStub().PutPrivateData(collectionSeller, receiptSaleKey, receipt)
	if err != nil {
		return fmt.Errorf("failed to put private asset receipt for seller: %v", err)
	}

	return nil
}

// getClientOrgID gets the client org ID.
func getClientOrgID(ctx contractapi.TransactionContextInterface) (string, error) {
	clientOrgID, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return "", fmt.Errorf("failed getting client's orgID: %v", err)
	}

	return clientOrgID, nil
}

// getClientImplicitCollectionNameAndVerifyClientOrg gets the implicit collection for the client and checks that the client is from the same org as the peer
func getClientImplicitCollectionNameAndVerifyClientOrg(ctx contractapi.TransactionContextInterface) (string, error) {
	clientOrgID, err := getClientOrgID(ctx)
	if err != nil {
		return "", err
	}

	err = verifyClientOrgMatchesPeerOrg(clientOrgID)
	if err != nil {
		return "", err
	}

	return buildCollectionName(clientOrgID), nil
}

// verifyClientOrgMatchesPeerOrg checks that the client is from the same org as the peer
func verifyClientOrgMatchesPeerOrg(clientOrgID string) error {
	peerOrgID, err := shim.GetMSPID()
	if err != nil {
		return fmt.Errorf("failed getting peer's orgID: %v", err)
	}

	if clientOrgID != peerOrgID {
		return fmt.Errorf("client from org %s is not authorized to read or write private data from an org %s peer",
			clientOrgID,
			peerOrgID,
		)
	}

	return nil
}

// buildCollectionName returns the implicit collection name for an org
func buildCollectionName(clientOrgID string) string {
	return fmt.Sprintf("_implicit_org_%s", clientOrgID)
}

// setAssetStateBasedEndorsement adds an endorsement policy to an asset so that the passed orgs need to agree upon transfer
func setAssetStateBasedEndorsement(ctx contractapi.TransactionContextInterface, assetID string, orgsToEndorse []string) error {
	endorsementPolicy, err := statebased.NewStateEP(nil)
	if err != nil {
		return err
	}
	err = endorsementPolicy.AddOrgs(statebased.RoleTypePeer, orgsToEndorse...)
	if err != nil {
		return fmt.Errorf("failed to add org to endorsement policy: %v", err)
	}
	policy, err := endorsementPolicy.Policy()
	if err != nil {
		return fmt.Errorf("failed to create endorsement policy bytes from org: %v", err)
	}
	err = ctx.GetStub().SetStateValidationParameter(assetID, policy)
	if err != nil {
		return fmt.Errorf("failed to set validation parameter on asset: %v", err)
	}

	return nil
}

// GetAssetHashId allows a potential buyer to validate the properties of an asset against the asset Id hash on chain and returns the hash
func (s *SmartContract) GetAssetHashId(ctx contractapi.TransactionContextInterface) (string, error) {
	transientMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return "", fmt.Errorf("error getting transient: %v", err)
	}

	// Asset properties must be retrieved from the transient field as they are private
	propertiesJSON, ok := transientMap["asset_properties"]
	if !ok {
		return "", fmt.Errorf("asset_properties key not found in the transient map")
	}

	hash := sha256.New()
	hash.Write(propertiesJSON)
	assetID := hex.EncodeToString(hash.Sum(nil))

	asset, err := s.ReadAsset(ctx, assetID)
	if err != nil {
		return "", fmt.Errorf("failed to get asset: %v, asset properies provided do not represent any on chain asset", err)
	}
	if asset.ID != assetID {
		return "", fmt.Errorf("Asset properies provided do not correpond to any on chain asset")
	}
	return asset.ID, nil
}

func main() {
	chaincode, err := contractapi.NewChaincode(new(SmartContract))
	if err != nil {
		log.Panicf("Error create transfer asset chaincode: %v", err)
	}

	if err := chaincode.Start(); err != nil {
		log.Panicf("Error starting asset chaincode: %v", err)
	}
}
