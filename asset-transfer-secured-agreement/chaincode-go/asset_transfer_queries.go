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
	"encoding/json"
	"fmt"
	"time"

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

	// since only public data is accessed in this function, no access control is required

	assetJSON, err := ctx.GetStub().GetState(assetID)
	if err != nil {
		return nil, fmt.Errorf("failed to read from world state: %s", err.Error())
	}
	if assetJSON == nil {
		return nil, fmt.Errorf("%s does not exist", assetID)
	}

	asset := new(Asset)
	_ = json.Unmarshal(assetJSON, asset)

	return asset, nil
}

// GetAssetPrivateProperties returns the immutable asset properties from owner's private data collection
func (s *SmartContract) GetAssetPrivateProperties(ctx contractapi.TransactionContextInterface, assetID string) (string, error) {

	// Get client org id and verify it matches peer org id.
	// In this scenario, client is only authorized to read/write private data from its own peer.
	collection, err := getClientImplicitCollectionName(ctx)
	if err != nil {
		return "", err
	}

	immutableProperties, err := ctx.GetStub().GetPrivateData(collection, assetID)
	if err != nil {
		return "", fmt.Errorf("failed to read asset private properties from client org's collection: %s", err.Error())
	}
	if immutableProperties == nil {
		return "", fmt.Errorf("asset private details does not exist in client org's collection: %s", assetID)
	}

	return string(immutableProperties), nil
}

// GetAssetSalesPrice returns the sales price as an integer
func (s *SmartContract) GetAssetSalesPrice(ctx contractapi.TransactionContextInterface, assetID string) (string, error) {
	return getAssetPrice(ctx, assetID, typeAssetForSale)
}

// GetAssetBidPrice returns the bid price as an integer
func (s *SmartContract) GetAssetBidPrice(ctx contractapi.TransactionContextInterface, assetID string) (string, error) {
	return getAssetPrice(ctx, assetID, typeAssetBid)
}

// getAssetPrice gets the bid or ask price from caller's implicit private data collection
func getAssetPrice(ctx contractapi.TransactionContextInterface, assetID string, priceType string) (string, error) {

	collection, err := getClientImplicitCollectionName(ctx)
	if err != nil {
		return "", err
	}

	assetPriceKey, err := ctx.GetStub().CreateCompositeKey(priceType, []string{assetID})
	if err != nil {
		return "", fmt.Errorf("failed to create composite key: %s", err.Error())
	}

	assetPriceJSON, err := ctx.GetStub().GetPrivateData(collection, assetPriceKey)
	if err != nil {
		return "", fmt.Errorf("failed to read asset price from implicit private data collection: %s", err.Error())
	}
	if assetPriceJSON == nil {
		return "", fmt.Errorf("asset price does not exist: %s", assetID)
	}

	return string(assetPriceJSON), nil
}

// QueryAssetSaleAgreements returns all of an organization's proposed sales
func (s *SmartContract) QueryAssetSaleAgreements(ctx contractapi.TransactionContextInterface) ([]Agreement, error) {
	return queryAgreementsByType(ctx, typeAssetForSale)
}

// QueryAssetBuyAgreements returns all of an organization's proposed buys
func (s *SmartContract) QueryAssetBuyAgreements(ctx contractapi.TransactionContextInterface) ([]Agreement, error) {
	return queryAgreementsByType(ctx, typeAssetBid)
}

func queryAgreementsByType(ctx contractapi.TransactionContextInterface, agreeType string) ([]Agreement, error) {
	collection, err := getClientImplicitCollectionName(ctx)
	if err != nil {
		return nil, err
	}

	// Query for any object type starting with `agreeType`
	agreementsIterator, err := ctx.GetStub().GetPrivateDataByPartialCompositeKey(collection, agreeType, []string{})
	if err != nil {
		return nil, fmt.Errorf("failed to read from private data collection: %s", err.Error())
	}
	defer agreementsIterator.Close()

	agreements := []Agreement{}

	for agreementsIterator.HasNext() {
		resp, err := agreementsIterator.Next()
		if err != nil {
			return nil, err
		}

		newAgree := new(Agreement)
		err = json.Unmarshal(resp.Value, newAgree)
		if err != nil {
			return nil, err
		}

		agreements = append(agreements, *newAgree)
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

	records := []QueryResult{}

	for resultsIterator.HasNext() {
		response, err := resultsIterator.Next()
		if err != nil {
			return nil, err
		}

		asset := new(Asset)
		err = json.Unmarshal(response.Value, asset)
		if err != nil {
			return nil, err
		}

		record := QueryResult{
			TxId:      response.TxId,
			Timestamp: time.Unix(response.Timestamp.Seconds, int64(response.Timestamp.Nanos)),
			Record:    asset,
		}
		records = append(records, record)
	}

	return records, nil
}
