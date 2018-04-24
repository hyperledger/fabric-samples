/*
 * Copyright IBM Corp All Rights Reserved
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Demonstrates how to handle data in an application with a high transaction volume where the transactions
 * all attempt to change the same key-value pair in the ledger. Such an application will have trouble
 * as multiple transactions may read a value at a certain version, which will then be invalid when the first
 * transaction updates the value to a new version, thus rejecting all other transactions until they're
 * re-executed.
 * Rather than relying on serialization of the transactions, which is slow, this application initializes
 * a value and then accepts deltas of that value which are added as rows to the ledger. The actual value
 * is then an aggregate of the initial value combined with all of the deltas. Additionally, a pruning
 * function is provided which aggregates and deletes the deltas to update the initial value. This should
 * be done during a maintenance window or when there is a lowered transaction volume, to avoid the proliferation
 * of millions of rows of data.
 *
 * @author	Alexandre Pauwels for IBM
 * @created	17 Aug 2017
 */

package main

/* Imports
 * 4 utility libraries for formatting, handling bytes, reading and writing JSON, and string manipulation
 * 2 specific Hyperledger Fabric specific libraries for Smart Contracts
 */
import (
	"fmt"
	"strconv"

	"github.com/hyperledger/fabric/core/chaincode/shim"
	sc "github.com/hyperledger/fabric/protos/peer"
)

//SmartContract is the data structure which represents this contract and on which  various contract lifecycle functions are attached
type SmartContract struct {
}

// Define Status codes for the response
const (
	OK    = 200
	ERROR = 500
)

// Init is called when the smart contract is instantiated
func (s *SmartContract) Init(APIstub shim.ChaincodeStubInterface) sc.Response {
	return shim.Success(nil)
}

// Invoke routes invocations to the appropriate function in chaincode
// Current supported invocations are:
//	- update, adds a delta to an aggregate variable in the ledger, all variables are assumed to start at 0
//	- get, retrieves the aggregate value of a variable in the ledger
//	- pruneFast, deletes all rows associated with the variable and replaces them with a single row containing the aggregate value
//	- pruneSafe, same as pruneFast except it pre-computed the value and backs it up before performing any destructive operations
//	- delete, removes all rows associated with the variable
func (s *SmartContract) Invoke(APIstub shim.ChaincodeStubInterface) sc.Response {
	// Retrieve the requested Smart Contract function and arguments
	function, args := APIstub.GetFunctionAndParameters()

	// Route to the appropriate handler function to interact with the ledger appropriately
	if function == "update" {
		return s.update(APIstub, args)
	} else if function == "get" {
		return s.get(APIstub, args)
	} else if function == "prunefast" {
		return s.pruneFast(APIstub, args)
	} else if function == "prunesafe" {
		return s.pruneSafe(APIstub, args)
	} else if function == "delete" {
		return s.delete(APIstub, args)
	} else if function == "putstandard" {
		return s.putStandard(APIstub, args)
	} else if function == "getstandard" {
		return s.getStandard(APIstub, args)
	}

	return shim.Error("Invalid Smart Contract function name.")
}

/**
 * Updates the ledger to include a new delta for a particular variable. If this is the first time
 * this variable is being added to the ledger, then its initial value is assumed to be 0. The arguments
 * to give in the args array are as follows:
 *	- args[0] -> name of the variable
 *	- args[1] -> new delta (float)
 *	- args[2] -> operation (currently supported are addition "+" and subtraction "-")
 *
 * @param APIstub The chaincode shim
 * @param args The arguments array for the update invocation
 *
 * @return A response structure indicating success or failure with a message
 */
func (s *SmartContract) update(APIstub shim.ChaincodeStubInterface, args []string) sc.Response {
	// Check we have a valid number of args
	if len(args) != 3 {
		return shim.Error("Incorrect number of arguments, expecting 3")
	}

	// Extract the args
	name := args[0]
	op := args[2]
	_, err := strconv.ParseFloat(args[1], 64)
	if err != nil {
		return shim.Error("Provided value was not a number")
	}

	// Make sure a valid operator is provided
	if op != "+" && op != "-" {
		return shim.Error(fmt.Sprintf("Operator %s is unrecognized", op))
	}

	// Retrieve info needed for the update procedure
	txid := APIstub.GetTxID()
	compositeIndexName := "varName~op~value~txID"

	// Create the composite key that will allow us to query for all deltas on a particular variable
	compositeKey, compositeErr := APIstub.CreateCompositeKey(compositeIndexName, []string{name, op, args[1], txid})
	if compositeErr != nil {
		return shim.Error(fmt.Sprintf("Could not create a composite key for %s: %s", name, compositeErr.Error()))
	}

	// Save the composite key index
	compositePutErr := APIstub.PutState(compositeKey, []byte{0x00})
	if compositePutErr != nil {
		return shim.Error(fmt.Sprintf("Could not put operation for %s in the ledger: %s", name, compositePutErr.Error()))
	}

	return shim.Success([]byte(fmt.Sprintf("Successfully added %s%s to %s", op, args[1], name)))
}

