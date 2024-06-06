/*
 SPDX-License-Identifier: Apache-2.0
*/

package main

import (
	"encoding/json"
	"fmt"
	"time"

	"github.com/golang/protobuf/ptypes"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

// QueryResult structure used for handling result of query
type QueryResult struct {
	Record    *Asset
	TxId      string    `json:"txId"`
	Timestamp time.Time `json:"timestamp"`
}

type Agreement struct {
	ID      string `json:"asset_id"`
	Price   int    `json:"price"`
	TradeID string `json:"trade_id"`
}

// ReadAsset returns the public asset data
func (s *SmartContract) ReadAsset(ctx contractapi.TransactionContextInterface, assetID string) (*Asset, error) {
	// Since only public data is accessed in this function, no access control is required
	assetJSON, err := ctx.GetStub().GetState(assetID)
	if err != nil {
		return nil, fmt.Errorf("failed to read from world state: %v", err)
	}
	if assetJSON == nil {
		return nil, fmt.Errorf("%s does not exist", assetID)
	}

	var asset *Asset
	err = json.Unmarshal(assetJSON, &asset)
	if err != nil {
		return nil, err
	}
	return asset, nil
}

// GetAssetPrivateProperties returns the immutable asset properties from owner's private data collection
func (s *SmartContract) GetAssetPrivateProperties(ctx contractapi.TransactionContextInterface, assetID string) (string, error) {

	collection, err := getClientImplicitCollectionNameAndVerifyClientOrg(ctx)
	if err != nil {
		return "", err
	}

	immutableProperties, err := ctx.GetStub().GetPrivateData(collection, assetID)
	if err != nil {
		return "", fmt.Errorf("failed to read asset private properties from client org's collection: %v", err)
	}
	if immutableProperties == nil {
		return "", fmt.Errorf("asset private details does not exist in client org's collection: %s", assetID)
	}

	return string(immutableProperties), nil
}

// GetAssetSalesPrice returns the sales price
func (s *SmartContract) GetAssetSalesPrice(ctx contractapi.TransactionContextInterface, assetID string) (string, error) {
	return getAssetPrice(ctx, assetID, typeAssetForSale)
}

// GetAssetBidPrice returns the bid price
func (s *SmartContract) GetAssetBidPrice(ctx contractapi.TransactionContextInterface, assetID string) (string, error) {
	return getAssetPrice(ctx, assetID, typeAssetBid)
}

// getAssetPrice gets the bid or ask price from caller's implicit private data collection
func getAssetPrice(ctx contractapi.TransactionContextInterface, assetID string, priceType string) (string, error) {

	collection, err := getClientImplicitCollectionNameAndVerifyClientOrg(ctx)
	if err != nil {
		return "", err
	}

	assetPriceKey, err := ctx.GetStub().CreateCompositeKey(priceType, []string{assetID})
	if err != nil {
		return "", fmt.Errorf("failed to create composite key: %v", err)
	}

	price, err := ctx.GetStub().GetPrivateData(collection, assetPriceKey)
	if err != nil {
		return "", fmt.Errorf("failed to read asset price from implicit private data collection: %v", err)
	}
	if price == nil {
		return "", fmt.Errorf("asset price does not exist: %s", assetID)
	}

	return string(price), nil
}

// QueryAssetSaleAgreements returns all of an organization's proposed sales
func (s *SmartContract) QueryAssetSaleAgreements(ctx contractapi.TransactionContextInterface) ([]Agreement, error) {
	return queryAgreementsByType(ctx, typeAssetForSale)
}

// QueryAssetBuyAgreements returns all of an organization's proposed bids
func (s *SmartContract) QueryAssetBuyAgreements(ctx contractapi.TransactionContextInterface) ([]Agreement, error) {
	return queryAgreementsByType(ctx, typeAssetBid)
}

func queryAgreementsByType(ctx contractapi.TransactionContextInterface, agreeType string) ([]Agreement, error) {
	collection, err := getClientImplicitCollectionNameAndVerifyClientOrg(ctx)
	if err != nil {
		return nil, err
	}

	// Query for any object type starting with `agreeType`
	agreementsIterator, err := ctx.GetStub().GetPrivateDataByPartialCompositeKey(collection, agreeType, []string{})
	if err != nil {
		return nil, fmt.Errorf("failed to read from private data collection: %v", err)
	}
	defer agreementsIterator.Close()

	var agreements []Agreement
	for agreementsIterator.HasNext() {
		resp, err := agreementsIterator.Next()
		if err != nil {
			return nil, err
		}

		var agreement Agreement
		err = json.Unmarshal(resp.Value, &agreement)
		if err != nil {
			return nil, err
		}

		agreements = append(agreements, agreement)
	}

	return agreements, nil
}

// QueryAssetHistory returns the chain of custody for a asset since issuance
func (s *SmartContract) QueryAssetHistory(ctx contractapi.TransactionContextInterface, assetID string) ([]QueryResult, error) {
	resultsIterator, err := ctx.GetStub().GetHistoryForKey(assetID)
	if err != nil {
		return nil, err
	}
	defer resultsIterator.Close()

	var results []QueryResult
	for resultsIterator.HasNext() {
		response, err := resultsIterator.Next()
		if err != nil {
			return nil, err
		}

		var asset *Asset
		err = json.Unmarshal(response.Value, &asset)
		if err != nil {
			return nil, err
		}

		timestamp, err := ptypes.Timestamp(response.Timestamp)
		if err != nil {
			return nil, err
		}
		record := QueryResult{
			TxId:      response.TxId,
			Timestamp: timestamp,
			Record:    asset,
		}
		results = append(results, record)
	}

	return results, nil
}
