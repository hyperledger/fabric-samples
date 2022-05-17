package chaincode

import (
	"fmt"
	"log"
	"strconv"

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

// SmartContract provides functions for transferring tokens using UTXO transactions
type SmartContract struct {
	contractapi.Contract
}

// UTXO represents an unspent transaction output
type UTXO struct {
	Key    string `json:"utxo_key"`
	Owner  string `json:"owner"`
	Amount int    `json:"amount"`
}

// Define key names for options
const nameKey = "name"
const symbolKey = "symbol"
const totalSupplyKey = "totalSupply"

// Mint creates a new unspent transaction output (UTXO) owned by the minter
func (s *SmartContract) Mint(ctx contractapi.TransactionContextInterface, amount int) (*UTXO, error) {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to check if contract ia already initialized: %v", err)
	}
	if !initialized {
		return nil, fmt.Errorf("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	// Check minter authorization - this sample assumes Org1 is the central banker with privilege to mint new tokens
	clientMSPID, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return nil, fmt.Errorf("failed to get MSPID: %v", err)
	}
	if clientMSPID != "Org1MSP" {
		return nil, fmt.Errorf("client is not authorized to mint new tokens")
	}

	// Get ID of submitting client identity
	minter, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return nil, fmt.Errorf("failed to get client id: %v", err)
	}

	if amount <= 0 {
		return nil, fmt.Errorf("mint amount must be a positive integer")
	}

	utxo := UTXO{}
	utxo.Key = ctx.GetStub().GetTxID() + ".0"
	utxo.Owner = minter
	utxo.Amount = amount

	// the utxo has a composite key of owner:utxoKey, this enables ClientUTXOs() function to query for an owner's utxos.
	utxoCompositeKey, err := ctx.GetStub().CreateCompositeKey("utxo", []string{minter, utxo.Key})

	err = ctx.GetStub().PutState(utxoCompositeKey, []byte(strconv.Itoa(amount)))
	if err != nil {
		return nil, err
	}

	log.Printf("utxo minted: %+v", utxo)

	return &utxo, nil
}

// Transfer transfers UTXOs containing tokens from client to recipient(s)
func (s *SmartContract) Transfer(ctx contractapi.TransactionContextInterface, utxoInputKeys []string, utxoOutputs []UTXO) ([]UTXO, error) {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to check if contract ia already initialized: %v", err)
	}
	if !initialized {
		return nil, fmt.Errorf("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	// Get ID of submitting client identity
	clientID, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return nil, fmt.Errorf("failed to get client id: %v", err)
	}

	// Validate and summarize utxo inputs
	utxoInputs := make(map[string]*UTXO)
	var totalInputAmount int
	for _, utxoInputKey := range utxoInputKeys {
		if utxoInputs[utxoInputKey] != nil {
			return nil, fmt.Errorf("the same utxo input can not be spend twice")
		}

		utxoInputCompositeKey, err := ctx.GetStub().CreateCompositeKey("utxo", []string{clientID, utxoInputKey})
		if err != nil {
			return nil, fmt.Errorf("failed to create composite key: %v", err)
		}

		// validate that client has a utxo matching the input key
		valueBytes, err := ctx.GetStub().GetState(utxoInputCompositeKey)
		if err != nil {
			return nil, fmt.Errorf("failed to read utxoInputCompositeKey %s from world state: %v", utxoInputCompositeKey, err)
		}

		if valueBytes == nil {
			return nil, fmt.Errorf("utxoInput %s not found for client %s", utxoInputKey, clientID)
		}

		amount, _ := strconv.Atoi(string(valueBytes)) // Error handling not needed since Itoa() was used when setting the utxo amount, guaranteeing it was an integer.

		utxoInput := &UTXO{
			Key:    utxoInputKey,
			Owner:  clientID,
			Amount: amount,
		}

		totalInputAmount, err = add(totalInputAmount, amount)
		utxoInputs[utxoInputKey] = utxoInput
	}

	// Validate and summarize utxo outputs
	var totalOutputAmount int
	txID := ctx.GetStub().GetTxID()
	for i, utxoOutput := range utxoOutputs {

		if utxoOutput.Amount <= 0 {
			return nil, fmt.Errorf("utxo output amount must be a positive integer")
		}

		utxoOutputs[i].Key = fmt.Sprintf("%s.%d", txID, i)

		totalOutputAmount, err = add(totalOutputAmount, utxoOutput.Amount)
	}

	// Validate total inputs equals total outputs
	if totalInputAmount != totalOutputAmount {
		return nil, fmt.Errorf("total utxoInput amount %d does not equal total utxoOutput amount %d", totalInputAmount, totalOutputAmount)
	}

	// Since the transaction is valid, now delete utxo inputs from owner's state
	for _, utxoInput := range utxoInputs {

		utxoInputCompositeKey, err := ctx.GetStub().CreateCompositeKey("utxo", []string{utxoInput.Owner, utxoInput.Key})
		if err != nil {
			return nil, fmt.Errorf("failed to create composite key: %v", err)
		}

		err = ctx.GetStub().DelState(utxoInputCompositeKey)
		if err != nil {
			return nil, err
		}
		log.Printf("utxoInput deleted: %+v", utxoInput)
	}

	// Create utxo outputs using a composite key based on the owner and utxo key
	for _, utxoOutput := range utxoOutputs {
		utxoOutputCompositeKey, err := ctx.GetStub().CreateCompositeKey("utxo", []string{utxoOutput.Owner, utxoOutput.Key})
		if err != nil {
			return nil, fmt.Errorf("failed to create composite key: %v", err)
		}

		err = ctx.GetStub().PutState(utxoOutputCompositeKey, []byte(strconv.Itoa(utxoOutput.Amount)))
		if err != nil {
			return nil, err
		}
		log.Printf("utxoOutput created: %+v", utxoOutput)
	}

	return utxoOutputs, nil
}