/**
 * Retrieves the aggregate value of a variable in the ledger. Gets all delta rows for the variable
 * and computes the final value from all deltas. The args array for the invocation must contain the
 * following argument:
 *	- args[0] -> The name of the variable to get the value of
 *
 * @param APIstub The chaincode shim
 * @param args The arguments array for the get invocation
 *
 * @return A response structure indicating success or failure with a message
 */
func (s *SmartContract) get(APIstub shim.ChaincodeStubInterface, args []string) sc.Response {
	// Check we have a valid number of args
	if len(args) != 1 {
		return shim.Error("Incorrect number of arguments, expecting 1")
	}

	name := args[0]
	// Get all deltas for the variable
	deltaResultsIterator, deltaErr := APIstub.GetStateByPartialCompositeKey("varName~op~value~txID", []string{name})
	if deltaErr != nil {
		return shim.Error(fmt.Sprintf("Could not retrieve value for %s: %s", name, deltaErr.Error()))
	}
	defer deltaResultsIterator.Close()

	// Check the variable existed
	if !deltaResultsIterator.HasNext() {
		return shim.Error(fmt.Sprintf("No variable by the name %s exists", name))
	}

	// Iterate through result set and compute final value
	var finalVal float64
	var i int
	for i = 0; deltaResultsIterator.HasNext(); i++ {
		// Get the next row
		responseRange, nextErr := deltaResultsIterator.Next()
		if nextErr != nil {
			return shim.Error(nextErr.Error())
		}

		// Split the composite key into its component parts
		_, keyParts, splitKeyErr := APIstub.SplitCompositeKey(responseRange.Key)
		if splitKeyErr != nil {
			return shim.Error(splitKeyErr.Error())
		}

		// Retrieve the delta value and operation
		operation := keyParts[1]
		valueStr := keyParts[2]

		// Convert the value string and perform the operation
		value, convErr := strconv.ParseFloat(valueStr, 64)
		if convErr != nil {
			return shim.Error(convErr.Error())
		}

		switch operation {
		case "+":
			finalVal += value
		case "-":
			finalVal -= value
		default:
			return shim.Error(fmt.Sprintf("Unrecognized operation %s", operation))
		}
	}

	return shim.Success([]byte(strconv.FormatFloat(finalVal, 'f', -1, 64)))
}

/**
 * Prunes a variable by deleting all of its delta rows while computing the final value. Once all rows
 * have been processed and deleted, a single new row is added which defines a delta containing the final
 * computed value of the variable. This function is NOT safe as any failures or errors during pruning
 * will result in an undefined final value for the variable and loss of data. Use pruneSafe if data
 * integrity is important. The args array contains the following argument:
 *	- args[0] -> The name of the variable to prune
 *
 * @param APIstub The chaincode shim
 * @param args The args array for the pruneFast invocation
 *
 * @return A response structure indicating success or failure with a message
 */
