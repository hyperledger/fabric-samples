// Copyright the Hyperledger Fabric contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package contractapi

import "github.com/hyperledger/fabric-contract-api-go/metadata"

// IgnoreContractInterface extends ContractInterface and provides additional functionality
// that can be used to mark which functions should not be accessible by invoking/querying
// chaincode
type IgnoreContractInterface interface {
	// GetIgnoredFunctions returns a list of function names for functions that should not
	// be included in the produced metadata or accessible by invoking/querying the chaincode.
	// Note these functions are still callable by the code just not directly by outside users.
	// Those that match functions in the ChaincodeInterface are ignored by default and do not
	// need to be included
	GetIgnoredFunctions() []string
}

// EvaluationContractInterface extends ContractInterface and provides additional functionality
// that can be used to improve metadata
type EvaluationContractInterface interface {
	// GetEvaluateTransactions returns a list of function names that should be tagged in the
	// metadata as "evaluate" to indicate to a user of the chaincode that they should query
	// rather than invoke these functions
	GetEvaluateTransactions() []string
}

// ContractInterface defines functions a valid contract should have. Contracts to
// be used in chaincode must implement this interface.
type ContractInterface interface {
	// GetInfo returns the information stored for the contract. This information will be
	// used to build up the metadata. If version is left blank in this info then "latest"
	// will be used in the metadata. If title is blank then the contract's GetName will be
	// used, if that is blank then the contract struct name
	GetInfo() metadata.InfoMetadata

	// GetUnknownTransaction returns the unknown function to be used for a contract.
	// When the contract is used in creating a new chaincode this function is called
	// and the unknown transaction returned is stored. The unknown function is then
	// called in cases where an unknown function name is passed for a call to the
	// contract via Init/Invoke of the chaincode. If nil is returned the
	// chaincode uses its default handling for unknown function names
	GetUnknownTransaction() interface{}

	// GetBeforeTransaction returns the before function to be used for a contract.
	// When the contract is used in creating a new chaincode this function is called
	// and the before transaction returned is stored. The before function is then
	// called before the named function on each Init/Invoke of that contract via the
	// chaincode. When called the before function is passed no extra args, only the
	// the transaction context (if specified to take it). If nil is returned
	// then no before function is called on Init/Invoke.
	GetBeforeTransaction() interface{}

	// GetAfterTransaction returns the after function to be used for a contract.
	// When the contract is used in creating a new chaincode this function is called
	// and the after transaction returned is stored. The after function is then
	// called after the named function on each Init/Invoke of that contract via the
	// chaincode. When called the after function is passed the returned value of the
	// named function and the transaction context (if the function takes the transaction
	// context). If nil is returned then no after function is called on Init/
	// Invoke.
	GetAfterTransaction() interface{}

	// GetName returns the name of the contract. When the contract is used
	// in creating a new chaincode this function is called and the name returned
	// is then used to identify the contract within the chaincode on Init/Invoke calls.
	// This function can return a blank string but this is undefined behaviour.
	GetName() string

	// GetTransactionContextHandler returns the SettableTransactionContextInterface that is
	// used by the functions of the contract. When the contract is used in creating
	// a new chaincode this function is called and the transaction context returned
	// is stored. When the chaincode is called via Init/Invoke a transaction context
	// of the stored type is created and sent as a parameter to the named contract
	// function (and before/after and unknown functions) if the function requires the
	// context in its list of parameters. If functions taking the transaction context
	// take an interface as the context, the transaction context returned by this function
	// must meet that interface
	GetTransactionContextHandler() SettableTransactionContextInterface
}

// Contract defines functions for setting and getting before, after and unknown transactions
// and name. Can be embedded in structs to quickly ensure their definition meets the
// ContractInterface.
type Contract struct {
	Name                      string
	Info                      metadata.InfoMetadata
	UnknownTransaction        interface{}
	BeforeTransaction         interface{}
	AfterTransaction          interface{}
	TransactionContextHandler SettableTransactionContextInterface
}

// GetInfo returns the info about the contract for use in metadata
func (c *Contract) GetInfo() metadata.InfoMetadata {
	return c.Info
}

// GetUnknownTransaction returns the current set unknownTransaction, may be nil
func (c *Contract) GetUnknownTransaction() interface{} {
	return c.UnknownTransaction
}

// GetBeforeTransaction returns the current set beforeTransaction, may be nil
func (c *Contract) GetBeforeTransaction() interface{} {
	return c.BeforeTransaction
}

// GetAfterTransaction returns the current set afterTransaction, may be nil
func (c *Contract) GetAfterTransaction() interface{} {
	return c.AfterTransaction
}

// GetName returns the name of the contract
func (c *Contract) GetName() string {
	return c.Name
}

// GetTransactionContextHandler returns the current transaction context set for
// the contract. If none has been set then TransactionContext will be returned
func (c *Contract) GetTransactionContextHandler() SettableTransactionContextInterface {
	if c.TransactionContextHandler == nil {
		return new(TransactionContext)
	}

	return c.TransactionContextHandler
}
