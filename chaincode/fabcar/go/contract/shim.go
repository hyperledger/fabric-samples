package contract

import (
	"github.com/hyperledger/fabric-chaincode-go/shim"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

//go:generate counterfeiter -o mocks/transaction.go -fake-name TransactionContext . TransactionContext
type TransactionContext interface {
	contractapi.TransactionContextInterface
}

//go:generate counterfeiter -o mocks/chaincodestub.go -fake-name ChaincodeStub . ChaincodeStub
type ChaincodeStub interface {
	shim.ChaincodeStubInterface
}

//go:generate counterfeiter -o mocks/statequeryiterator.go -fake-name StateQueryIterator . StateQueryIterator
type StateQueryIterator interface {
	shim.StateQueryIteratorInterface
}
