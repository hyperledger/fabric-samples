/*******************************************************************************
 * IBM Confidential
 *
 * OCO Source Materials
 *
 * Copyright IBM Corp. 2020
 *
 * The source code for this program is not published or otherwise
 * divested of its trade secrets, irrespective of what has been
 * deposited with the U.S. Copyright Office.
 *******************************************************************************/
package chaincode

import (
	"fmt"
	"log"
	"strconv"

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

// SmartContract provides functions for transferring tokens between accounts
type SmartContract struct {
	contractapi.Contract
}

// TotalSupply - returns the total number of tokens minted
func (s *SmartContract) TotalSupply(ctx contractapi.TransactionContextInterface) (int, error) {

	// Get ID of submitting client identity
	clientID, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return 0, fmt.Errorf("failed to get client id: %v", err)
	}

	var minterTotalSupply = "TotalSupply"
	balanceBytes, err := ctx.GetStub().GetState(minterTotalSupply)
	if err != nil {
		return 0, fmt.Errorf("failed to read from world state: %v", err)
	}

	if balanceBytes == nil {
		return 0, fmt.Errorf("the account %s does not exist", clientID)
	}

	balance, _ := strconv.Atoi(string(balanceBytes))

	return balance, nil
}

// Mint creates new tokens and adds them to minter's account balance
func (s *SmartContract) Mint(ctx contractapi.TransactionContextInterface, amount int) error {

	// Check minter authorization - this sample assumes Org1 to mint new tokens
	client := ctx.GetClientIdentity()
	clientMSPID, err := client.GetMSPID()
	if err != nil {
		return fmt.Errorf("failed to get MSPID: %v", err)
	}
	if clientMSPID != "Org1MSP" {
		return fmt.Errorf("client is not authorized to mint new tokens")
	}

	// Get ID of submitting client identity
	minter, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return fmt.Errorf("failed to get client id: %v", err)
	}

	if amount <= 0 {
		return fmt.Errorf("mint amount must be a positive integer")
	}

	currentBalanceBytes, err := ctx.GetStub().GetState(minter)
	if err != nil {
		return fmt.Errorf("failed to read minter account %s from world state: %v", minter, err)
	}

	var currentBalance int

	// If minter current balance doesn't yet exist, we'll create it with a current balance of 0
	if currentBalanceBytes == nil {
		currentBalance = 0
	} else {
		currentBalance, _ = strconv.Atoi(string(currentBalanceBytes))
	}

	updatedBalance := currentBalance + amount

	err = ctx.GetStub().PutState(minter, []byte(strconv.Itoa(updatedBalance)))
	if err != nil {
		return err
	}

	var minterTotalSupply = "TotalSupply"
	currentBalanceBytes, err = ctx.GetStub().GetState(minterTotalSupply)
	if err != nil {
		return fmt.Errorf("failed to read minter account %s from world state: %v", minter, err)
	}

	// If minter current balance doesn't yet exist, we'll create it with a current balance of 0
	if currentBalanceBytes == nil {
		currentBalance = 0
	} else {
		currentBalance, _ = strconv.Atoi(string(currentBalanceBytes))
	}

	updatedBalance = currentBalance + amount

	err = ctx.GetStub().PutState(minterTotalSupply, []byte(strconv.Itoa(updatedBalance)))

	log.Printf("minter account %s balance updated from %d to %d", minter, currentBalance, updatedBalance)

	return nil
}

