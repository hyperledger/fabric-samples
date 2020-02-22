/*
Copyright IBM Corp. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

// ====CHAINCODE EXECUTION SAMPLES (CLI) ==================

// ==== Invoke marbles, pass private data as base64 encoded bytes in transient map ====
//
// export MARBLE=$(echo -n "{\"name\":\"marble1\",\"color\":\"blue\",\"size\":35,\"owner\":\"tom\",\"price\":99}" | base64 | tr -d \\n)
// peer chaincode invoke -C mychannel -n marblesp -c '{"Args":["initMarble"]}' --transient "{\"marble\":\"$MARBLE\"}"
//
// export MARBLE=$(echo -n "{\"name\":\"marble2\",\"color\":\"red\",\"size\":50,\"owner\":\"tom\",\"price\":102}" | base64 | tr -d \\n)
// peer chaincode invoke -C mychannel -n marblesp -c '{"Args":["initMarble"]}' --transient "{\"marble\":\"$MARBLE\"}"
//
// export MARBLE=$(echo -n "{\"name\":\"marble3\",\"color\":\"blue\",\"size\":70,\"owner\":\"tom\",\"price\":103}" | base64 | tr -d \\n)
// peer chaincode invoke -C mychannel -n marblesp -c '{"Args":["initMarble"]}' --transient "{\"marble\":\"$MARBLE\"}"
//
// export MARBLE_OWNER=$(echo -n "{\"name\":\"marble2\",\"owner\":\"jerry\"}" | base64 | tr -d \\n)
// peer chaincode invoke -C mychannel -n marblesp -c '{"Args":["transferMarble"]}' --transient "{\"marble_owner\":\"$MARBLE_OWNER\"}"
//
// export MARBLE_DELETE=$(echo -n "{\"name\":\"marble1\"}" | base64 | tr -d \\n)
// peer chaincode invoke -C mychannel -n marblesp -c '{"Args":["delete"]}' --transient "{\"marble_delete\":\"$MARBLE_DELETE\"}"

// ==== Query marbles, since queries are not recorded on chain we don't need to hide private data in transient map ====
// peer chaincode query -C mychannel -n marblesp -c '{"Args":["readMarble","marble1"]}'
// peer chaincode query -C mychannel -n marblesp -c '{"Args":["readMarblePrivateDetails","marble1"]}'
// peer chaincode query -C mychannel -n marblesp -c '{"Args":["getMarblesByRange","marble1","marble4"]}'
//
// Rich Query (Only supported if CouchDB is used as state database):
//   peer chaincode query -C mychannel -n marblesp -c '{"Args":["queryMarblesByOwner","tom"]}'
//   peer chaincode query -C mychannel -n marblesp -c '{"Args":["queryMarbles","{\"selector\":{\"owner\":\"tom\"}}"]}'

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
//   peer chaincode query -C mychannel -n marblesp -c '{"Args":["queryMarbles","{\"selector\":{\"docType\":\"marble\",\"owner\":\"tom\"}, \"use_index\":[\"_design/indexOwnerDoc\", \"indexOwner\"]}"]}'

// Rich Query with index design doc specified only (Only supported if CouchDB is used as state database):
//   peer chaincode query -C mychannel -n marblesp -c '{"Args":["queryMarbles","{\"selector\":{\"docType\":{\"$eq\":\"marble\"},\"owner\":{\"$eq\":\"tom\"},\"size\":{\"$gt\":0}},\"fields\":[\"docType\",\"owner\",\"size\"],\"sort\":[{\"size\":\"desc\"}],\"use_index\":\"_design/indexSizeSortDoc\"}"]}'

package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"strings"

	"github.com/hyperledger/fabric-chaincode-go/shim"
	pb "github.com/hyperledger/fabric-protos-go/peer"
)

// SimpleChaincode example simple Chaincode implementation
type SimpleChaincode struct {
}

type marble struct {
	ObjectType string `json:"docType"` //docType is used to distinguish the various types of objects in state database
	Name       string `json:"name"`    //the fieldtags are needed to keep case from bouncing around
	Color      string `json:"color"`
	Size       int    `json:"size"`
	Owner      string `json:"owner"`
}

type marblePrivateDetails struct {
	ObjectType string `json:"docType"` //docType is used to distinguish the various types of objects in state database
	Name       string `json:"name"`    //the fieldtags are needed to keep case from bouncing around
	Price      int    `json:"price"`
}

// ===================================================================================
// Main
// ===================================================================================
func main() {
	err := shim.Start(new(SimpleChaincode))
	if err != nil {
		fmt.Printf("Error starting Simple chaincode: %s", err)
	}
}

// Init initializes chaincode
// ===========================
func (t *SimpleChaincode) Init(stub shim.ChaincodeStubInterface) pb.Response {
	return shim.Success(nil)
}

// Invoke - Our entry point for Invocations
// ========================================
func (t *SimpleChaincode) Invoke(stub shim.ChaincodeStubInterface) pb.Response {
	function, args := stub.GetFunctionAndParameters()
	fmt.Println("invoke is running " + function)

	// Handle different functions
	switch function {
	case "initMarble":
		//create a new marble
		return t.initMarble(stub, args)
	case "readMarble":
		//read a marble
		return t.readMarble(stub, args)
	case "readMarblePrivateDetails":
		//read a marble private details
		return t.readMarblePrivateDetails(stub, args)
	case "transferMarble":
		//change owner of a specific marble
		return t.transferMarble(stub, args)
	case "delete":
		//delete a marble
		return t.delete(stub, args)
	case "queryMarblesByOwner":
		//find marbles for owner X using rich query
		return t.queryMarblesByOwner(stub, args)
	case "queryMarbles":
		//find marbles based on an ad hoc rich query
		return t.queryMarbles(stub, args)
	case "getMarblesByRange":
		//get marbles based on range query
		return t.getMarblesByRange(stub, args)
	default:
		//error
		fmt.Println("invoke did not find func: " + function)
		return shim.Error("Received unknown function invocation")
	}
}

// ============================================================
// initMarble - create a new marble, store into chaincode state
// ============================================================
func (t *SimpleChaincode) initMarble(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	var err error

	type marbleTransientInput struct {
		Name  string `json:"name"` //the fieldtags are needed to keep case from bouncing around
		Color string `json:"color"`
		Size  int    `json:"size"`
		Owner string `json:"owner"`
		Price int    `json:"price"`
	}

	// ==== Input sanitation ====
	fmt.Println("- start init marble")

	if len(args) != 0 {
		return shim.Error("Incorrect number of arguments. Private marble data must be passed in transient map.")
	}

	transMap, err := stub.GetTransient()
	if err != nil {
		return shim.Error("Error getting transient: " + err.Error())
	}

	marbleJsonBytes, ok := transMap["marble"]
	if !ok {
		return shim.Error("marble must be a key in the transient map")
	}

	if len(marbleJsonBytes) == 0 {
		return shim.Error("marble value in the transient map must be a non-empty JSON string")
	}

	var marbleInput marbleTransientInput
	err = json.Unmarshal(marbleJsonBytes, &marbleInput)
	if err != nil {
		return shim.Error("Failed to decode JSON of: " + string(marbleJsonBytes))
	}

	if len(marbleInput.Name) == 0 {
		return shim.Error("name field must be a non-empty string")
	}
	if len(marbleInput.Color) == 0 {
		return shim.Error("color field must be a non-empty string")
	}
	if marbleInput.Size <= 0 {
		return shim.Error("size field must be a positive integer")
	}
	if len(marbleInput.Owner) == 0 {
		return shim.Error("owner field must be a non-empty string")
	}
	if marbleInput.Price <= 0 {
		return shim.Error("price field must be a positive integer")
	}

	// ==== Check if marble already exists ====
	marbleAsBytes, err := stub.GetPrivateData("collectionMarbles", marbleInput.Name)
	if err != nil {
		return shim.Error("Failed to get marble: " + err.Error())
	} else if marbleAsBytes != nil {
		fmt.Println("This marble already exists: " + marbleInput.Name)
		return shim.Error("This marble already exists: " + marbleInput.Name)
	}

	// ==== Create marble object, marshal to JSON, and save to state ====
	marble := &marble{
		ObjectType: "marble",
		Name:       marbleInput.Name,
		Color:      marbleInput.Color,
		Size:       marbleInput.Size,
		Owner:      marbleInput.Owner,
	}
	marbleJSONasBytes, err := json.Marshal(marble)
	if err != nil {
		return shim.Error(err.Error())
	}

	// === Save marble to state ===
	err = stub.PutPrivateData("collectionMarbles", marbleInput.Name, marbleJSONasBytes)
	if err != nil {
		return shim.Error(err.Error())
	}

	// ==== Create marble private details object with price, marshal to JSON, and save to state ====
	marblePrivateDetails := &marblePrivateDetails{
		ObjectType: "marblePrivateDetails",
		Name:       marbleInput.Name,
		Price:      marbleInput.Price,
	}
	marblePrivateDetailsBytes, err := json.Marshal(marblePrivateDetails)
	if err != nil {
		return shim.Error(err.Error())
	}
	err = stub.PutPrivateData("collectionMarblePrivateDetails", marbleInput.Name, marblePrivateDetailsBytes)
	if err != nil {
		return shim.Error(err.Error())
	}

	//  ==== Index the marble to enable color-based range queries, e.g. return all blue marbles ====
	//  An 'index' is a normal key/value entry in state.
	//  The key is a composite key, with the elements that you want to range query on listed first.
	//  In our case, the composite key is based on indexName=color~name.
	//  This will enable very efficient state range queries based on composite keys matching indexName=color~*
	indexName := "color~name"
	colorNameIndexKey, err := stub.CreateCompositeKey(indexName, []string{marble.Color, marble.Name})
	if err != nil {
		return shim.Error(err.Error())
	}
	//  Save index entry to state. Only the key name is needed, no need to store a duplicate copy of the marble.
	//  Note - passing a 'nil' value will effectively delete the key from state, therefore we pass null character as value
	value := []byte{0x00}
	stub.PutPrivateData("collectionMarbles", colorNameIndexKey, value)

	// ==== Marble saved and indexed. Return success ====
	fmt.Println("- end init marble")
	return shim.Success(nil)
}

// ===============================================
// readMarble - read a marble from chaincode state
// ===============================================
func (t *SimpleChaincode) readMarble(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	var name, jsonResp string
	var err error

	if len(args) != 1 {
		return shim.Error("Incorrect number of arguments. Expecting name of the marble to query")
	}

	name = args[0]
	valAsbytes, err := stub.GetPrivateData("collectionMarbles", name) //get the marble from chaincode state
	if err != nil {
		jsonResp = "{\"Error\":\"Failed to get state for " + name + ": " + err.Error() + "\"}"
		return shim.Error(jsonResp)
	} else if valAsbytes == nil {
		jsonResp = "{\"Error\":\"Marble does not exist: " + name + "\"}"
		return shim.Error(jsonResp)
	}

	return shim.Success(valAsbytes)
}

// ===============================================
// readMarblereadMarblePrivateDetails - read a marble private details from chaincode state
// ===============================================
func (t *SimpleChaincode) readMarblePrivateDetails(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	var name, jsonResp string
	var err error

	if len(args) != 1 {
		return shim.Error("Incorrect number of arguments. Expecting name of the marble to query")
	}

	name = args[0]
	valAsbytes, err := stub.GetPrivateData("collectionMarblePrivateDetails", name) //get the marble private details from chaincode state
	if err != nil {
		jsonResp = "{\"Error\":\"Failed to get private details for " + name + ": " + err.Error() + "\"}"
		return shim.Error(jsonResp)
	} else if valAsbytes == nil {
		jsonResp = "{\"Error\":\"Marble private details does not exist: " + name + "\"}"
		return shim.Error(jsonResp)
	}

	return shim.Success(valAsbytes)
}

// ==================================================
// delete - remove a marble key/value pair from state
// ==================================================
func (t *SimpleChaincode) delete(stub shim.ChaincodeStubInterface, args []string) pb.Response {
	fmt.Println("- start delete marble")

	type marbleDeleteTransientInput struct {
		Name string `json:"name"`
	}

	if len(args) != 0 {
		return shim.Error("Incorrect number of arguments. Private marble name must be passed in transient map.")
	}

	transMap, err := stub.GetTransient()
	if err != nil {
		return shim.Error("Error getting transient: " + err.Error())
	}

	marbleDeleteJsonBytes, ok := transMap["marble_delete"]
	if !ok {
		return shim.Error("marble_delete must be a key in the transient map")
	}

	if len(marbleDeleteJsonBytes) == 0 {
		return shim.Error("marble_delete value in the transient map must be a non-empty JSON string")
	}

	var marbleDeleteInput marbleDeleteTransientInput
	err = json.Unmarshal(marbleDeleteJsonBytes, &marbleDeleteInput)
	if err != nil {
		return shim.Error("Failed to decode JSON of: " + string(marbleDeleteJsonBytes))
	}

	if len(marbleDeleteInput.Name) == 0 {
		return shim.Error("name field must be a non-empty string")
	}

	// to maintain the color~name index, we need to read the marble first and get its color
	valAsbytes, err := stub.GetPrivateData("collectionMarbles", marbleDeleteInput.Name) //get the marble from chaincode state
	if err != nil {
		return shim.Error("Failed to get state for " + marbleDeleteInput.Name)
	} else if valAsbytes == nil {
		return shim.Error("Marble does not exist: " + marbleDeleteInput.Name)
	}

	var marbleToDelete marble
	err = json.Unmarshal([]byte(valAsbytes), &marbleToDelete)
	if err != nil {
		return shim.Error("Failed to decode JSON of: " + string(valAsbytes))
	}

	// delete the marble from state
	err = stub.DelPrivateData("collectionMarbles", marbleDeleteInput.Name)
	if err != nil {
		return shim.Error("Failed to delete state:" + err.Error())
	}

	// Also delete the marble from the color~name index
	indexName := "color~name"
	colorNameIndexKey, err := stub.CreateCompositeKey(indexName, []string{marbleToDelete.Color, marbleToDelete.Name})
	if err != nil {
		return shim.Error(err.Error())
	}
	err = stub.DelPrivateData("collectionMarbles", colorNameIndexKey)
	if err != nil {
		return shim.Error("Failed to delete state:" + err.Error())
	}

	// Finally, delete private details of marble
	err = stub.DelPrivateData("collectionMarblePrivateDetails", marbleDeleteInput.Name)
	if err != nil {
		return shim.Error(err.Error())
	}

	return shim.Success(nil)
}

// ===========================================================
// transfer a marble by setting a new owner name on the marble
// ===========================================================
func (t *SimpleChaincode) transferMarble(stub shim.ChaincodeStubInterface, args []string) pb.Response {

	fmt.Println("- start transfer marble")

	type marbleTransferTransientInput struct {
		Name  string `json:"name"`
		Owner string `json:"owner"`
	}

	if len(args) != 0 {
		return shim.Error("Incorrect number of arguments. Private marble data must be passed in transient map.")
	}

	transMap, err := stub.GetTransient()
	if err != nil {
		return shim.Error("Error getting transient: " + err.Error())
	}

	marbleOwnerJsonBytes, ok := transMap["marble_owner"]
	if !ok {
		return shim.Error("marble_owner must be a key in the transient map")
	}

	if len(marbleOwnerJsonBytes) == 0 {
		return shim.Error("marble_owner value in the transient map must be a non-empty JSON string")
	}

	var marbleTransferInput marbleTransferTransientInput
	err = json.Unmarshal(marbleOwnerJsonBytes, &marbleTransferInput)
	if err != nil {
		return shim.Error("Failed to decode JSON of: " + string(marbleOwnerJsonBytes))
	}

	if len(marbleTransferInput.Name) == 0 {
		return shim.Error("name field must be a non-empty string")
	}
	if len(marbleTransferInput.Owner) == 0 {
		return shim.Error("owner field must be a non-empty string")
	}

	marbleAsBytes, err := stub.GetPrivateData("collectionMarbles", marbleTransferInput.Name)
	if err != nil {
		return shim.Error("Failed to get marble:" + err.Error())
	} else if marbleAsBytes == nil {
		return shim.Error("Marble does not exist: " + marbleTransferInput.Name)
	}

	marbleToTransfer := marble{}
	err = json.Unmarshal(marbleAsBytes, &marbleToTransfer) //unmarshal it aka JSON.parse()
	if err != nil {
		return shim.Error(err.Error())
	}
	marbleToTransfer.Owner = marbleTransferInput.Owner //change the owner

	marbleJSONasBytes, _ := json.Marshal(marbleToTransfer)
	err = stub.PutPrivateData("collectionMarbles", marbleToTransfer.Name, marbleJSONasBytes) //rewrite the marble
	if err != nil {
		return shim.Error(err.Error())
	}

	fmt.Println("- end transferMarble (success)")
	return shim.Success(nil)
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
func (t *SimpleChaincode) getMarblesByRange(stub shim.ChaincodeStubInterface, args []string) pb.Response {

	if len(args) < 2 {
		return shim.Error("Incorrect number of arguments. Expecting 2")
	}

	startKey := args[0]
	endKey := args[1]

	resultsIterator, err := stub.GetPrivateDataByRange("collectionMarbles", startKey, endKey)
	if err != nil {
		return shim.Error(err.Error())
	}
	defer resultsIterator.Close()

	// buffer is a JSON array containing QueryResults
	var buffer bytes.Buffer
	buffer.WriteString("[")

	bArrayMemberAlreadyWritten := false
	for resultsIterator.HasNext() {
		queryResponse, err := resultsIterator.Next()
		if err != nil {
			return shim.Error(err.Error())
		}
		// Add a comma before array members, suppress it for the first array member
		if bArrayMemberAlreadyWritten {
			buffer.WriteString(",")
		}

		buffer.WriteString(
			fmt.Sprintf(
				`{"Key":"%s", "Record":%s}`,
				queryResponse.Key, queryResponse.Value,
			),
		)
		bArrayMemberAlreadyWritten = true
	}
	buffer.WriteString("]")

	fmt.Printf("- getMarblesByRange queryResult:\n%s\n", buffer.String())

	return shim.Success(buffer.Bytes())
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
func (t *SimpleChaincode) queryMarblesByOwner(stub shim.ChaincodeStubInterface, args []string) pb.Response {

	//   0
	// "bob"
	if len(args) < 1 {
		return shim.Error("Incorrect number of arguments. Expecting 1")
	}

	owner := strings.ToLower(args[0])

	queryString := fmt.Sprintf("{\"selector\":{\"docType\":\"marble\",\"owner\":\"%s\"}}", owner)

	queryResults, err := getQueryResultForQueryString(stub, queryString)
	if err != nil {
		return shim.Error(err.Error())
	}
	return shim.Success(queryResults)
}

// ===== Example: Ad hoc rich query ========================================================
// queryMarbles uses a query string to perform a query for marbles.
// Query string matching state database syntax is passed in and executed as is.
// Supports ad hoc queries that can be defined at runtime by the client.
// If this is not desired, follow the queryMarblesForOwner example for parameterized queries.
// Only available on state databases that support rich query (e.g. CouchDB)
// =========================================================================================
func (t *SimpleChaincode) queryMarbles(stub shim.ChaincodeStubInterface, args []string) pb.Response {

	//   0
	// "queryString"
	if len(args) < 1 {
		return shim.Error("Incorrect number of arguments. Expecting 1")
	}

	queryString := args[0]

	queryResults, err := getQueryResultForQueryString(stub, queryString)
	if err != nil {
		return shim.Error(err.Error())
	}
	return shim.Success(queryResults)
}

// =========================================================================================
// getQueryResultForQueryString executes the passed in query string.
// Result set is built and returned as a byte array containing the JSON results.
// =========================================================================================
func getQueryResultForQueryString(stub shim.ChaincodeStubInterface, queryString string) ([]byte, error) {

	fmt.Printf("- getQueryResultForQueryString queryString:\n%s\n", queryString)

	resultsIterator, err := stub.GetPrivateDataQueryResult("collectionMarbles", queryString)
	if err != nil {
		return nil, err
	}
	defer resultsIterator.Close()

	// buffer is a JSON array containing QueryRecords
	var buffer bytes.Buffer
	buffer.WriteString("[")

	bArrayMemberAlreadyWritten := false
	for resultsIterator.HasNext() {
		queryResponse, err := resultsIterator.Next()
		if err != nil {
			return nil, err
		}
		// Add a comma before array members, suppress it for the first array member
		if bArrayMemberAlreadyWritten == true {
			buffer.WriteString(",")
		}
		buffer.WriteString("{\"Key\":")
		buffer.WriteString("\"")
		buffer.WriteString(queryResponse.Key)
		buffer.WriteString("\"")

		buffer.WriteString(", \"Record\":")
		// Record is a JSON object, so we write as-is
		buffer.WriteString(string(queryResponse.Value))
		buffer.WriteString("}")
		bArrayMemberAlreadyWritten = true
	}
	buffer.WriteString("]")

	fmt.Printf("- getQueryResultForQueryString queryResult:\n%s\n", buffer.String())

	return buffer.Bytes(), nil
}
