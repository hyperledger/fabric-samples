/*
 SPDX-License-Identifier: Apache-2.0
*/

// ====CHAINCODE EXECUTION SAMPLES (CLI) ==================

// ==== Invoke assets ====
// peer chaincode invoke -C myc1 -n asset_transfer -c '{"Args":["CreateAsset","asset1","blue","35","tom"]}'
// peer chaincode invoke -C myc1 -n asset_transfer -c '{"Args":["CreateAsset","asset2","red","50","tom"]}'
// peer chaincode invoke -C myc1 -n asset_transfer -c '{"Args":["CreateAsset","asset3","blue","70","tom"]}'
// peer chaincode invoke -C myc1 -n asset_transfer -c '{"Args":["TransferAsset","asset2","jerry"]}'
// peer chaincode invoke -C myc1 -n asset_transfer -c '{"Args":["TransferAssetBasedOnColor","blue","jerry"]}'
// peer chaincode invoke -C myc1 -n asset_transfer -c '{"Args":["DeleteAsset","asset1"]}'

// ==== Query assets ====
// peer chaincode query -C myc1 -n asset_transfer -c '{"Args":["ReadAsset","asset1"]}'
// peer chaincode query -C myc1 -n asset_transfer -c '{"Args":["GetAssetsByRange","asset1","asset3"]}'
// peer chaincode query -C myc1 -n asset_transfer -c '{"Args":["GetAssetHistory","asset1"]}'

// Rich Query (Only supported if CouchDB is used as state database):
// peer chaincode query -C myc1 -n asset_transfer -c '{"Args":["QueryAssetsByOwner","tom"]}'
// peer chaincode query -C myc1 -n asset_transfer -c '{"Args":["QueryAssets","{\"selector\":{\"owner\":\"tom\"}}"]}'

// Rich Query with Pagination (Only supported if CouchDB is used as state database):
// peer chaincode query -C myc1 -n asset_transfer -c '{"Args":["QueryAssetsWithPagination","{\"selector\":{\"owner\":\"tom\"}}","3",""]}'

// INDEXES TO SUPPORT COUCHDB RICH QUERIES
//
// Indexes in CouchDB are required in order to make JSON queries efficient and are required for
// any JSON query with a sort. Indexes may be packaged alongside
// chaincode in a META-INF/statedb/couchdb/indexes directory. Each index must be defined in its own
// text file with extension *.json with the index definition formatted in JSON following the
// CouchDB index JSON syntax as documented at:
// http://docs.couchdb.org/en/2.3.1/api/database/find.html#db-index
//
// This asset transfer ledger example chaincode demonstrates a packaged
// index which you can find in META-INF/statedb/couchdb/indexes/indexOwner.json.
//
// If you have access to the your peer's CouchDB state database in a development environment,
// you may want to iteratively test various indexes in support of your chaincode queries.  You
// can use the CouchDB Fauxton interface or a command line curl utility to create and update
// indexes. Then once you finalize an index, include the index definition alongside your
// chaincode in the META-INF/statedb/couchdb/indexes directory, for packaging and deployment
// to managed environments.
//
// In the examples below you can find index definitions that support asset transfer ledger
// chaincode queries, along with the syntax that you can use in development environments
// to create the indexes in the CouchDB Fauxton interface or a curl command line utility.
//

// Index for docType, owner.
//
// Example curl command line to define index in the CouchDB channel_chaincode database
// curl -i -X POST -H "Content-Type: application/json" -d "{\"index\":{\"fields\":[\"docType\",\"owner\"]},\"name\":\"indexOwner\",\"ddoc\":\"indexOwnerDoc\",\"type\":\"json\"}" http://hostname:port/myc1_assets/_index
//

// Index for docType, owner, size (descending order).
//
// Example curl command line to define index in the CouchDB channel_chaincode database
// curl -i -X POST -H "Content-Type: application/json" -d "{\"index\":{\"fields\":[{\"size\":\"desc\"},{\"docType\":\"desc\"},{\"owner\":\"desc\"}]},\"ddoc\":\"indexSizeSortDoc\", \"name\":\"indexSizeSortDesc\",\"type\":\"json\"}" http://hostname:port/myc1_assets/_index

