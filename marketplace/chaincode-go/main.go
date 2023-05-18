/*
 * SPDX-License-Identifier: Apache-2.0
 */

package main

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"github.com/hyperledger/fabric-chaincode-go/shim"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
	"github.com/hyperledger/fabric-contract-api-go/metadata"
	"strconv"
)

type MarketplaceContract struct {
	contractapi.Contract
}

type ListingItem struct {
	TokenAddr string `json:"tokenAddr"`
	Id        string `json:"id"`
}

type Listing struct {
	ListingId    string `json:"listingId"`
	Creator      string `json:"creator"`
	Price        int    `json:"price"`
	CurrencyAddr string `json:"currencyAddr"`
}

type ListingCreated struct {
	ListingId    string        `json:"listingId"`
	Creator      string        `json:"creator"`
	Price        int           `json:"price"`
	CurrencyAddr string        `json:"currencyAddr"`
	Nfts         []ListingItem `json:"nfts"`
	Batches      []ListingItem `json:"batches"`
	Collections  []ListingItem `json:"collections"`
}

type ListingItemSold struct {
	ListingId    string `json:"listingId"`
	Creator      string `json:"creator"`
	Price        int    `json:"price"`
	TokenAddr    string `json:"tokenAddr"`
	CurrencyAddr string `json:"currencyAddr"`
	TokenId      string `json:"tokenId"`
	Buyer        string `json:"buyer"`
}

type ListingCanceled struct {
	ListingId string `json:"listingId"`
}

const listingPrefix = "listing"

func _readListing(ctx contractapi.TransactionContextInterface, listingId string) (*Listing, error) {
	listingKey, err := ctx.GetStub().CreateCompositeKey(listingPrefix, []string{listingId})
	if err != nil {
		return nil, fmt.Errorf("failed to CreateCompositeKey %s: %v", listingId, err)
	}

	listingBytes, err := ctx.GetStub().GetState(listingKey)
	if err != nil {
		return nil, fmt.Errorf("failed to GetState %s: %v", listingId, err)
	}

	listing := new(Listing)
	err = json.Unmarshal(listingBytes, listing)
	if err != nil {
		return nil, fmt.Errorf("failed to Unmarshal listingBytes: %v", err)
	}

	return listing, nil
}

func (c *MarketplaceContract) List(ctx contractapi.TransactionContextInterface, creator string, listingId string, currencyAddr string, nfts []ListingItem, batches []ListingItem, collections []ListingItem, price int) (*Listing, error) {

	listing := new(Listing)
	listing.ListingId = listingId
	listing.Creator = creator
	listing.Price = price
	listing.CurrencyAddr = currencyAddr

	listingKey, err := ctx.GetStub().CreateCompositeKey(listingPrefix, []string{listingId})

	if err != nil {
		return nil, fmt.Errorf("failed to CreateCompositeKey to nftKey: %v", err)
	}

	listingBytes, err := json.Marshal(listing)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal listing: %v", err)
	}

	err = ctx.GetStub().PutState(listingKey, listingBytes)
	if err != nil {
		return nil, fmt.Errorf("failed to PutState listingBytes %s: %v", listingBytes, err)
	}

	listingCreated := new(ListingCreated)
	listingCreated.ListingId = listingId
	listingCreated.Creator = creator
	listingCreated.Price = price
	listingCreated.CurrencyAddr = currencyAddr
	listingCreated.Nfts = nfts
	listingCreated.Batches = batches
	listingCreated.Collections = collections

	listingCreatedBytes, err := json.Marshal(listingCreated)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal listingCreatedBytes: %v", err)
	}

	err = ctx.GetStub().SetEvent("ListingCreated", listingCreatedBytes)
	if err != nil {
		return nil, fmt.Errorf("failed to SetEvent listingCreatedBytes %s: %v", listingCreatedBytes, err)
	}

	return listing, nil
}

