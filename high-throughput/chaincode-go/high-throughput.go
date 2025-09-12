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
	"log"
	"strconv"

	"github.com/hyperledger/fabric-contract-api-go/v2/contractapi"
)

func main() {
	chaincode, err := contractapi.NewChaincode(&SmartContract{})
	if err != nil {
		log.Panicf("Error creating chaincode: %s", err)
	}

	if err := chaincode.Start(); err != nil {
		log.Panicf("Error starting chaincode: %s", err)
	}
}

// SmartContract is the data structure which represents this contract and its functions
type SmartContract struct {
	contractapi.Contract
}

/**
 * Updates the ledger to include a new delta for a particular variable. If this is the first time
 * this variable is being added to the ledger, then its initial value is assumed to be 0. The arguments
 * to give in the args array are as follows:
 *	- args[0] -> name of the variable
 *	- args[1] -> new delta (float)
 *	- args[2] -> operation (currently supported are addition "+" and subtraction "-")
 *
 * Returns a response indicating success or failure with a message.
 */
func (s *SmartContract) Update(ctx contractapi.TransactionContextInterface, name string, delta string, op string) (string, error) {
	_, err := strconv.ParseFloat(delta, 64)
	if err != nil {
		return "", fmt.Errorf("provided value was not a number: %s", delta)
	}

	// Make sure a valid operator is provided
	if op != "+" && op != "-" {
		return "", fmt.Errorf("operator %s is unrecognized", op)
	}

	// Retrieve info needed for the update procedure
	txid := ctx.GetStub().GetTxID()
	compositeIndexName := "varName~op~value~txID"

	// Create the composite key that will allow us to query for all deltas on a particular variable
	compositeKey, compositeErr := ctx.GetStub().CreateCompositeKey(compositeIndexName, []string{name, op, delta, txid})
	if compositeErr != nil {
		return "", fmt.Errorf("could not create a composite key for %s: %w", name, compositeErr)
	}

	// Save the composite key index
	compositePutErr := ctx.GetStub().PutState(compositeKey, []byte{0x00})
	if compositePutErr != nil {
		return "", fmt.Errorf("could not put operation for %s in the ledger: %w", name, compositePutErr)
	}

	return fmt.Sprintf("Successfully added %s%s to %s", op, delta, name), nil
}

/**
 * Retrieves the aggregate value of a variable in the ledger. Gets all delta rows for the variable
 * and computes the final value from all deltas. The args array for the invocation must contain the
 * following argument:
 *	- args[0] -> The name of the variable to get the value of
 *
 * Returns a response indicating success or failure with a message
 */
func (s *SmartContract) Get(ctx contractapi.TransactionContextInterface, name string) (string, error) {
	// Get all deltas for the variable
	deltaResultsIterator, deltaErr := ctx.GetStub().GetStateByPartialCompositeKey("varName~op~value~txID", []string{name})
	if deltaErr != nil {
		return "", fmt.Errorf("could not retrieve value for %s: %w", name, deltaErr)
	}
	defer deltaResultsIterator.Close()

	// Check the variable existed
	if !deltaResultsIterator.HasNext() {
		return "", fmt.Errorf("no variable by the name %s exists", name)
	}

	// Iterate through result set and compute final value
	var finalVal float64
	for deltaResultsIterator.HasNext() {
		// Get the next row
		responseRange, nextErr := deltaResultsIterator.Next()
		if nextErr != nil {
			return "", nextErr
		}

		// Split the composite key into its component parts
		_, keyParts, splitKeyErr := ctx.GetStub().SplitCompositeKey(responseRange.Key)
		if splitKeyErr != nil {
			return "", splitKeyErr
		}

		// Retrieve the delta value and operation
		operation := keyParts[1]
		valueStr := keyParts[2]

		// Convert the value string and perform the operation
		value, convErr := strconv.ParseFloat(valueStr, 64)
		if convErr != nil {
			return "", convErr
		}

		switch operation {
		case "+":
			finalVal += value
		case "-":
			finalVal -= value
		default:
			return "", fmt.Errorf("unrecognized operation %s", operation)
		}
	}

	return strconv.FormatFloat(finalVal, 'f', -1, 64), nil
}

/**
 * Prunes a variable by deleting all of its delta rows while computing the final value. Once all rows
 * have been processed and deleted, a single new row is added which defines a delta containing the final
 * computed value of the variable. The args array contains the following argument:
 *	- args[0] -> The name of the variable to prune
 *
 * Returns a response indicating success or failure with a message
 */