// Transfer transfers tokens from client account to recipient account.
// recipient account must be a valid clientID as returned by the ClientID() function.
func (s *SmartContract) Transfer(ctx contractapi.TransactionContextInterface, recipient string, amount int) error {

	if amount <= 0 {
		return fmt.Errorf("transfer amount must be a positive integer")
	}

	// Get ID of submitting client identity
	clientID, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return fmt.Errorf("failed to get client id: %v", err)
	}

	if clientID == recipient {
		return fmt.Errorf("transfer recipient must be different")
	}

	clientCurrentBalanceBytes, err := ctx.GetStub().GetState(clientID)
	if err != nil {
		return fmt.Errorf("failed to read client account %s from world state: %v", clientID, err)
	}

	if clientCurrentBalanceBytes == nil {
		return fmt.Errorf("client account %s has no balance", clientID)
	}

	clientCurrentBalance, _ := strconv.Atoi(string(clientCurrentBalanceBytes))

	if clientCurrentBalance < amount {
		return fmt.Errorf("client account %s has insufficient funds", clientID)
	}

	recipientCurrentBalanceBytes, err := ctx.GetStub().GetState(recipient)
	if err != nil {
		fmt.Errorf("failed to read recipient account %s from world state: %v", recipient, err)
	}

	var recipientCurrentBalance int
	// If recipient current balance doesn't yet exist, we'll create it with a current balance of 0
	if recipientCurrentBalanceBytes == nil {
		recipientCurrentBalance = 0
	} else {
		recipientCurrentBalance, _ = strconv.Atoi(string(recipientCurrentBalanceBytes))
	}

	clientUpdatedBalance := clientCurrentBalance - amount
	recipientUpdatedBalance := recipientCurrentBalance + amount

	err = ctx.GetStub().PutState(clientID, []byte(strconv.Itoa(clientUpdatedBalance)))
	if err != nil {
		return err
	}

	err = ctx.GetStub().PutState(recipient, []byte(strconv.Itoa(recipientUpdatedBalance)))
	if err != nil {
		return err
	}

	log.Printf("client %s balance updated from %d to %d", clientID, clientCurrentBalance, clientUpdatedBalance)
	log.Printf("recipient %s balance updated from %d to %d", recipient, recipientCurrentBalance, recipientUpdatedBalance)

	return nil
}

// Allowance - Returns the amount of tokens approved by the owner that can be
// transfered to the spender's account
func (s *SmartContract) Allowance(ctx contractapi.TransactionContextInterface, owner string, spender string) (int, error) {

	approvalKey := owner + spender

	clientApprovedBalanceBytes, err := ctx.GetStub().GetState(approvalKey)
	if err != nil {
		return 0, fmt.Errorf("failed to read client %s approval of owner %s from world state: %v", spender, owner, err)
	}

	if clientApprovedBalanceBytes == nil {
		return 0, fmt.Errorf("spender approval account %s has no balance approved to withdraw", spender)
	}

	clientApprovedBalance, _ := strconv.Atoi(string(clientApprovedBalanceBytes))

	return clientApprovedBalance, nil
}

// Approve - Allow `spender` to withdraw from owner account, multiple times, up to the tokens `amount`.
// If this function is called again it overwrites the current allowance with `amount`.
func (s *SmartContract) Approve(ctx contractapi.TransactionContextInterface, spender string, amount int) error {

	if amount <= 0 {
		return fmt.Errorf("approval amount must be a positive integer")
	}

	// Get ID of submitting client identity
	clientID, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return fmt.Errorf("failed to get client id: %v", err)
	}

	approvalKey := clientID + spender
	err = ctx.GetStub().PutState(approvalKey, []byte(strconv.Itoa(amount)))
	if err != nil {
		return err
	}

	return nil

}

