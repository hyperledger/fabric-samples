package contract_test

import (
	"encoding/json"
	"fmt"
	"testing"

	"github.com/hyperledger/fabric-protos-go/ledger/queryresult"
	"github.com/hyperledger/fabric-samples/chaincode/fabcar/go/contract"
	"github.com/hyperledger/fabric-samples/chaincode/fabcar/go/contract/mocks"
	"github.com/stretchr/testify/require"
)

func TestInitLedger(t *testing.T) {
	chaincodeStub := &mocks.ChaincodeStub{}
	transactionContext := &mocks.TransactionContext{}
	transactionContext.GetStubReturns(chaincodeStub)

	fabcar := &contract.SmartContract{}
	err := fabcar.InitLedger(transactionContext)
	require.NoError(t, err)

	chaincodeStub.PutStateReturns(fmt.Errorf("failed inserting key"))
	err = fabcar.InitLedger(transactionContext)
	require.EqualError(t, err, "failed to put to world state. failed inserting key")
}

func TestCreateCar(t *testing.T) {
	chaincodeStub := &mocks.ChaincodeStub{}
	transactionContext := &mocks.TransactionContext{}
	transactionContext.GetStubReturns(chaincodeStub)

	fabcar := &contract.SmartContract{}
	err := fabcar.CreateCar(transactionContext, "CAR1", "Ford", "F150", "red", "Tim")
	require.NoError(t, err)

	chaincodeStub.PutStateReturns(fmt.Errorf("failed inserting key"))
	err = fabcar.CreateCar(transactionContext, "CAR1", "Ford", "F150", "red", "Tim")
	require.EqualError(t, err, "failed inserting key")

}

func TestQueryCar(t *testing.T) {
	car := &contract.Car{
		Make:   "Ford",
		Model:  "F150",
		Colour: "red",
		Owner:  "Tim",
	}
	bytes, err := json.Marshal(car)
	require.NoError(t, err)

	chaincodeStub := &mocks.ChaincodeStub{}
	chaincodeStub.GetStateReturns(bytes, nil)
	transactionContext := &mocks.TransactionContext{}
	transactionContext.GetStubReturns(chaincodeStub)

	fabcar := &contract.SmartContract{}
	car, err = fabcar.QueryCar(transactionContext, "CAR1")
	require.NoError(t, err)
	require.NotNil(t, car)

	chaincodeStub.GetStateReturns(nil, nil)
	car, err = fabcar.QueryCar(transactionContext, "CAR1")
	require.EqualError(t, err, "CAR1 does not exist")
	require.Nil(t, car)

	chaincodeStub.GetStateReturns([]byte{}, fmt.Errorf("failed retrieving key"))
	car, err = fabcar.QueryCar(transactionContext, "CAR1")
	require.Nil(t, car)
	require.EqualError(t, err, "failed to read from world state: failed retrieving key")
}

func TestQueryAllCars(t *testing.T) {
	car := &contract.Car{
		Make:   "Ford",
		Model:  "F150",
		Colour: "red",
		Owner:  "Tim",
	}
	bytes, err := json.Marshal(car)
	require.NoError(t, err)

	iterator := &mocks.StateQueryIterator{}
	iterator.HasNextReturnsOnCall(0, true)
	iterator.HasNextReturnsOnCall(1, false)
	iterator.NextReturns(&queryresult.KV{Value: bytes}, nil)

	chaincodeStub := &mocks.ChaincodeStub{}
	chaincodeStub.GetStateByRangeReturns(iterator, nil)
	transactionContext := &mocks.TransactionContext{}
	transactionContext.GetStubReturns(chaincodeStub)

	fabcar := &contract.SmartContract{}
	cars, err := fabcar.QueryAllCars(transactionContext)
	require.NoError(t, err)
	require.NotNil(t, cars)

	iterator.HasNextReturns(true)
	iterator.NextReturns(nil, fmt.Errorf("failed retrieving next item"))
	cars, err = fabcar.QueryAllCars(transactionContext)
	require.EqualError(t, err, "failed retrieving next item")
	require.Nil(t, cars)

	chaincodeStub.GetStateByRangeReturns(nil, fmt.Errorf("failed retrieving all cars"))
	cars, err = fabcar.QueryAllCars(transactionContext)
	require.EqualError(t, err, "failed retrieving all cars")
	require.Nil(t, cars)
}

func TestChangeCarOwner(t *testing.T) {
	car := &contract.Car{
		Make:   "Ford",
		Model:  "F150",
		Colour: "red",
		Owner:  "Tim",
	}
	bytes, err := json.Marshal(car)
	require.NoError(t, err)

	chaincodeStub := &mocks.ChaincodeStub{}
	chaincodeStub.GetStateReturns(bytes, nil)
	transactionContext := &mocks.TransactionContext{}
	transactionContext.GetStubReturns(chaincodeStub)

	fabcar := &contract.SmartContract{}
	err = fabcar.ChangeCarOwner(transactionContext, "CAR1", "Ben")
	require.NoError(t, err)

	chaincodeStub.GetStateReturns(nil, nil)
	err = fabcar.ChangeCarOwner(transactionContext, "CAR1", "Ben")
	require.EqualError(t, err, "CAR1 does not exist")
}