func (c *MarketplaceContract) CancelListing(ctx contractapi.TransactionContextInterface, listingId string) (bool, error) {

	sender64, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return false, fmt.Errorf("failed to GetClientIdentity: %v", err)
	}

	senderBytes, err := base64.StdEncoding.DecodeString(sender64)
	if err != nil {
		return false, fmt.Errorf("failed to DecodeString senderBytes: %v", err)
	}
	sender := string(senderBytes)

	listing, err := _readListing(ctx, listingId)
	if err != nil {
		return false, fmt.Errorf("failed to _readListing: %v", err)
	}

	if listing.Creator != sender {
		return false, fmt.Errorf("sender is not owner of listing")
	}

	listingKey, err := ctx.GetStub().CreateCompositeKey(listingPrefix, []string{listingId})

	if err != nil {
		return false, fmt.Errorf("failed to CreateCompositeKey to nftKey: %v", err)
	}

	err = ctx.GetStub().DelState(listingKey)
	if err != nil {
		return false, fmt.Errorf("failed to DelState %s: %v", listingKey, err)
	}

	listingCanceled := new(ListingCanceled)
	listingCanceled.ListingId = listingId

	listingCanceledBytes, err := json.Marshal(listingCanceled)
	if err != nil {
		return false, fmt.Errorf("failed to marshal listingCanceledBytes: %v", err)
	}

	err = ctx.GetStub().SetEvent("ListingCanceled", listingCanceledBytes)
	if err != nil {
		return false, fmt.Errorf("failed to SetEvent listingCanceledBytes %s: %v", listingCanceledBytes, err)
	}

	return true, nil
}

func (c *MarketplaceContract) Buy(ctx contractapi.TransactionContextInterface, buyer string, listingId string, tokenAddr string, tokenId string) (bool, error) {

	listing, err := _readListing(ctx, listingId)
	if err != nil {
		return false, fmt.Errorf("failed to _readListing: %v", err)
	}

	// payment
	stub := ctx.GetStub()

	if listing.Price > 0 {
		params := []string{"TransferFrom", buyer, listing.Creator, strconv.Itoa(listing.Price)}
		invokeArgs := make([][]byte, len(params))

		for i, arg := range params {
			invokeArgs[i] = []byte(arg)
		}

		response := stub.InvokeChaincode(listing.CurrencyAddr, invokeArgs, "firefly")

		if response.Status != shim.OK {
			return false, fmt.Errorf("Failed to invoke erc-20 chaincode. Got error: %v %s %s %s", response, buyer, listing.Creator, strconv.Itoa(listing.Price))
		}
	}

	// transfer tokens
	tokenTransferParams := []string{"TransferFrom", listing.Creator, buyer, tokenId}
	tokenTransferInvokeArgs := make([][]byte, len(tokenTransferParams))

	for i, arg := range tokenTransferParams {
		tokenTransferInvokeArgs[i] = []byte(arg)
	}

	tokenTransferResponse := stub.InvokeChaincode(tokenAddr, tokenTransferInvokeArgs, "firefly")

	if tokenTransferResponse.Status != shim.OK {
		return false, fmt.Errorf("Failed to invoke erc-721 chaincode. Got error: %s", tokenTransferResponse.Payload)
	}

	listingItemSold := new(ListingItemSold)
	listingItemSold.ListingId = listingId
	listingItemSold.Creator = listing.Creator
	listingItemSold.Price = listing.Price
	listingItemSold.CurrencyAddr = listing.CurrencyAddr
	listingItemSold.TokenAddr = tokenAddr
	listingItemSold.Buyer = buyer
	listingItemSold.TokenId = tokenId

	listingItemSoldBytes, err := json.Marshal(listingItemSold)
	if err != nil {
		return false, fmt.Errorf("failed to marshal listingItemSoldBytes: %v", err)
	}

	err = ctx.GetStub().SetEvent("ListingItemSold", listingItemSoldBytes)
	if err != nil {
		return false, fmt.Errorf("failed to SetEvent listingItemSoldBytes %s: %v", listingItemSoldBytes, err)
	}

	return true, nil
}

func main() {
	marketplaceContract := new(MarketplaceContract)
	marketplaceContract.Info.Version = "0.0.1"
	marketplaceContract.Info.Description = "MarketplaceContract fabric port"
	marketplaceContract.Info.License = new(metadata.LicenseMetadata)
	marketplaceContract.Info.License.Name = "Apache-2.0"
	marketplaceContract.Info.Contact = new(metadata.ContactMetadata)
	marketplaceContract.Info.Contact.Name = "Marketplace"

	chaincode, err := contractapi.NewChaincode(marketplaceContract)
	chaincode.Info.Title = "Marketplace chaincode"
	chaincode.Info.Version = "0.0.1"

	if err != nil {
		panic("Could not create chaincode from MarketplaceContract." + err.Error())
	}

	err = chaincode.Start()

	if err != nil {
		panic("Failed to start chaincode. " + err.Error())
	}
}
