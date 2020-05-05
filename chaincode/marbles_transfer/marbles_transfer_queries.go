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

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

// QueryMarble returns the public marble data
func (s *SmartContract) QueryMarble(ctx contractapi.TransactionContextInterface, marbleID string) (*Marble, error) {

	// since only public data is accessed in this function, no access control is required

	marbleJSON, err := ctx.GetStub().GetState(marbleID)
	if err != nil {
		return nil, fmt.Errorf("failed to read from world state: %s", err.Error())
	}
	if marbleJSON == nil {
		return nil, fmt.Errorf("%s does not exist", marbleID)
	}

	marble := new(Marble)
	_ = json.Unmarshal(marbleJSON, marble)

	return marble, nil
}

// QueryMarblePrivateImmutableProperties returns the immutable marble properties from owner's private data collection
func (s *SmartContract) QueryMarblePrivateImmutableProperties(ctx contractapi.TransactionContextInterface, marbleID string) (string, error) {

	// Get client org id and verify it matches peer org id.
	// In this scenario, client is only authorized to read/write private data from its own peer.
	clientOrgID, err := getClientOrgID(ctx, true)
	if err != nil {
		return "", fmt.Errorf("failed to get verified OrgID: %s", err.Error())
	}

	collection := "_implicit_org_" + clientOrgID

	immutableProperties, err := ctx.GetStub().GetPrivateData(collection, marbleID)
	if err != nil {
		return "", fmt.Errorf("failed to read marble private properties from client org's collection: %s", err.Error())
	}
	if immutableProperties == nil {
		return "", fmt.Errorf("marble private details does not exist in client org's collection: %s", marbleID)
	}

	return string(immutableProperties), nil
}

// QueryMarbleSalesPrice returns the sales price as an integer
func (s *SmartContract) QueryMarbleSalesPrice(ctx contractapi.TransactionContextInterface, marbleID string) (string, error) {
	return queryMarblePrice(ctx, marbleID, typeMarbleForSale)
}

// QueryMarbleBidPrice returns the bid price as an integer
func (s *SmartContract) QueryMarbleBidPrice(ctx contractapi.TransactionContextInterface, marbleID string) (string, error) {
	return queryMarblePrice(ctx, marbleID, typeMarbleBid)
}

// queryMarblePrice gets the bid or ask price from caller's implicit private data collection
func queryMarblePrice(ctx contractapi.TransactionContextInterface, marbleID string, priceType string) (string, error) {

	// Get client org id and verify it matches peer org id.
	// In this scenario, client is only authorized to read/write private data from its own peer.
	clientOrgID, err := getClientOrgID(ctx, true)
	if err != nil {
		return "", fmt.Errorf("failed to get verified OrgID: %s", err.Error())
	}

	collection := "_implicit_org_" + clientOrgID

	marblePriceKey, err := ctx.GetStub().CreateCompositeKey(priceType, []string{marbleID})
	if err != nil {
		return "", fmt.Errorf("failed to create composite key: %s", err.Error())
	}

	marblePriceJSON, err := ctx.GetStub().GetPrivateData(collection, marblePriceKey)
	if err != nil {
		return "", fmt.Errorf("failed to read marble price from implicit private data collection: %s", err.Error())
	}
	if marblePriceJSON == nil {
		return "", fmt.Errorf("marble price does not exist: %s", marbleID)
	}

	return string(marblePriceJSON), nil
}

// TODO add a query to get all of an organization's proposed sales
// Use GetPrivateDataByPartialCompositeKey to find all keys starting with typeMarbleForSale
// hint: see sample at https://github.com/hyperledger/fabric-samples/blob/master/chaincode/marbles02/go/marbles_chaincode.go#L458

// TODO add a query to get all of an organization's proposed buys
// Use GetPrivateDataByPartialCompositeKey to find all keys starting with typeMarbleBid

// TODO add a JSON index and query to return all of an organization's marbles larger than a certain size (only works when using CouchDB state database)
// hint: see sample index at https://github.com/hyperledger/fabric-samples/blob/master/chaincode/marbles02/go/META-INF/statedb/couchdb/indexes/indexOwner.json
// hint: see sample query at https://github.com/hyperledger/fabric-samples/blob/master/chaincode/marbles02/go/marbles_chaincode.go#L515

// TODO add a history query so that users can see the chain of custody for a marble since issuance
// hint: see sample at https://github.com/hyperledger/fabric-samples/blob/master/chaincode/marbles02/go/marbles_chaincode.go#L692
