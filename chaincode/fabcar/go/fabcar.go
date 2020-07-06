/*
SPDX-License-Identifier: Apache-2.0
*/

package main

import (
	"encoding/json"
	"fmt"
	"log"

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

// SmartContract provides functions for managing a car
type SmartContract struct {
	contractapi.Contract
}

// Car describes basic details of what makes up a car
type Car struct {
	Make   string `json:"make"`
	Model  string `json:"model"`
	Colour string `json:"colour"`
	Owner  string `json:"owner"`
}

// QueryResult structure used for handling result of query
type QueryResult struct {
	Key    string `json:"Key"`
	Record *Car
}

// InitLedger adds a base set of cars to the ledger
func (s *SmartContract) InitLedger(ctx contractapi.TransactionContextInterface) error {
	cars := []Car{
		{Make: "Toyota", Model: "Prius", Colour: "blue", Owner: "Tomoko"},
		{Make: "Ford", Model: "Mustang", Colour: "red", Owner: "Brad"},
		{Make: "Hyundai", Model: "Tucson", Colour: "green", Owner: "Jin Soo"},
		{Make: "Volkswagen", Model: "Passat", Colour: "yellow", Owner: "Max"},
		{Make: "Tesla", Model: "S", Colour: "black", Owner: "Adriana"},
		{Make: "Peugeot", Model: "205", Colour: "purple", Owner: "Michel"},
		{Make: "Chery", Model: "S22L", Colour: "white", Owner: "Aarav"},
		{Make: "Fiat", Model: "Punto", Colour: "violet", Owner: "Pari"},
		{Make: "Tata", Model: "Nano", Colour: "indigo", Owner: "Valeria"},
		{Make: "Holden", Model: "Barina", Colour: "brown", Owner: "Shotaro"},
	}

	for i, car := range cars {
		carAsBytes, _ := json.Marshal(car)
		key := fmt.Sprintf("CAR%d", i)
		err := ctx.GetStub().PutState(key, carAsBytes)
		if err != nil {
			return fmt.Errorf("failed to put to world state. %v", err)
		}
	}

	return nil
}

// CreateCar adds a new car to the world state with given details
func (s *SmartContract) CreateCar(ctx contractapi.TransactionContextInterface, carNumber string, make string, model string, colour string, owner string) error {
	car := Car{
		Make:   make,
		Model:  model,
		Colour: colour,
		Owner:  owner,
	}
	carAsBytes, err := json.Marshal(car)
	if err != nil {
		return fmt.Errorf("failed marshalling to json: %v", err)
	}
	return ctx.GetStub().PutState(carNumber, carAsBytes)
}

// QueryCar returns the car stored in the world state with given id
func (s *SmartContract) QueryCar(ctx contractapi.TransactionContextInterface, carNumber string) (*Car, error) {
	carAsBytes, err := ctx.GetStub().GetState(carNumber)
	if err != nil {
		return nil, fmt.Errorf("failed to read from world state: %v", err)
	}
	if carAsBytes == nil {
		return nil, fmt.Errorf("%s does not exist", carNumber)
	}
	var car *Car
	err = json.Unmarshal(carAsBytes, car)
	if err != nil {
		return nil, err
	}
	return car, nil
}

// QueryAllCars returns all cars found in world state
func (s *SmartContract) QueryAllCars(ctx contractapi.TransactionContextInterface) ([]QueryResult, error) {
	// Return all cars by using empty startKey and endKey
	resultsIterator, err := ctx.GetStub().GetStateByRange("", "")
	if err != nil {
		return nil, err
	}
	defer resultsIterator.Close()

	var results []QueryResult
	for resultsIterator.HasNext() {
		queryResponse, err := resultsIterator.Next()
		if err != nil {
			return nil, err
		}

		var car *Car
		err = json.Unmarshal(queryResponse.Value, car)
		if err != nil {
			return nil, fmt.Errorf("failed marshalling to json: %v", err)
		}
		queryResult := QueryResult{Key: queryResponse.Key, Record: car}
		results = append(results, queryResult)
	}
	return results, nil
}

// ChangeCarOwner updates the owner field of car with given id in world state
func (s *SmartContract) ChangeCarOwner(ctx contractapi.TransactionContextInterface, carNumber string, newOwner string) error {
	car, err := s.QueryCar(ctx, carNumber)
	if err != nil {
		return err
	}

	car.Owner = newOwner
	carAsBytes, err := json.Marshal(car)
	if err != nil {
		return fmt.Errorf("failed marshalling to json: %v", err)
	}
	return ctx.GetStub().PutState(carNumber, carAsBytes)
}

func main() {
	chaincode, err := contractapi.NewChaincode(&SmartContract{})
	if err != nil {
		log.Panicf("Error create fabcar chaincode: %v", err)
	}

	if err := chaincode.Start(); err != nil {
		log.Panicf("Error starting fabcar chaincode: %v", err)
	}
}