// ClientUTXOs returns all UTXOs owned by the calling client
func (s *SmartContract) ClientUTXOs(ctx contractapi.TransactionContextInterface) ([]*UTXO, error) {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to check if contract ia already initialized: %v", err)
	}
	if !initialized {
		return nil, fmt.Errorf("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	// Get ID of submitting client identity
	clientID, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return nil, fmt.Errorf("failed to get client id: %v", err)
	}

	// since utxos have a composite key of owner:utxoKey, we can query for all utxos matching owner:*
	utxoResultsIterator, err := ctx.GetStub().GetStateByPartialCompositeKey("utxo", []string{clientID})
	if err != nil {
		return nil, err
	}
	defer utxoResultsIterator.Close()

	var utxos []*UTXO
	for utxoResultsIterator.HasNext() {
		utxoRecord, err := utxoResultsIterator.Next()
		if err != nil {
			return nil, err
		}

		// composite key is expected to be owner:utxoKey
		_, compositeKeyParts, err := ctx.GetStub().SplitCompositeKey(utxoRecord.Key)
		if err != nil {
			return nil, err
		}

		if len(compositeKeyParts) != 2 {
			return nil, fmt.Errorf("expected composite key with two parts (owner:utxoKey)")
		}

		utxoKey := compositeKeyParts[1] // owner is at [0], utxoKey is at[1]

		if utxoRecord.Value == nil {
			return nil, fmt.Errorf("utxo %s has no value", utxoKey)
		}

		amount, _ := strconv.Atoi(string(utxoRecord.Value)) // Error handling not needed since Itoa() was used when setting the utxo amount, guaranteeing it was an integer.

		utxo := &UTXO{
			Key:    utxoKey,
			Owner:  clientID,
			Amount: amount,
		}

		utxos = append(utxos, utxo)
	}
	return utxos, nil
}

// ClientID returns the client id of the calling client
// Users can use this function to get their own client id, which they can then give to others as the payment address
func (s *SmartContract) ClientID(ctx contractapi.TransactionContextInterface) (string, error) {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return "", fmt.Errorf("failed to check if contract ia already initialized: %v", err)
	}
	if !initialized {
		return "", fmt.Errorf("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	// Get ID of submitting client identity
	clientID, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return "", fmt.Errorf("failed to get client id: %v", err)
	}

	return clientID, nil
}

// Name returns a descriptive name for fungible tokens in this contract
// returns {String} Returns the name of the token

func (s *SmartContract) Name(ctx contractapi.TransactionContextInterface) (string, error) {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return "", fmt.Errorf("failed to check if contract ia already initialized: %v", err)
	}
	if !initialized {
		return "", fmt.Errorf("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	bytes, err := ctx.GetStub().GetState(nameKey)
	if err != nil {
		return "", fmt.Errorf("failed to get Name bytes: %s", err)
	}

	return string(bytes), nil
}

// Symbol returns an abbreviated name for fungible tokens in this contract.
// returns {String} Returns the symbol of the token

func (s *SmartContract) Symbol(ctx contractapi.TransactionContextInterface) (string, error) {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return "", fmt.Errorf("failed to check if contract ia already initialized: %v", err)
	}
	if !initialized {
		return "", fmt.Errorf("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	bytes, err := ctx.GetStub().GetState(symbolKey)
	if err != nil {
		return "", fmt.Errorf("failed to get Symbol: %v", err)
	}

	return string(bytes), nil
}

// Set information for a token and intialize contract.
// param {String} name The name of the token
// param {String} symbol The symbol of the token
func (s *SmartContract) Initialize(ctx contractapi.TransactionContextInterface, name string, symbol string) (bool, error) {

	// Check minter authorization - this sample assumes Org1 is the central banker with privilege to intitialize contract
	clientMSPID, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return false, fmt.Errorf("failed to get MSPID: %v", err)
	}
	if clientMSPID != "Org1MSP" {
		return false, fmt.Errorf("client is not authorized to initialize contract")
	}

	//check contract options are not already set, client is not authorized to change them once intitialized
	bytes, err := ctx.GetStub().GetState(nameKey)
	if err != nil {
		return false, fmt.Errorf("failed to get Name: %v", err)
	}
	if bytes != nil {
		return false, fmt.Errorf("contract options are already set, client is not authorized to change them")
	}

	err = ctx.GetStub().PutState(nameKey, []byte(name))
	if err != nil {
		return false, fmt.Errorf("failed to set token name: %v", err)
	}

	err = ctx.GetStub().PutState(symbolKey, []byte(symbol))
	if err != nil {
		return false, fmt.Errorf("failed to set symbol: %v", err)
	}

	log.Printf("name: %v, symbol: %v", name, symbol)

	return true, nil
}

//Checks that contract options have been already initialized
func checkInitialized(ctx contractapi.TransactionContextInterface) (bool, error) {
	tokenName, err := ctx.GetStub().GetState(nameKey)
	if err != nil {
		return false, fmt.Errorf("failed to get token name: %v", err)
	}
	if tokenName == nil {
		return false, nil
	}
	return true, nil
}

// add two number checking for overflow
func add(b int, q int) (int, error) {

	// Check overflow
	var sum int
	sum = q + b

	if (sum < q) == (b >= 0 && q >= 0) {
		return 0, fmt.Errorf("Math: addition overflow occurred %d + %d", b, q)
	}

	return sum, nil
}
