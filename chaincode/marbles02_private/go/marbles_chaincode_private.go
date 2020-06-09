/*
Copyright IBM Corp. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

// ====CHAINCODE EXECUTION SAMPLES (CLI) ==================

// ==== Invoke marbles, pass private data as base64 encoded bytes in transient map ====
//
// export MARBLE=$(echo -n "{\"name\":\"marble1\",\"color\":\"blue\",\"size\":35,\"owner\":\"tom\",\"price\":99}" | base64 | tr -d \\n)
// peer chaincode invoke -C mychannel -n marblesp -c '{"Args":["InitMarble"]}' --transient "{\"marble\":\"$MARBLE\"}"
//
// export MARBLE=$(echo -n "{\"name\":\"marble2\",\"color\":\"red\",\"size\":50,\"owner\":\"tom\",\"price\":102}" | base64 | tr -d \\n)
// peer chaincode invoke -C mychannel -n marblesp -c '{"Args":["InitMarble"]}' --transient "{\"marble\":\"$MARBLE\"}"
//
// export MARBLE=$(echo -n "{\"name\":\"marble3\",\"color\":\"blue\",\"size\":70,\"owner\":\"tom\",\"price\":103}" | base64 | tr -d \\n)
// peer chaincode invoke -C mychannel -n marblesp -c '{"Args":["InitMarble"]}' --transient "{\"marble\":\"$MARBLE\"}"
//
// export MARBLE_OWNER=$(echo -n "{\"name\":\"marble2\",\"owner\":\"jerry\"}" | base64 | tr -d \\n)
// peer chaincode invoke -C mychannel -n marblesp -c '{"Args":["TransferMarble"]}' --transient "{\"marble_owner\":\"$MARBLE_OWNER\"}"
//
// export MARBLE_DELETE=$(echo -n "{\"name\":\"marble1\"}" | base64 | tr -d \\n)
// peer chaincode invoke -C mychannel -n marblesp -c '{"Args":["Delete"]}' --transient "{\"marble_delete\":\"$MARBLE_DELETE\"}"

// ==== Query marbles, since queries are not recorded on chain we don't need to hide private data in transient map ====
// peer chaincode query -C mychannel -n marblesp -c '{"Args":["ReadMarble","marble1"]}'
// peer chaincode query -C mychannel -n marblesp -c '{"Args":["ReadMarblePrivateDetails","marble1"]}'
// peer chaincode query -C mychannel -n marblesp -c '{"Args":["GetMarblesByRange","marble1","marble4"]}'

// Query a marble's public data hash
//	peer chaincode query -C mychannel -n marblesp -c '{"Args":["GetMarbleHash","collectionMarbles","marble1"]}'

// Rich Query (Only supported if CouchDB is used as state database):
//   peer chaincode query -C mychannel -n marblesp -c '{"Args":["QueryMarblesByOwner","tom"]}'
//   peer chaincode query -C mychannel -n marblesp -c '{"Args":["QueryMarbles","{\"selector\":{\"owner\":\"tom\"}}"]}'

// INDEXES TO SUPPORT COUCHDB RICH QUERIES
//
// Indexes in CouchDB are required in order to make JSON queries efficient and are required for
// any JSON query with a sort. As of Hyperledger Fabric 1.1, indexes may be packaged alongside
// chaincode in a META-INF/statedb/couchdb/indexes directory. Or for indexes on private data
// collections, in a META-INF/statedb/couchdb/collections/<collection_name>/indexes directory.
// Each index must be defined in its own text file with extension *.json with the index
// definition formatted in JSON following the CouchDB index JSON syntax as documented at:
// http://docs.couchdb.org/en/2.1.1/api/database/find.html#db-index
//
// This marbles02_private example chaincode demonstrates a packaged index which you
// can find in META-INF/statedb/couchdb/collection/collectionMarbles/indexes/indexOwner.json.
// For deployment of chaincode to production environments, it is recommended
// to define any indexes alongside chaincode so that the chaincode and supporting indexes
// are deployed automatically as a unit, once the chaincode has been installed on a peer and
// instantiated on a channel. See Hyperledger Fabric documentation for more details.
//
// If you have access to the your peer's CouchDB state database in a development environment,
// you may want to iteratively test various indexes in support of your chaincode queries.  You
// can use the CouchDB Fauxton interface or a command line curl utility to create and update
// indexes. Then once you finalize an index, include the index definition alongside your
// chaincode in the META-INF/statedb/couchdb/indexes directory or
// META-INF/statedb/couchdb/collections/<collection_name>/indexes directory, for packaging
// and deployment to managed environments.
//
// In the examples below you can find index definitions that support marbles02_private
// chaincode queries, along with the syntax that you can use in development environments
// to create the indexes in the CouchDB Fauxton interface.
//

//Example hostname:port configurations to access CouchDB.
//
//To access CouchDB docker container from within another docker container or from vagrant environments:
// http://couchdb:5984/
//
//Inside couchdb docker container
// http://127.0.0.1:5984/

// Index for docType, owner.
// Note that docType and owner fields must be prefixed with the "data" wrapper
//
// Index definition for use with Fauxton interface
// {"index":{"fields":["data.docType","data.owner"]},"ddoc":"indexOwnerDoc", "name":"indexOwner","type":"json"}

// Index for docType, owner, size (descending order).
// Note that docType, owner and size fields must be prefixed with the "data" wrapper
//
// Index definition for use with Fauxton interface
// {"index":{"fields":[{"data.size":"desc"},{"data.docType":"desc"},{"data.owner":"desc"}]},"ddoc":"indexSizeSortDoc", "name":"indexSizeSortDesc","type":"json"}

// Rich Query with index design doc and index name specified (Only supported if CouchDB is used as state database):
//   peer chaincode query -C mychannel -n marblesp -c '{"Args":["QueryMarbles","{\"selector\":{\"docType\":\"marble\",\"owner\":\"tom\"}, \"use_index\":[\"_design/indexOwnerDoc\", \"indexOwner\"]}"]}'

// Rich Query with index design doc specified only (Only supported if CouchDB is used as state database):
//   peer chaincode query -C mychannel -n marblesp -c '{"Args":["QueryMarbles","{\"selector\":{\"docType\":{\"$eq\":\"marble\"},\"owner\":{\"$eq\":\"tom\"},\"size\":{\"$gt\":0}},\"fields\":[\"docType\",\"owner\",\"size\"],\"sort\":[{\"size\":\"desc\"}],\"use_index\":\"_design/indexSizeSortDoc\"}"]}'

package main

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

type Marble struct {
	ObjectType string `json:"docType"` //docType is used to distinguish the various types of objects in state database
	Name       string `json:"name"`    //the fieldtags are needed to keep case from bouncing around
	Color      string `json:"color"`
	Size       int    `json:"size"`
	Owner      string `json:"owner"`
}

type MarblePrivateDetails struct {
	ObjectType string `json:"docType"` //docType is used to distinguish the various types of objects in state database
	Name       string `json:"name"`    //the fieldtags are needed to keep case from bouncing around
	Price      int    `json:"price"`
}

type SmartContract struct {
	contractapi.Contract
}


// ============================================================
// initMarble - create a new marble, store into chaincode state
// ============================================================
func (s *SmartContract) InitMarble(ctx contractapi.TransactionContextInterface) error {

	transMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return fmt.Errorf("Error getting transient: " + err.Error())
	}

	// Marble properties are private, therefore they get passed in transient field
	transientMarbleJSON, ok := transMap["marble"]
	if !ok {
		return fmt.Errorf("marble not found in the transient map")
	}

	type marbleTransientInput struct {
		Name  string `json:"name"` //the fieldtags are needed to keep case from bouncing around
		Color string `json:"color"`
		Size  int    `json:"size"`
		Owner string `json:"owner"`
		Price int    `json:"price"`
	}

	var marbleInput marbleTransientInput
	err = json.Unmarshal(transientMarbleJSON, &marbleInput)
	if err != nil {
		return fmt.Errorf("failed to unmarshal JSON: %s", err.Error())
	}

	if len(marbleInput.Name) == 0 {
		return fmt.Errorf("name field must be a non-empty string")
	}
	if len(marbleInput.Color) == 0 {
		return fmt.Errorf("color field must be a non-empty string")
	}
	if marbleInput.Size <= 0 {
		return fmt.Errorf("size field must be a positive integer")
	}
	if len(marbleInput.Owner) == 0 {
		return fmt.Errorf("owner field must be a non-empty string")
	}
	if marbleInput.Price <= 0 {
		return fmt.Errorf("price field must be a positive integer")
	}

	// ==== Check if marble already exists ====
	marbleAsBytes, err := ctx.GetStub().GetPrivateData("collectionMarbles", marbleInput.Name)
	if err != nil {
		return fmt.Errorf("Failed to get marble: " + err.Error())
	} else if marbleAsBytes != nil {
		fmt.Println("This marble already exists: " + marbleInput.Name)
		return fmt.Errorf("This marble already exists: " + marbleInput.Name)
	}

	// ==== Create marble object, marshal to JSON, and save to state ====
	marble := &Marble{
		ObjectType: "Marble",
		Name:       marbleInput.Name,
		Color:      marbleInput.Color,
		Size:       marbleInput.Size,
		Owner:      marbleInput.Owner,
	}
	marbleJSONasBytes, err := json.Marshal(marble)
	if err != nil {
		return fmt.Errorf(err.Error())
	}

	// === Save marble to state ===
	err = ctx.GetStub().PutPrivateData("collectionMarbles", marbleInput.Name, marbleJSONasBytes)
	if err != nil {
		return fmt.Errorf("failed to put Marble: %s", err.Error())
	}

	// ==== Create marble private details object with price, marshal to JSON, and save to state ====
	marblePrivateDetails := &MarblePrivateDetails{
		ObjectType: "MarblePrivateDetails",
		Name:       marbleInput.Name,
		Price:      marbleInput.Price,
	}
	marblePrivateDetailsAsBytes, err := json.Marshal(marblePrivateDetails)
	if err != nil {
		return fmt.Errorf(err.Error())
	}
	err = ctx.GetStub().PutPrivateData("collectionMarblePrivateDetails", marbleInput.Name, marblePrivateDetailsAsBytes)
	if err != nil {
		return fmt.Errorf("failed to put Marble private details: %s", err.Error())
	}

	//  ==== Index the marble to enable color-based range queries, e.g. return all blue marbles ====
	//  An 'index' is a normal key/value entry in state.
	//  The key is a composite key, with the elements that you want to range query on listed first.
	//  In our case, the composite key is based on indexName=color~name.
	//  This will enable very efficient state range queries based on composite keys matching indexName=color~*
	indexName := "color~name"
	colorNameIndexKey, err := ctx.GetStub().CreateCompositeKey(indexName, []string{marble.Color, marble.Name})
	if err != nil {
			return err
	}
	//  Save index entry to state. Only the key name is needed, no need to store a duplicate copy of the marble.
	//  Note - passing a 'nil' value will effectively delete the key from state, therefore we pass null character as value
	value := []byte{0x00}
	err = ctx.GetStub().PutPrivateData("collectionMarbles", colorNameIndexKey, value)

	// ==== Marble saved and indexed. Return success ====

	return nil

}

// ===============================================
// readMarble - read a marble from chaincode state
// ===============================================

func (s *SmartContract) ReadMarble(ctx contractapi.TransactionContextInterface, marbleID string) (*Marble, error) {

	marbleJSON, err := ctx.GetStub().GetPrivateData("collectionMarbles", marbleID) //get the marble from chaincode state
	if err != nil {
			return nil, fmt.Errorf("failed to read from marble %s", err.Error())
		}
		if marbleJSON == nil {
			return nil, fmt.Errorf("%s does not exist", marbleID)
		}

		marble := new(Marble)
	_ = json.Unmarshal(marbleJSON, marble)

	return marble, nil

}

// ===============================================
// ReadMarblePrivateDetails - read a marble private details from chaincode state
// ===============================================
func (s *SmartContract) ReadMarblePrivateDetails(ctx contractapi.TransactionContextInterface, marbleID string) (*MarblePrivateDetails, error) {

	marbleDetailsJSON, err := ctx.GetStub().GetPrivateData("collectionMarblePrivateDetails", marbleID) //get the marble from chaincode state
		if err != nil {
			return nil, fmt.Errorf("failed to read from marble details %s", err.Error())
		}
		if marbleDetailsJSON == nil {
			return nil, fmt.Errorf("%s does not exist", marbleID)
		}

		marbleDetails := new(MarblePrivateDetails)
	_ = json.Unmarshal(marbleDetailsJSON, marbleDetails)

	return marbleDetails, nil
}

// ==================================================
// delete - remove a marble key/value pair from state
// ==================================================
func (s *SmartContract) Delete(ctx contractapi.TransactionContextInterface) error {

	transMap, err := ctx.GetStub().GetTransient()
		if err != nil {
			return fmt.Errorf("Error getting transient: " + err.Error())
		}

	// Marble properties are private, therefore they get passed in transient field
	transientDeleteMarbleJSON, ok := transMap["marble_delete"]
		if !ok {
			return fmt.Errorf("marble to delete not found in the transient map")
		}

	type marbleDelete struct {
			Name string `json:"name"`
		}

	var marbleDeleteInput marbleDelete
	err = json.Unmarshal(transientDeleteMarbleJSON, &marbleDeleteInput)
		if err != nil {
			return fmt.Errorf("failed to unmarshal JSON: %s", err.Error())
		}

	if len(marbleDeleteInput.Name) == 0 {
		return fmt.Errorf("name field must be a non-empty string")
	}

	// to maintain the color~name index, we need to read the marble first and get its color
	valAsbytes, err := ctx.GetStub().GetPrivateData("collectionMarbles", marbleDeleteInput.Name) //get the marble from chaincode state
		if err != nil {
			return fmt.Errorf("failed to read marble: %s", err.Error())
		}
		if valAsbytes == nil {
			return fmt.Errorf("marble private details does not exist: %s",  marbleDeleteInput.Name)
		}

	var marbleToDelete Marble
	err = json.Unmarshal([]byte(valAsbytes), &marbleToDelete)
	if err != nil {
		return fmt.Errorf("failed to unmarshal JSON: %s", err.Error())
	}

	// delete the marble from state
	err = ctx.GetStub().DelPrivateData("collectionMarbles", marbleDeleteInput.Name)
	if err != nil {
		return fmt.Errorf("Failed to delete state:" + err.Error())
	}

	// Also delete the marble from the color~name index
	indexName := "color~name"
	colorNameIndexKey, err := ctx.GetStub().CreateCompositeKey(indexName, []string{marbleToDelete.Color, marbleToDelete.Name})
	if err != nil {
			return err
	}
	err = ctx.GetStub().DelPrivateData("collectionMarbles", colorNameIndexKey)
	if err != nil {
		return fmt.Errorf("Failed to delete marble:" + err.Error())
	}

	// Finally, delete private details of marble
	err = ctx.GetStub().DelPrivateData("collectionMarblePrivateDetails", marbleDeleteInput.Name)
	if err != nil {
			return err
	}

	return nil

}

// ===========================================================
// transfer a marble by setting a new owner name on the marble
// ===========================================================
func (s *SmartContract) TransferMarble(ctx contractapi.TransactionContextInterface) error {

	transMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return fmt.Errorf("Error getting transient: " + err.Error())
	}

	// Marble properties are private, therefore they get passed in transient field
	transientTransferMarbleJSON, ok := transMap["marble_owner"]
	if !ok {
		return fmt.Errorf("marble owner not found in the transient map")
	}

	type marbleTransferTransientInput struct {
		Name  string `json:"name"`
		Owner string `json:"owner"`
	}

	var marbleTransferInput marbleTransferTransientInput
	err = json.Unmarshal(transientTransferMarbleJSON, &marbleTransferInput)
	if err != nil {
		return fmt.Errorf("failed to unmarshal JSON: %s", err.Error())
	}


	if len(marbleTransferInput.Name) == 0 {
		return fmt.Errorf("name field must be a non-empty string")
	}
	if len(marbleTransferInput.Owner) == 0 {
		return fmt.Errorf("owner field must be a non-empty string")
	}

	marbleAsBytes, err := ctx.GetStub().GetPrivateData("collectionMarbles", marbleTransferInput.Name)
		if err != nil {
			return fmt.Errorf("Failed to get marble:" + err.Error())
		} else if marbleAsBytes == nil {
			return fmt.Errorf("Marble does not exist: " + marbleTransferInput.Name)
		}

	marbleToTransfer := Marble{}
	err = json.Unmarshal(marbleAsBytes, &marbleToTransfer) //unmarshal it aka JSON.parse()
		if err != nil {
			return fmt.Errorf("failed to unmarshal JSON: %s", err.Error())
		}

	marbleToTransfer.Owner = marbleTransferInput.Owner //change the owner

	marbleJSONasBytes, _ := json.Marshal(marbleToTransfer)
	err = ctx.GetStub().PutPrivateData("collectionMarbles", marbleToTransfer.Name, marbleJSONasBytes) //rewrite the marble
		if err != nil {
				return err
		}

	return nil

}

// ===========================================================================================
// getMarblesByRange performs a range query based on the start and end keys provided.

// Read-only function results are not typically submitted to ordering. If the read-only
// results are submitted to ordering, or if the query is used in an update transaction
// and submitted to ordering, then the committing peers will re-execute to guarantee that
// result sets are stable between endorsement time and commit time. The transaction is
// invalidated by the committing peers if the result set has changed between endorsement
// time and commit time.
// Therefore, range queries are a safe option for performing update transactions based on query results.
// ===========================================================================================
func (s *SmartContract) GetMarblesByRange(ctx contractapi.TransactionContextInterface, startKey string, endKey string) ([]Marble, error) {

	resultsIterator, err := ctx.GetStub().GetPrivateDataByRange("collectionMarbles", startKey, endKey)
	if err != nil {
			return nil, err
	}
	defer resultsIterator.Close()

	results := []Marble{}

	for resultsIterator.HasNext() {
		response, err := resultsIterator.Next()
		if err != nil {
			return nil, err
		}

		newMarble := new(Marble)

		err = json.Unmarshal(response.Value, newMarble)
		if err != nil {
				return nil, err
		}

		results = append(results, *newMarble)
	}

	return results, nil

}


// =======Rich queries =========================================================================
// Two examples of rich queries are provided below (parameterized query and ad hoc query).
// Rich queries pass a query string to the state database.
// Rich queries are only supported by state database implementations
//  that support rich query (e.g. CouchDB).
// The query string is in the syntax of the underlying state database.
// With rich queries there is no guarantee that the result set hasn't changed between
//  endorsement time and commit time, aka 'phantom reads'.
// Therefore, rich queries should not be used in update transactions, unless the
// application handles the possibility of result set changes between endorsement and commit time.
// Rich queries can be used for point-in-time queries against a peer.
// ============================================================================================

// ===== Example: Parameterized rich query =================================================
// queryMarblesByOwner queries for marbles based on a passed in owner.
// This is an example of a parameterized query where the query logic is baked into the chaincode,
// and accepting a single query parameter (owner).
// Only available on state databases that support rich query (e.g. CouchDB)
// =========================================================================================
func (s *SmartContract) QueryMarblesByOwner(ctx contractapi.TransactionContextInterface, owner string) ([]Marble, error) {

	ownerString  := strings.ToLower(owner)

	queryString := fmt.Sprintf("{\"selector\":{\"docType\":\"marble\",\"owner\":\"%s\"}}", ownerString)

	queryResults, err := s.getQueryResultForQueryString(ctx, queryString)
	if err != nil {
			return nil, err
	}
	return queryResults, nil
}

// ===== Example: Ad hoc rich query ========================================================
// queryMarbles uses a query string to perform a query for marbles.
// Query string matching state database syntax is passed in and executed as is.
// Supports ad hoc queries that can be defined at runtime by the client.
// If this is not desired, follow the queryMarblesForOwner example for parameterized queries.
// Only available on state databases that support rich query (e.g. CouchDB)
// =========================================================================================
func (s *SmartContract) QueryMarbles(ctx contractapi.TransactionContextInterface, queryString string) ([]Marble, error) {

	queryResults, err := s.getQueryResultForQueryString(ctx, queryString)
	if err != nil {
			return nil, err
	}
	return queryResults, nil
}

// =========================================================================================
// getQueryResultForQueryString executes the passed in query string.
// Result set is built and returned as a byte array containing the JSON results.
// =========================================================================================
func (s *SmartContract) getQueryResultForQueryString(ctx contractapi.TransactionContextInterface, queryString string) ([]Marble, error) {

	resultsIterator, err := ctx.GetStub().GetPrivateDataQueryResult("collectionMarbles", queryString)
	if err != nil {
		return nil, err
	}
	defer resultsIterator.Close()

	results := []Marble{}

	for resultsIterator.HasNext() {
		response, err := resultsIterator.Next()
		if err != nil {
			return nil, err
		}

		newMarble := new(Marble)

		err = json.Unmarshal(response.Value, newMarble)
		if err != nil {
				return nil, err
		}

		results = append(results, *newMarble)
	}
	return results, nil
}

// ===============================================
// getMarbleHash - use the public data hash to verify a private marble
// Result is the hash on the public ledger of a marble stored a private data collection
// ===============================================
func (s *SmartContract) GetMarbleHash(ctx contractapi.TransactionContextInterface, collection string, marbleID string,) (string, error) {

	// GetPrivateDataHash can use any collection deployed with the chaincode as input
	hashAsbytes, err := ctx.GetStub().GetPrivateDataHash(collection, marbleID)
	if err != nil {
		return "", fmt.Errorf("Failed to get public data hash for marble:" + err.Error())
	} else if hashAsbytes == nil {
		return "", fmt.Errorf("Marble does not exist: " + marbleID)
	}

	return string(hashAsbytes), nil
}

func main() {

	chaincode, err := contractapi.NewChaincode(new(SmartContract))

	if err != nil {
		fmt.Printf("Error creating private mables chaincode: %s", err.Error())
		return
	}

	if err := chaincode.Start(); err != nil {
		fmt.Printf("Error starting private mables chaincode: %s", err.Error())
	}
}