func (s *SmartContract) Prune(ctx contractapi.TransactionContextInterface, name string) (string, error) {
	// Get all delta rows for the variable
	deltaResultsIterator, deltaErr := ctx.GetStub().GetStateByPartialCompositeKey("varName~op~value~txID", []string{name})
	if deltaErr != nil {
		return "", fmt.Errorf("could not retrieve value for %s: %w", name, deltaErr)
	}
	defer deltaResultsIterator.Close()

	// Check the variable existed
	if !deltaResultsIterator.HasNext() {
		return "", fmt.Errorf("no variable by the name %s exists", name)
	}

	// Iterate through result set computing final value while iterating and deleting each key
	var finalVal float64
	var i int
	for ; deltaResultsIterator.HasNext(); i++ {
		// Get the next row
		responseRange, nextErr := deltaResultsIterator.Next()
		if nextErr != nil {
			return "", nextErr
		}

		// Split the key into its composite parts
		_, keyParts, splitKeyErr := ctx.GetStub().SplitCompositeKey(responseRange.Key)
		if splitKeyErr != nil {
			return "", splitKeyErr
		}

		// Retrieve the operation and value
		operation := keyParts[1]
		valueStr := keyParts[2]

		// Convert the value to a float
		value, convErr := strconv.ParseFloat(valueStr, 64)
		if convErr != nil {
			return "", convErr
		}

		// Delete the row from the ledger
		deltaRowDelErr := ctx.GetStub().DelState(responseRange.Key)
		if deltaRowDelErr != nil {
			return "", fmt.Errorf("could not delete delta row: %w", deltaRowDelErr)
		}

		// Add the value of the deleted row to the final aggregate
		switch operation {
		case "+":
			finalVal += value
		case "-":
			finalVal -= value
		default:
			return "", fmt.Errorf("unrecognized operation %s", operation)
		}
	}

	// Update the ledger with the final value
	if updateMessage, err := s.Update(ctx, name, strconv.FormatFloat(finalVal, 'f', -1, 64), "+"); err != nil {
		return "", fmt.Errorf("could not update the final value of the variable after pruning: %s", updateMessage)
	}

	return fmt.Sprintf("Successfully pruned variable %s, final value is %f, %d rows pruned", name, finalVal, i), nil
}

/**
 * Deletes all rows associated with an aggregate variable from the ledger. The args array
 * contains the following argument:
 *	- args[0] -> The name of the variable to delete
 *
 * Returns a response indicating success or failure with a message
 */
func (s *SmartContract) Delete(ctx contractapi.TransactionContextInterface, name string) (string, error) {
	// Delete all delta rows
	deltaResultsIterator, deltaErr := ctx.GetStub().GetStateByPartialCompositeKey("varName~op~value~txID", []string{name})
	if deltaErr != nil {
		return "", fmt.Errorf("could not retrieve delta rows for %s: %w", name, deltaErr)
	}
	defer deltaResultsIterator.Close()

	// Ensure the variable exists
	if !deltaResultsIterator.HasNext() {
		return "", fmt.Errorf("no variable by the name %s exists", name)
	}

	// Iterate through result set and delete all indices
	var i int
	for ; deltaResultsIterator.HasNext(); i++ {
		responseRange, nextErr := deltaResultsIterator.Next()
		if nextErr != nil {
			return "", fmt.Errorf("could not retrieve next delta row: %w", nextErr)
		}

		deltaRowDelErr := ctx.GetStub().DelState(responseRange.Key)
		if deltaRowDelErr != nil {
			return "", fmt.Errorf("could not delete delta row: %w", deltaRowDelErr)
		}
	}

	return fmt.Sprintf("Deleted %s, %d rows removed", name, i), nil
}

/**
 * All functions below this are for testing traditional editing of a single row
 */
func (s *SmartContract) UpdateStandard(ctx contractapi.TransactionContextInterface, name string, delta string, operation string) (float64, error) {
	deltaValue, err := strconv.ParseFloat(delta, 64)
	if err != nil {
		return 0, err
	}

	var currentValue float64
	if valueBytes, err := ctx.GetStub().GetState(name); err == nil && len(valueBytes) > 0 {
		currentValue, err = strconv.ParseFloat(string(valueBytes), 64)
		if err != nil {
			return 0, err
		}
	}

	switch operation {
	case "+":
		currentValue += deltaValue
	case "-":
		currentValue -= deltaValue
	default:
		return 0, fmt.Errorf("unrecognized operation %s", operation)
	}

	valueStr := strconv.FormatFloat(currentValue, 'f', -1, 64)

	if err := ctx.GetStub().PutState(name, []byte(valueStr)); err != nil {
		return 0, fmt.Errorf("failed to put state: %w", err)
	}

	return currentValue, nil
}

func (s *SmartContract) GetStandard(ctx contractapi.TransactionContextInterface, name string) (string, error) {
	valueBytes, err := ctx.GetStub().GetState(name)
	if err != nil {
		return "", fmt.Errorf("failed to get state: %w", err)
	}

	value, err := strconv.ParseFloat(string(valueBytes), 64)
	if err != nil {
		return "", err
	}

	return strconv.FormatFloat(value, 'f', -1, 64), nil
}

func (s *SmartContract) DelStandard(ctx contractapi.TransactionContextInterface, name string) error {
	if err := ctx.GetStub().DelState(name); err != nil {
		return fmt.Errorf("failed to delete state: %w", err)
	}

	return nil
}