// TransferFrom - transfers tokens from client account to recipient account.
// recipient account must be a valid clientID as returned by the ClientID() function.
func (s *SmartContract) TransferFrom(ctx contractapi.TransactionContextInterface, sender string, recipient string, amount int) error {

	if amount <= 0 {
		return fmt.Errorf("transfer amount must be a positive integer")
	}

	// // Get ID of submitting client identity
	clientID, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return fmt.Errorf("failed to get client id: %v", err)
	}

	clientApprovedBalance, err := s.Allowance(ctx, sender, clientID)
	if err != nil {
		return err
	}

	if clientApprovedBalance < amount {
		return fmt.Errorf("client %s approved account has insufficient funds", clientID)
	}

	// get sender Balance
	senderCurrentBalanceBytes, err := ctx.GetStub().GetState(sender)
	if err != nil {
		return fmt.Errorf("failed to read client account %s from world state: %v", sender, err)
	}

	if senderCurrentBalanceBytes == nil {
		return fmt.Errorf("sender account %s has no balance", sender)
	}

	senderCurrentBalance, _ := strconv.Atoi(string(senderCurrentBalanceBytes))

	if senderCurrentBalance < amount {
		return fmt.Errorf("client account %s has insufficient funds", sender)
	}

	// get recipient Balance
	recipientCurrentBalanceBytes, err := ctx.GetStub().GetState(recipient)
	if err != nil {
		_ = fmt.Errorf("failed to read recipient account %s from world state: %v", recipient, err)
	}

	var recipientCurrentBalance int
	// If recipient current balance doesn't yet exist, we'll create it with a current balance of 0
	if recipientCurrentBalanceBytes == nil {
		recipientCurrentBalance = 0
	} else {
		recipientCurrentBalance, _ = strconv.Atoi(string(recipientCurrentBalanceBytes))
	}

	senderUpdatedBalance := senderCurrentBalance - amount
	clientApprovedUpdatedBalance := clientApprovedBalance - amount

	recipientUpdatedBalance := recipientCurrentBalance + amount
	err = ctx.GetStub().PutState(sender, []byte(strconv.Itoa(senderUpdatedBalance)))
	if err != nil {
		return err
	}

	approvalKey := sender + clientID
	err = ctx.GetStub().PutState(approvalKey, []byte(strconv.Itoa(clientApprovedUpdatedBalance)))
	if err != nil {
		return err
	}

	err = ctx.GetStub().PutState(recipient, []byte(strconv.Itoa(recipientUpdatedBalance)))
	if err != nil {
		return err
	}

	log.Printf("sender %s balance updated from %d to %d", sender, senderCurrentBalance, senderUpdatedBalance)
	log.Printf("client %s approval balance updated from %d to %d", clientID, clientApprovedBalance, clientApprovedUpdatedBalance)
	log.Printf("recipient %s balance updated from %d to %d", recipient, recipientCurrentBalance, recipientUpdatedBalance)

	return nil

}

// BalanceOf returns the balance of the given account
func (s *SmartContract) BalanceOf(ctx contractapi.TransactionContextInterface, account string) (int, error) {
	balanceBytes, err := ctx.GetStub().GetState(account)
	if err != nil {
		return 0, fmt.Errorf("failed to read from world state: %v", err)
	}
	if balanceBytes == nil {
		return 0, fmt.Errorf("the account %s does not exist", account)
	}

	balance, _ := strconv.Atoi(string(balanceBytes))

	return balance, nil
}

// ClientBalance returns the balance of the requesting client
func (s *SmartContract) ClientBalance(ctx contractapi.TransactionContextInterface) (int, error) {

	// Get ID of submitting client identity
	clientID, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return 0, fmt.Errorf("failed to get client id: %v", err)
	}

	balanceBytes, err := ctx.GetStub().GetState(clientID)
	if err != nil {
		return 0, fmt.Errorf("failed to read from world state: %v", err)
	}
	if balanceBytes == nil {
		return 0, fmt.Errorf("the account %s does not exist", clientID)
	}

	balance, _ := strconv.Atoi(string(balanceBytes))

	return balance, nil
}

// ClientID returns the client id of the transaction creator
// Users can use this function to get their own client id, which they can then give to others as the payment address
func (s *SmartContract) ClientID(ctx contractapi.TransactionContextInterface) (string, error) {

	// Get ID of submitting client identity
	clientID, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return "", fmt.Errorf("failed to get client id: %v", err)
	}

	return clientID, nil
}