func (s *SmartContract) pruneFast(APIstub shim.ChaincodeStubInterface, args []string) sc.Response {
	// Check we have a valid number of ars
	if len(args) != 1 {
		return shim.Error("Incorrect number of arguments, expecting 1")
	}

	// Retrieve the name of the variable to prune
	name := args[0]

	// Get all delta rows for the variable
	deltaResultsIterator, deltaErr := APIstub.GetStateByPartialCompositeKey("varName~op~value~txID", []string{name})
	if deltaErr != nil {
		return shim.Error(fmt.Sprintf("Could not retrieve value for %s: %s", name, deltaErr.Error()))
	}
	defer deltaResultsIterator.Close()

	// Check the variable existed
	if !deltaResultsIterator.HasNext() {
		return shim.Error(fmt.Sprintf("No variable by the name %s exists", name))
	}

	// Iterate through result set computing final value while iterating and deleting each key
	var finalVal float64
	var i int
	for i = 0; deltaResultsIterator.HasNext(); i++ {
		// Get the next row
		responseRange, nextErr := deltaResultsIterator.Next()
		if nextErr != nil {
			return shim.Error(nextErr.Error())
		}

		// Split the key into its composite parts
		_, keyParts, splitKeyErr := APIstub.SplitCompositeKey(responseRange.Key)
		if splitKeyErr != nil {
			return shim.Error(splitKeyErr.Error())
		}

		// Retrieve the operation and value
		operation := keyParts[1]
		valueStr := keyParts[2]

		// Convert the value to a float
		value, convErr := strconv.ParseFloat(valueStr, 64)
		if convErr != nil {
			return shim.Error(convErr.Error())
		}

		// Delete the row from the ledger
		deltaRowDelErr := APIstub.DelState(responseRange.Key)
		if deltaRowDelErr != nil {
			return shim.Error(fmt.Sprintf("Could not delete delta row: %s", deltaRowDelErr.Error()))
		}

		// Add the value of the deleted row to the final aggregate
		switch operation {
		case "+":
			finalVal += value
		case "-":
			finalVal -= value
		default:
			return shim.Error(fmt.Sprintf("Unrecognized operation %s", operation))
		}
	}

	// Update the ledger with the final value and return
	updateResp := s.update(APIstub, []string{name, strconv.FormatFloat(finalVal, 'f', -1, 64), "+"})
	if updateResp.Status == OK {
		return shim.Success([]byte(fmt.Sprintf("Successfully pruned variable %s, final value is %f, %d rows pruned", args[0], finalVal, i)))
	}

	return shim.Error(fmt.Sprintf("Failed to prune variable: all rows deleted but could not update value to %f, variable no longer exists in ledger", finalVal))
}

/**
 * This function performs the same function as pruneFast except it provides data backups in case the
 * prune fails. The final aggregate value is computed before any deletion occurs and is backed up
 * to a new row. This back-up row is deleted only after the new aggregate delta has been successfully
 * written to the ledger. The args array contains the following argument:
 *	args[0] -> The name of the variable to prune
 *
 * @param APIstub The chaincode shim
 * @param args The arguments array for the pruneSafe invocation
 *
 * @result A response structure indicating success or failure with a message
 */
func (s *SmartContract) pruneSafe(APIstub shim.ChaincodeStubInterface, args []string) sc.Response {
	// Verify there are a correct number of arguments
	if len(args) != 1 {
		return shim.Error("Incorrect number of arguments, expecting 1 (the name of the variable to prune)")
	}

	// Get the var name
	name := args[0]

	// Get the var's value and process it
	getResp := s.get(APIstub, args)
	if getResp.Status == ERROR {
		return shim.Error(fmt.Sprintf("Could not retrieve the value of %s before pruning, pruning aborted: %s", name, getResp.Message))
	}

	valueStr := string(getResp.Payload)
	val, convErr := strconv.ParseFloat(valueStr, 64)
	if convErr != nil {
		return shim.Error(fmt.Sprintf("Could not convert the value of %s to a number before pruning, pruning aborted: %s", name, convErr.Error()))
	}

	// Store the var's value temporarily
	backupPutErr := APIstub.PutState(fmt.Sprintf("%s_PRUNE_BACKUP", name), []byte(valueStr))
	if backupPutErr != nil {
		return shim.Error(fmt.Sprintf("Could not backup the value of %s before pruning, pruning aborted: %s", name, backupPutErr.Error()))
	}

	// Get all deltas for the variable
	deltaResultsIterator, deltaErr := APIstub.GetStateByPartialCompositeKey("varName~op~value~txID", []string{name})
	if deltaErr != nil {
		return shim.Error(fmt.Sprintf("Could not retrieve value for %s: %s", name, deltaErr.Error()))
	}
	defer deltaResultsIterator.Close()

	// Delete each row
	var i int
	for i = 0; deltaResultsIterator.HasNext(); i++ {
		responseRange, nextErr := deltaResultsIterator.Next()
		if nextErr != nil {
			return shim.Error(fmt.Sprintf("Could not retrieve next row for pruning: %s", nextErr.Error()))
		}

		deltaRowDelErr := APIstub.DelState(responseRange.Key)
		if deltaRowDelErr != nil {
			return shim.Error(fmt.Sprintf("Could not delete delta row: %s", deltaRowDelErr.Error()))
		}
	}

	// Insert new row for the final value
	updateResp := s.update(APIstub, []string{name, valueStr, "+"})
	if updateResp.Status == ERROR {
		return shim.Error(fmt.Sprintf("Could not insert the final value of the variable after pruning, variable backup is stored in %s_PRUNE_BACKUP: %s", name, updateResp.Message))
	}

	// Delete the backup value
	delErr := APIstub.DelState(fmt.Sprintf("%s_PRUNE_BACKUP", name))
	if delErr != nil {
		return shim.Error(fmt.Sprintf("Could not delete backup value %s_PRUNE_BACKUP, this does not affect the ledger but should be removed manually", name))
	}

	return shim.Success([]byte(fmt.Sprintf("Successfully pruned variable %s, final value is %f, %d rows pruned", name, val, i)))
}