// Rich Query with index design doc and index name specified (Only supported if CouchDB is used as state database):
//   peer chaincode query -C myc1 -n asset_transfer -c '{"Args":["QueryAssets","{\"selector\":{\"docType\":\"asset\",\"owner\":\"tom\"}, \"use_index\":[\"_design/indexOwnerDoc\", \"indexOwner\"]}"]}'

// Rich Query with index design doc specified only (Only supported if CouchDB is used as state database):
//   peer chaincode query -C myc1 -n asset_transfer -c '{"Args":["QueryAssets","{\"selector\":{\"docType\":{\"$eq\":\"asset\"},\"owner\":{\"$eq\":\"tom\"},\"size\":{\"$gt\":0}},\"fields\":[\"docType\",\"owner\",\"size\"],\"sort\":[{\"size\":\"desc\"}],\"use_index\":\"_design/indexSizeSortDoc\"}"]}'

package main

import (
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"github.com/hyperledger/fabric-chaincode-go/shim"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

// SimpleChaincode example simple Chaincode implementation
type SimpleChaincode struct {
	contractapi.Contract
}

type asset struct {
	ObjectType     string `json:"docType"` //docType is used to distinguish the various types of objects in state database
	ID             string `json:"ID"`      //the fieldtags are needed to keep case from bouncing around
	Color          string `json:"color"`
	Size           int    `json:"size"`
	Owner          string `json:"owner"`
	AppraisedValue int    `json:"appraisedValue"`
}

// QueryResult structure used for handling result of query
type QueryResult struct {
	Record              *asset
	TxId                string    `json:"txId"`
	Timestamp           time.Time `json:"timestamp"`
	FetchedRecordsCount int       `json:"fetchedRecordsCount"`
	Bookmark            string    `json:"bookmark"`
}

// CreateAsset - create a new asset, store into chaincode state
func (t *SimpleChaincode) CreateAsset(ctx contractapi.TransactionContextInterface, ID, color, owner string, size, appraisedValue int) error {

	exists, err := t.AssetExists(ctx, ID)
	if err != nil {
		return fmt.Errorf("Failed to get asset: " + err.Error())
	} else if exists {
		return fmt.Errorf("This asset already exists: " + ID)
	}

	objectType := "asset"
	asset := &asset{
		ObjectType:     objectType,
		ID:             ID,
		Color:          color,
		Size:           size,
		Owner:          owner,
		AppraisedValue: appraisedValue,
	}
	assetJSON, err := json.Marshal(asset)
	if err != nil {
		return err
	}

	err = ctx.GetStub().PutState(ID, assetJSON)
	if err != nil {
		return err
	}

	//  ==== Index the asset to enable color-based range queries, e.g. return all blue assets ====
	//  An 'index' is a normal key/value entry in state.
	//  The key is a composite key, with the elements that you want to range query on listed first.
	//  In our case, the composite key is based on indexName~color~name.
	//  This will enable very efficient state range queries based on composite keys matching indexName~color~*
	indexName := "color~name"
	colorNameIndexKey, err := ctx.GetStub().CreateCompositeKey(indexName, []string{asset.Color, asset.ID})
	if err != nil {
		return err
	}
	//  Save index entry to state. Only the key name is needed, no need to store a duplicate copy of the asset.
	//  Note - passing a 'nil' value will effectively delete the key from state, therefore we pass null character as value
	value := []byte{0x00}
	return ctx.GetStub().PutState(colorNameIndexKey, value)
}

// ReadAsset - read a asset from chaincode state
func (t *SimpleChaincode) ReadAsset(ctx contractapi.TransactionContextInterface, ID string) (*asset, error) {
	assetJSON, err := ctx.GetStub().GetState(ID)
	if err != nil {
		return nil, err
	} else if assetJSON == nil {
		return nil, fmt.Errorf("%s does not exist", ID)
	}

	asset := new(asset)
	err = json.Unmarshal(assetJSON, asset)
	if err != nil {
		return nil, err
	}

	return asset, nil
}

// DeleteAsset - remove a asset key/value pair from state
func (t *SimpleChaincode) DeleteAsset(ctx contractapi.TransactionContextInterface, assetID string) error {

	// to maintain the color~name index, we need to read the asset first and get its color
	assetJSON, err := ctx.GetStub().GetState(assetID) //get the asset from chaincode state
	if err != nil {
		return fmt.Errorf("Failed to get state for %s", assetID)
	} else if assetJSON == nil {
		return fmt.Errorf("Asset does not exist %s", assetID)
	}

	var asset asset
	err = json.Unmarshal([]byte(assetJSON), &asset)
	if err != nil {
		return fmt.Errorf("Failed to decode JSON of %s", assetID)
	}

	err = ctx.GetStub().DelState(assetID) //remove the asset from chaincode state
	if err != nil {
		return fmt.Errorf("Failed to delete state:" + err.Error())
	}

	// maintain the index
	indexName := "color~name"
	colorNameIndexKey, err := ctx.GetStub().CreateCompositeKey(indexName, []string{asset.Color, asset.ID})
	if err != nil {
		return fmt.Errorf(err.Error())
	}

	//  Delete index entry to state.
	return ctx.GetStub().DelState(colorNameIndexKey)
}

// TransferAsset transfers a asset by setting a new owner name on the asset
func (t *SimpleChaincode) TransferAsset(ctx contractapi.TransactionContextInterface, assetID, newOwner string) error {
	newOwner = strings.ToLower(newOwner)

	assetAsBytes, err := ctx.GetStub().GetState(assetID)
	if err != nil {
		return fmt.Errorf("Failed to get asset:" + err.Error())
	} else if assetAsBytes == nil {
		return fmt.Errorf("Asset does not exist")
	}

	assetToTransfer := asset{}
	err = json.Unmarshal(assetAsBytes, &assetToTransfer)
	if err != nil {
		return fmt.Errorf(err.Error())
	}
	assetToTransfer.Owner = newOwner //change the owner

	assetJSON, _ := json.Marshal(assetToTransfer)
	return ctx.GetStub().PutState(assetID, assetJSON)
}

// constructQueryResponseFromIterator constructs a JSON array containing query results from
// a given result iterator
func constructQueryResponseFromIterator(resultsIterator shim.StateQueryIteratorInterface) ([]*QueryResult, error) {

	resp := []*QueryResult{}

	for resultsIterator.HasNext() {
		queryResponse, err := resultsIterator.Next()
		if err != nil {
			return nil, err
		}

		newRecord := new(QueryResult)
		err = json.Unmarshal(queryResponse.Value, newRecord)
		if err != nil {
			return nil, err
		}

		resp = append(resp, newRecord)
	}

	return resp, nil
}

// GetAssetsByRange performs a range query based on the start and end keys provided.
// Read-only function results are not typically submitted to ordering. If the read-only
// results are submitted to ordering, or if the query is used in an update transaction
// and submitted to ordering, then the committing peers will re-execute to guarantee that
// result sets are stable between endorsement time and commit time. The transaction is
// invalidated by the committing peers if the result set has changed between endorsement
// time and commit time.
// Therefore, range queries are a safe option for performing update transactions based on query results.
func (t *SimpleChaincode) GetAssetsByRange(ctx contractapi.TransactionContextInterface, startKey, endKey string) ([]*QueryResult, error) {
	resultsIterator, err := ctx.GetStub().GetStateByRange(startKey, endKey)
	if err != nil {
		return nil, err
	}
	defer resultsIterator.Close()

	return constructQueryResponseFromIterator(resultsIterator)
}

// TransferAssetBasedOnColor will transfer assets of a given color to a certain new owner.
// Uses a GetStateByPartialCompositeKey (range query) against color~name 'index'.
// Committing peers will re-execute range queries to guarantee that result sets are stable
// between endorsement time and commit time. The transaction is invalidated by the
// committing peers if the result set has changed between endorsement time and commit time.
// Therefore, range queries are a safe option for performing update transactions based on query results.
// Example: GetStateByPartialCompositeKey/RangeQuery
func (t *SimpleChaincode) TransferAssetBasedOnColor(ctx contractapi.TransactionContextInterface, color, newOwner string) error {
	newOwner = strings.ToLower(newOwner)

	// Query the color~name index by color
	// This will execute a key range query on all keys starting with 'color'
	coloredAssetResultsIterator, err := ctx.GetStub().GetStateByPartialCompositeKey("color~name", []string{color})
	if err != nil {
		return fmt.Errorf(err.Error())
	}
	defer coloredAssetResultsIterator.Close()

	// Iterate through result set and for each asset found, transfer to newOwner
	var i int
	for i = 0; coloredAssetResultsIterator.HasNext(); i++ {
		// Note that we don't get the value (2nd return variable), we'll just get the asset name from the composite key
		responseRange, err := coloredAssetResultsIterator.Next()
		if err != nil {
			return fmt.Errorf(err.Error())
		}

		// get the color and name from color~name composite key
		_, compositeKeyParts, err := ctx.GetStub().SplitCompositeKey(responseRange.Key)
		if err != nil {
			return fmt.Errorf(err.Error())
		}

		if len(compositeKeyParts) > 1 {
			returnedAssetID := compositeKeyParts[1]

			// Now call the transfer function for the found asset.
			// Re-use the same function that is used to transfer individual assets
			err = t.TransferAsset(ctx, returnedAssetID, newOwner)
			// if the transfer failed break out of loop and return error
			if err != nil {
				return fmt.Errorf("Transfer failed: %v", err)
			}
		}
	}

	return nil
}

// QueryAssetsByOwner queries for assets based on a passed in owner.
// This is an example of a parameterized query where the query logic is baked into the chaincode,
// and accepting a single query parameter (owner).
// Only available on state databases that support rich query (e.g. CouchDB)
// Example: Parameterized rich query
func (t *SimpleChaincode) QueryAssetsByOwner(ctx contractapi.TransactionContextInterface, owner string) ([]*QueryResult, error) {
	queryString := fmt.Sprintf("{\"selector\":{\"docType\":\"asset\",\"owner\":\"%s\"}}", owner)

	return getQueryResultForQueryString(ctx, queryString)
}

// QueryAssets uses a query string to perform a query for assets.
// Query string matching state database syntax is passed in and executed as is.
// Supports ad hoc queries that can be defined at runtime by the client.
// If this is not desired, follow the QueryAssetsForOwner example for parameterized queries.
// Only available on state databases that support rich query (e.g. CouchDB)
// Example: Ad hoc rich query
func (t *SimpleChaincode) QueryAssets(ctx contractapi.TransactionContextInterface, queryString string) ([]*QueryResult, error) {
	return getQueryResultForQueryString(ctx, queryString)
}

// getQueryResultForQueryString executes the passed in query string.
// Result set is built and returned as a byte array containing the JSON results.
func getQueryResultForQueryString(ctx contractapi.TransactionContextInterface, queryString string) ([]*QueryResult, error) {

	resultsIterator, err := ctx.GetStub().GetQueryResult(queryString)
	if err != nil {
		return nil, err
	}
	defer resultsIterator.Close()

	return constructQueryResponseFromIterator(resultsIterator)
}

// GetAssetsByRangeWithPagination performs a range query based on the start & end key,
// page size and a bookmark.
// The number of fetched records will be equal to or lesser than the page size.
// Paginated range queries are only valid for read only transactions.
// Example: Pagination with Range Query
func (t *SimpleChaincode) GetAssetsByRangeWithPagination(ctx contractapi.TransactionContextInterface, startKey,
	endKey, bookmark string, pageSize int) ([]*QueryResult, error) {

	resultsIterator, _, err := ctx.GetStub().GetStateByRangeWithPagination(startKey, endKey, int32(pageSize), bookmark)
	if err != nil {
		return nil, err
	}
	defer resultsIterator.Close()

	return constructQueryResponseFromIterator(resultsIterator)
}

// QueryAssetsWithPagination uses a query string, page size and a bookmark to perform a query
// for assets. Query string matching state database syntax is passed in and executed as is.
// The number of fetched records would be equal to or lesser than the specified page size.
// Supports ad hoc queries that can be defined at runtime by the client.
// If this is not desired, follow the QueryAssetsForOwner example for parameterized queries.
// Only available on state databases that support rich query (e.g. CouchDB)
// Paginated queries are only valid for read only transactions.
// Example: Pagination with Ad hoc Rich Query
func (t *SimpleChaincode) QueryAssetsWithPagination(ctx contractapi.TransactionContextInterface, queryString,
	bookmark string, pageSize int) ([]*QueryResult, error) {
	return getQueryResultForQueryStringWithPagination(ctx, queryString, int32(pageSize), bookmark)
}

// getQueryResultForQueryStringWithPagination executes the passed in query string with
// pagination info. Result set is built and returned as a byte array containing the JSON results.
func getQueryResultForQueryStringWithPagination(ctx contractapi.TransactionContextInterface, queryString string, pageSize int32, bookmark string) ([]*QueryResult, error) {

	resultsIterator, _, err := ctx.GetStub().GetQueryResultWithPagination(queryString, pageSize, bookmark)
	if err != nil {
		return nil, err
	}
	defer resultsIterator.Close()

	return constructQueryResponseFromIterator(resultsIterator)
}

// GetAssetHistory returns the chain of custody for an asset since issuance.
func (t *SimpleChaincode) GetAssetHistory(ctx contractapi.TransactionContextInterface, assetID string) ([]QueryResult, error) {

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

		asset := new(asset)
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

// AssetExists returns true when asset with given ID exists in world state
func (t *SimpleChaincode) AssetExists(ctx contractapi.TransactionContextInterface, id string) (bool, error) {
	assetJSON, err := ctx.GetStub().GetState(id)
	if err != nil {
		return false, fmt.Errorf("Failed to read from world state. %s", err.Error())
	}

	return assetJSON != nil, nil
}

// InitLedger creates sample assets in the ledger
func (t *SimpleChaincode) InitLedger(ctx contractapi.TransactionContextInterface) error {
	assets := []asset{
		asset{ID: "asset1", Color: "blue", Size: 5, Owner: "Tomoko", AppraisedValue: 300},
		asset{ID: "asset2", Color: "red", Size: 5, Owner: "Brad", AppraisedValue: 400},
		asset{ID: "asset3", Color: "green", Size: 10, Owner: "Jin Soo", AppraisedValue: 500},
		asset{ID: "asset4", Color: "yellow", Size: 10, Owner: "Max", AppraisedValue: 600},
		asset{ID: "asset5", Color: "black", Size: 15, Owner: "Adriana", AppraisedValue: 700},
		asset{ID: "asset6", Color: "white", Size: 15, Owner: "Michel", AppraisedValue: 800},
	}

	for _, asset := range assets {
		assetJSON, err := json.Marshal(asset)
		if err != nil {
			return err
		}

		err = ctx.GetStub().PutState(asset.ID, assetJSON)
		if err != nil {
			return fmt.Errorf("Failed to put to world state. %s", err.Error())
		}
	}

	return nil
}

func main() {

	chaincode, err := contractapi.NewChaincode(new(SimpleChaincode))

	if err != nil {
		fmt.Printf("Error creating asset chaincode: %s", err.Error())
		return
	}

	if err := chaincode.Start(); err != nil {
		fmt.Printf("Error starting asset chaincode: %s", err.Error())
		return
	}
}
