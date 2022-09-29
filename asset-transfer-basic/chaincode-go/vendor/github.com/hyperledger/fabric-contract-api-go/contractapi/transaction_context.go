// Copyright the Hyperledger Fabric contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package contractapi

import (
	"github.com/hyperledger/fabric-chaincode-go/pkg/cid"
	"github.com/hyperledger/fabric-chaincode-go/shim"
)

// TransactionContextInterface defines the interface which TransactionContext
// meets. This can be taken by transacton functions on a contract which has not set
// a custom transaction context to allow transaction functions to take an interface
// to simplify unit testing.
type TransactionContextInterface interface {
	// GetStub should provide a way to access the stub set by Init/Invoke
	GetStub() shim.ChaincodeStubInterface
	// GetClientIdentity should provide a way to access the client identity set by Init/Invoke
	GetClientIdentity() cid.ClientIdentity
}

// SettableTransactionContextInterface defines functions a valid transaction context
// should have. Transaction context's set for contracts to be used in chaincode
// must implement this interface.
type SettableTransactionContextInterface interface {
	// SetStub should provide a way to pass the stub from a chaincode transaction
	// call to the transaction context so that it can be used by contract functions.
	// This is called by Init/Invoke with the stub passed.
	SetStub(shim.ChaincodeStubInterface)
	// SetClientIdentity should provide a way to pass the client identity from a chaincode
	// transaction call to the transaction context so that it can be used by contract functions.
	// This is called by Init/Invoke with the stub passed.
	SetClientIdentity(ci cid.ClientIdentity)
}

// TransactionContext is a basic transaction context to be used in contracts,
// containing minimal required functionality use in contracts as part of
// chaincode. Provides access to the stub and clientIdentity of a transaction.
// If a contract implements the ContractInterface using the Contract struct then
// this is the default transaction context that will be used.
type TransactionContext struct {
	stub           shim.ChaincodeStubInterface
	clientIdentity cid.ClientIdentity
}

// SetStub stores the passed stub in the transaction context
func (ctx *TransactionContext) SetStub(stub shim.ChaincodeStubInterface) {
	ctx.stub = stub
}

// SetClientIdentity stores the passed stub in the transaction context
func (ctx *TransactionContext) SetClientIdentity(ci cid.ClientIdentity) {
	ctx.clientIdentity = ci
}

// GetStub returns the current set stub
func (ctx *TransactionContext) GetStub() shim.ChaincodeStubInterface {
	return ctx.stub
}

// GetClientIdentity returns the current set client identity
func (ctx *TransactionContext) GetClientIdentity() cid.ClientIdentity {
	return ctx.clientIdentity
}