/**
 * Deletes all rows associated with an aggregate variable from the ledger. The args array
 * contains the following argument:
 *	- args[0] -> The name of the variable to delete
 *
 * @param APIstub The chaincode shim
 * @param args The arguments array for the delete invocation
 *
 * @return A response structure indicating success or failure with a message
 */
func (s *SmartContract) delete(APIstub shim.ChaincodeStubInterface, args []string) sc.Response {
	// Check there are a correct number of arguments
	if len(args) != 1 {
		return shim.Error("Incorrect number of arguments, expecting 1")
	}

	// Retrieve the variable name
	name := args[0]

	// Delete all delta rows
	deltaResultsIterator, deltaErr := APIstub.GetStateByPartialCompositeKey("varName~op~value~txID", []string{name})
	if deltaErr != nil {
		return shim.Error(fmt.Sprintf("Could not retrieve delta rows for %s: %s", name, deltaErr.Error()))
	}
	defer deltaResultsIterator.Close()

	// Ensure the variable exists
	if !deltaResultsIterator.HasNext() {
		return shim.Error(fmt.Sprintf("No variable by the name %s exists", name))
	}

	// Iterate through result set and delete all indices
	var i int
	for i = 0; deltaResultsIterator.HasNext(); i++ {
		responseRange, nextErr := deltaResultsIterator.Next()
		if nextErr != nil {
			return shim.Error(fmt.Sprintf("Could not retrieve next delta row: %s", nextErr.Error()))
		}

		deltaRowDelErr := APIstub.DelState(responseRange.Key)
		if deltaRowDelErr != nil {
			return shim.Error(fmt.Sprintf("Could not delete delta row: %s", deltaRowDelErr.Error()))
		}
	}

	return shim.Success([]byte(fmt.Sprintf("Deleted %s, %d rows removed", name, i)))
}

/**
 * Converts a float64 to a byte array
 *
 * @param f The float64 to convert
 *
 * @return The byte array representation
 */
func f2barr(f float64) []byte {
	str := strconv.FormatFloat(f, 'f', -1, 64)

	return []byte(str)
}

// The main function is only relevant in unit test mode. Only included here for completeness.
func main() {

	// Create a new Smart Contract
	err := shim.Start(new(SmartContract))
	if err != nil {
		fmt.Printf("Error creating new Smart Contract: %s", err)
	}
}

/**
 * All functions below this are for testing traditional editing of a single row
 */
func (s *SmartContract) putStandard(APIstub shim.ChaincodeStubInterface, args []string) sc.Response {
	name := args[0]
	valStr := args[1]

	_, getErr := APIstub.GetState(name)
	if getErr != nil {
		return shim.Error(fmt.Sprintf("Failed to retrieve the statr of %s: %s", name, getErr.Error()))
	}

	putErr := APIstub.PutState(name, []byte(valStr))
	if putErr != nil {
		return shim.Error(fmt.Sprintf("Failed to put state: %s", putErr.Error()))
	}

	return shim.Success(nil)
}

func (s *SmartContract) getStandard(APIstub shim.ChaincodeStubInterface, args []string) sc.Response {
	name := args[0]

	val, getErr := APIstub.GetState(name)
	if getErr != nil {
		return shim.Error(fmt.Sprintf("Failed to get state: %s", getErr.Error()))
	}

	return shim.Success(val)
}
