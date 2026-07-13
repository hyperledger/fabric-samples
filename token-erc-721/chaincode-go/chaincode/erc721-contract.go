package chaincode

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"

	"github.com/hyperledger/fabric-contract-api-go/v2/contractapi"
)

// Define objectType names for prefix
const balancePrefix = "balance"
const nftPrefix = "nft"
const approvalPrefix = "approval"

// Define key names for options
const nameKey = "name"
const symbolKey = "symbol"

// TokenERC721Contract contract for managing CRUD operations
type TokenERC721Contract struct {
	contractapi.Contract
}

func _readNFT(ctx contractapi.TransactionContextInterface, tokenID string) (*Nft, error) {
	nftKey, err := ctx.GetStub().CreateCompositeKey(nftPrefix, []string{tokenID})
	if err != nil {
		return nil, fmt.Errorf("failed to CreateCompositeKey %s: %w", tokenID, err)
	}

	nftBytes, err := ctx.GetStub().GetState(nftKey)
	if err != nil {
		return nil, fmt.Errorf("failed to GetState %s: %w", tokenID, err)
	}

	nft := new(Nft)
	err = json.Unmarshal(nftBytes, nft)
	if err != nil {
		return nil, fmt.Errorf("failed to Unmarshal nftBytes: %w", err)
	}

	return nft, nil
}

func _nftExists(ctx contractapi.TransactionContextInterface, tokenID string) bool {
	nftKey, err := ctx.GetStub().CreateCompositeKey(nftPrefix, []string{tokenID})
	if err != nil {
		panic("error creating CreateCompositeKey:" + err.Error())
	}

	nftBytes, err := ctx.GetStub().GetState(nftKey)
	if err != nil {
		panic("error GetState nftBytes:" + err.Error())
	}

	return len(nftBytes) > 0
}

// BalanceOf counts all non-fungible tokens assigned to an owner.
// owner is the address for whom to query the balance.
// Returns the number of non-fungible tokens owned by owner, possibly zero.
func (c *TokenERC721Contract) BalanceOf(ctx contractapi.TransactionContextInterface, owner string) int {

	// Check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		panic("failed to check if contract is already initialized:" + err.Error())
	}
	if !initialized {
		panic("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	// There is a key record for every non-fungible token in the format of balancePrefix.owner.tokenID.
	// BalanceOf() queries for and counts all records matching balancePrefix.owner.*

	iterator, err := ctx.GetStub().GetStateByPartialCompositeKey(balancePrefix, []string{owner})
	if err != nil {
		panic("Error creating asset chaincode:" + err.Error())
	}

	// Count the number of returned composite keys
	balance := 0
	for iterator.HasNext() {
		_, err := iterator.Next()
		if err != nil {
			return 0
		}
		balance++

	}
	return balance
}

// OwnerOf finds the owner of a non-fungible token.
// tokenID is the identifier for a non-fungible token.
// Returns the owner of the non-fungible token.
func (c *TokenERC721Contract) OwnerOf(ctx contractapi.TransactionContextInterface, tokenID string) (string, error) {

	// Check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return "", fmt.Errorf("failed to check if contract is already initialized: %w", err)
	}
	if !initialized {
		return "", errors.New("contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	nft, err := _readNFT(ctx, tokenID)
	if err != nil {
		return "", fmt.Errorf("could not process OwnerOf for tokenID: %w", err)
	}

	return nft.Owner, nil
}

// Approve changes or reaffirms the approved client for a non-fungible token.
// operator is the new approved client.
// tokenID is the non-fungible token to approve.
// Returns true if the approval was successful.
func (c *TokenERC721Contract) Approve(ctx contractapi.TransactionContextInterface, operator string, tokenID string) (bool, error) {

	// Check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return false, fmt.Errorf("failed to check if contract is already initialized: %w", err)
	}
	if !initialized {
		return false, errors.New("contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	sender64, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return false, fmt.Errorf("failed to GetClientIdentity: %w", err)
	}

	senderBytes, err := base64.StdEncoding.DecodeString(sender64)
	if err != nil {
		return false, fmt.Errorf("failed to DecodeString senderBytes: %w", err)
	}
	sender := string(senderBytes)

	nft, err := _readNFT(ctx, tokenID)
	if err != nil {
		return false, fmt.Errorf("failed to _readNFT: %w", err)
	}

	// Check if the sender is the current owner of the non-fungible token
	// or an authorized operator of the current owner
	owner := nft.Owner
	operatorApproval, err := c.IsApprovedForAll(ctx, owner, sender)
	if err != nil {
		return false, fmt.Errorf("failed to get IsApprovedForAll: %w", err)
	}
	if owner != sender && !operatorApproval {
		return false, errors.New("the sender is not the current owner nor an authorized operator")
	}

	// Update the approved operator of the non-fungible token
	nft.Approved = operator
	nftKey, err := ctx.GetStub().CreateCompositeKey(nftPrefix, []string{tokenID})
	if err != nil {
		return false, fmt.Errorf("failed to CreateCompositeKey %s: %w", nftKey, err)
	}

	nftBytes, err := json.Marshal(nft)
	if err != nil {
		return false, fmt.Errorf("failed to marshal nftBytes: %w", err)
	}

	err = ctx.GetStub().PutState(nftKey, nftBytes)
	if err != nil {
		return false, fmt.Errorf("failed to PutState for nftKey: %w", err)
	}

	return true, nil
}

// SetApprovalForAll enables or disables approval for a third party ("operator")
// to manage all the message sender's assets.
// operator is a client to add to the set of authorized operators.
// approved is true if the operator is approved, false to revoke approval.
// Returns true if the approval was successful.
func (c *TokenERC721Contract) SetApprovalForAll(ctx contractapi.TransactionContextInterface, operator string, approved bool) (bool, error) {

	// Check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return false, fmt.Errorf("failed to check if contract is already initialized: %w", err)
	}
	if !initialized {
		return false, errors.New("contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	sender64, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return false, fmt.Errorf("failed to GetClientIdentity: %w", err)
	}

	senderBytes, err := base64.StdEncoding.DecodeString(sender64)
	if err != nil {
		return false, fmt.Errorf("failed to DecodeString sender: %w", err)
	}
	sender := string(senderBytes)

	nftApproval := new(Approval)
	nftApproval.Owner = sender
	nftApproval.Operator = operator
	nftApproval.Approved = approved

	approvalKey, err := ctx.GetStub().CreateCompositeKey(approvalPrefix, []string{sender, operator})
	if err != nil {
		return false, fmt.Errorf("failed to CreateCompositeKey: %w", err)
	}

	approvalBytes, err := json.Marshal(nftApproval)
	if err != nil {
		return false, fmt.Errorf("failed to marshal approvalBytes: %w", err)
	}

	err = ctx.GetStub().PutState(approvalKey, approvalBytes)
	if err != nil {
		return false, fmt.Errorf("failed to PutState approvalBytes: %w", err)
	}

	// Emit the ApprovalForAll event
	err = ctx.GetStub().SetEvent("ApprovalForAll", approvalBytes)
	if err != nil {
		return false, fmt.Errorf("failed to SetEvent ApprovalForAll: %w", err)
	}

	return true, nil
}

// IsApprovedForAll returns if a client is an authorized operator for another client.
// owner is the client that owns the non-fungible tokens.
// operator is the client that acts on behalf of the owner.
// Returns true if operator is an approved operator for owner, false otherwise.
func (c *TokenERC721Contract) IsApprovedForAll(ctx contractapi.TransactionContextInterface, owner string, operator string) (bool, error) {

	// Check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return false, fmt.Errorf("failed to check if contract is already initialized: %w", err)
	}
	if !initialized {
		return false, errors.New("contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	approvalKey, err := ctx.GetStub().CreateCompositeKey(approvalPrefix, []string{owner, operator})
	if err != nil {
		return false, fmt.Errorf("failed to CreateCompositeKey: %w", err)
	}
	approvalBytes, err := ctx.GetStub().GetState(approvalKey)
	if err != nil {
		return false, fmt.Errorf("failed to GetState approvalBytes %s: %w", approvalBytes, err)
	}

	if len(approvalBytes) < 1 {
		return false, nil
	}

	approval := new(Approval)
	err = json.Unmarshal(approvalBytes, approval)
	if err != nil {
		return false, fmt.Errorf("failed to Unmarshal: %w, string %s", err, string(approvalBytes))
	}

	return approval.Approved, nil

}

// GetApproved returns the approved client for a single non-fungible token.
// tokenID is the non-fungible token to find the approved client for.
// Returns the approved client for this non-fungible token, or an empty string if there is none.
func (c *TokenERC721Contract) GetApproved(ctx contractapi.TransactionContextInterface, tokenID string) (string, error) {

	// Check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return "false", fmt.Errorf("failed to check if contract is already initialized: %w", err)
	}
	if !initialized {
		return "false", errors.New("contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	nft, err := _readNFT(ctx, tokenID)
	if err != nil {
		return "false", fmt.Errorf("failed GetApproved for tokenID : %w", err)
	}
	return nft.Approved, nil
}

// TransferFrom transfers the ownership of a non-fungible token from one owner to another owner.
// from is the current owner of the non-fungible token.
// to is the new owner.
// tokenID is the non-fungible token to transfer.
// Returns true if the transfer was successful.
func (c *TokenERC721Contract) TransferFrom(ctx contractapi.TransactionContextInterface, from string, to string, tokenID string) (bool, error) {

	// Check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return false, fmt.Errorf("failed to check if contract is already initialized: %w", err)
	}
	if !initialized {
		return false, errors.New("contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	// Get ID of submitting client identity
	sender64, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return false, fmt.Errorf("failed to GetClientIdentity: %w", err)
	}

	senderBytes, err := base64.StdEncoding.DecodeString(sender64)
	if err != nil {
		return false, fmt.Errorf("failed to DecodeString sender: %w", err)
	}
	sender := string(senderBytes)

	nft, err := _readNFT(ctx, tokenID)
	if err != nil {
		return false, fmt.Errorf("failed to _readNFT : %w", err)
	}

	owner := nft.Owner
	operator := nft.Approved
	operatorApproval, err := c.IsApprovedForAll(ctx, owner, sender)
	if err != nil {
		return false, fmt.Errorf("failed to get IsApprovedForAll : %w", err)
	}
	if owner != sender && operator != sender && !operatorApproval {
		return false, errors.New("the sender is not the current owner nor an authorized operator")
	}

	// Check if `from` is the current owner
	if owner != from {
		return false, errors.New("the from is not the current owner")
	}

	// Clear the approved client for this non-fungible token
	nft.Approved = ""

	// Overwrite a non-fungible token to assign a new owner.
	nft.Owner = to
	nftKey, err := ctx.GetStub().CreateCompositeKey(nftPrefix, []string{tokenID})
	if err != nil {
		return false, fmt.Errorf("failed to CreateCompositeKey: %w", err)
	}

	nftBytes, err := json.Marshal(nft)
	if err != nil {
		return false, fmt.Errorf("failed to marshal approval: %w", err)
	}

	err = ctx.GetStub().PutState(nftKey, nftBytes)
	if err != nil {
		return false, fmt.Errorf("failed to PutState nftBytes %s: %w", nftBytes, err)
	}

	// Remove a composite key from the balance of the current owner
	balanceKeyFrom, err := ctx.GetStub().CreateCompositeKey(balancePrefix, []string{from, tokenID})
	if err != nil {
		return false, fmt.Errorf("failed to CreateCompositeKey from: %w", err)
	}

	err = ctx.GetStub().DelState(balanceKeyFrom)
	if err != nil {
		return false, fmt.Errorf("failed to DelState balanceKeyFrom %s: %w", nftBytes, err)
	}

	// Save a composite key to count the balance of a new owner
	balanceKeyTo, err := ctx.GetStub().CreateCompositeKey(balancePrefix, []string{to, tokenID})
	if err != nil {
		return false, fmt.Errorf("failed to CreateCompositeKey to: %w", err)
	}
	err = ctx.GetStub().PutState(balanceKeyTo, []byte{0})
	if err != nil {
		return false, fmt.Errorf("failed to PutState balanceKeyTo %s: %w", balanceKeyTo, err)
	}

	// Emit the Transfer event
	transferEvent := new(Transfer)
	transferEvent.From = from
	transferEvent.To = to
	transferEvent.TokenID = tokenID

	transferEventBytes, err := json.Marshal(transferEvent)
	if err != nil {
		return false, fmt.Errorf("failed to marshal transferEventBytes: %w", err)
	}

	err = ctx.GetStub().SetEvent("Transfer", transferEventBytes)
	if err != nil {
		return false, fmt.Errorf("failed to SetEvent transferEventBytes %s: %w", transferEventBytes, err)
	}
	return true, nil
}

// ============== ERC721 metadata extension ===============

// Name returns a descriptive name for a collection of non-fungible tokens in this contract.
func (c *TokenERC721Contract) Name(ctx contractapi.TransactionContextInterface) (string, error) {

	// Check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return "", fmt.Errorf("failed to check if contract is already initialized: %w", err)
	}
	if !initialized {
		return "", errors.New("contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	bytes, err := ctx.GetStub().GetState(nameKey)
	if err != nil {
		return "", fmt.Errorf("failed to get Name bytes: %w", err)
	}

	return string(bytes), nil
}

// Symbol returns an abbreviated name for non-fungible tokens in this contract.
func (c *TokenERC721Contract) Symbol(ctx contractapi.TransactionContextInterface) (string, error) {

	// Check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return "", fmt.Errorf("failed to check if contract is already initialized: %w", err)
	}
	if !initialized {
		return "", errors.New("contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	bytes, err := ctx.GetStub().GetState(symbolKey)
	if err != nil {
		return "", fmt.Errorf("failed to get Symbol: %w", err)
	}

	return string(bytes), nil
}

// TokenURI returns a distinct Uniform Resource Identifier (URI) for a given token.
// tokenID is the identifier for the non-fungible token.
func (c *TokenERC721Contract) TokenURI(ctx contractapi.TransactionContextInterface, tokenID string) (string, error) {

	// Check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return "", fmt.Errorf("failed to check if contract is already initialized: %w", err)
	}
	if !initialized {
		return "", errors.New("contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	nft, err := _readNFT(ctx, tokenID)
	if err != nil {
		return "", fmt.Errorf("failed to get TokenURI: %w", err)
	}
	return nft.TokenURI, nil
}

// ============== ERC721 enumeration extension ===============

// TotalSupply counts non-fungible tokens tracked by this contract.
// Returns a count of valid non-fungible tokens tracked by this contract,
// where each one has an assigned and queryable owner.
func (c *TokenERC721Contract) TotalSupply(ctx contractapi.TransactionContextInterface) int {

	// Check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		panic("failed to check if contract is already initialized:" + err.Error())
	}
	if !initialized {
		panic("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	// There is a key record for every non-fungible token in the format of nftPrefix.tokenID.
	// TotalSupply() queries for and counts all records matching nftPrefix.*

	iterator, err := ctx.GetStub().GetStateByPartialCompositeKey(nftPrefix, []string{})
	if err != nil {
		panic("Error creating GetStateByPartialCompositeKey:" + err.Error())
	}
	// Count the number of returned composite keys

	totalSupply := 0
	for iterator.HasNext() {
		_, err := iterator.Next()
		if err != nil {
			return 0
		}
		totalSupply++

	}
	return totalSupply

}

// ============== ERC721 enumeration extension ===============

// Initialize sets information for a token and initializes the contract.
// name is the name of the token.
// symbol is the symbol of the token.
func (c *TokenERC721Contract) Initialize(ctx contractapi.TransactionContextInterface, name string, symbol string) (bool, error) {
	// Check minter authorization - this sample assumes Org1 is the issuer with privilege to set the name and symbol
	clientMSPID, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return false, fmt.Errorf("failed to get clientMSPID: %w", err)
	}
	if clientMSPID != "Org1MSP" {
		return false, errors.New("client is not authorized to set the name and symbol of the token")
	}

	bytes, err := ctx.GetStub().GetState(nameKey)
	if err != nil {
		return false, fmt.Errorf("failed to get Name: %w", err)
	}
	if bytes != nil {
		return false, errors.New("contract options are already set, client is not authorized to change them")
	}

	err = ctx.GetStub().PutState(nameKey, []byte(name))
	if err != nil {
		return false, fmt.Errorf("failed to PutState nameKey %s: %w", nameKey, err)
	}

	err = ctx.GetStub().PutState(symbolKey, []byte(symbol))
	if err != nil {
		return false, fmt.Errorf("failed to PutState symbolKey %s: %w", symbolKey, err)
	}

	return true, nil
}

// MintWithTokenURI creates a new non-fungible token.
// tokenID is the unique ID of the non-fungible token to be minted.
// tokenURI is the URI containing metadata of the minted non-fungible token.
// Returns the minted non-fungible token.
func (c *TokenERC721Contract) MintWithTokenURI(ctx contractapi.TransactionContextInterface, tokenID string, tokenURI string) (*Nft, error) {

	// Check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to check if contract is already initialized: %w", err)
	}
	if !initialized {
		return nil, errors.New("contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	// Check minter authorization - this sample assumes Org1 is the issuer with privilege to mint a new token
	clientMSPID, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return nil, fmt.Errorf("failed to get clientMSPID: %w", err)
	}

	if clientMSPID != "Org1MSP" {
		return nil, errors.New("client is not authorized to set the name and symbol of the token")
	}

	// Get ID of submitting client identity
	minter64, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return nil, fmt.Errorf("failed to get minter id: %w", err)
	}

	minterBytes, err := base64.StdEncoding.DecodeString(minter64)
	if err != nil {
		return nil, fmt.Errorf("failed to DecodeString minter64: %w", err)
	}
	minter := string(minterBytes)

	// Check if the token to be minted does not exist
	exists := _nftExists(ctx, tokenID)
	if exists {
		return nil, fmt.Errorf("the token %s is already minted.: %w", tokenID, err)
	}

	// Add a non-fungible token
	nft := new(Nft)
	nft.TokenID = tokenID
	nft.Owner = minter
	nft.TokenURI = tokenURI

	nftKey, err := ctx.GetStub().CreateCompositeKey(nftPrefix, []string{tokenID})
	if err != nil {
		return nil, fmt.Errorf("failed to CreateCompositeKey to nftKey: %w", err)
	}

	nftBytes, err := json.Marshal(nft)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal nft: %w", err)
	}

	err = ctx.GetStub().PutState(nftKey, nftBytes)
	if err != nil {
		return nil, fmt.Errorf("failed to PutState nftBytes %s: %w", nftBytes, err)
	}

	// A composite key would be balancePrefix.owner.tokenID, which enables partial
	// composite key query to find and count all records matching balance.owner.*
	// An empty value would represent a delete, so we simply insert the null character.

	balanceKey, err := ctx.GetStub().CreateCompositeKey(balancePrefix, []string{minter, tokenID})
	if err != nil {
		return nil, fmt.Errorf("failed to CreateCompositeKey to balanceKey: %w", err)
	}

	err = ctx.GetStub().PutState(balanceKey, []byte{'\u0000'})
	if err != nil {
		return nil, fmt.Errorf("failed to PutState balanceKey %s: %w", nftBytes, err)
	}

	// Emit the Transfer event
	transferEvent := new(Transfer)
	transferEvent.From = "0x0"
	transferEvent.To = minter
	transferEvent.TokenID = tokenID

	transferEventBytes, err := json.Marshal(transferEvent)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal transferEventBytes: %w", err)
	}

	err = ctx.GetStub().SetEvent("Transfer", transferEventBytes)
	if err != nil {
		return nil, fmt.Errorf("failed to SetEvent transferEventBytes %s: %w", transferEventBytes, err)
	}

	return nft, nil
}

// Burn destroys a non-fungible token.
// tokenID is the unique ID of the non-fungible token to burn.
// Returns true if the burn was successful.
func (c *TokenERC721Contract) Burn(ctx contractapi.TransactionContextInterface, tokenID string) (bool, error) {

	// Check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return false, fmt.Errorf("failed to check if contract is already initialized: %w", err)
	}
	if !initialized {
		return false, errors.New("contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	owner64, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return false, fmt.Errorf("failed to GetClientIdentity owner64: %w", err)
	}

	ownerBytes, err := base64.StdEncoding.DecodeString(owner64)
	if err != nil {
		return false, fmt.Errorf("failed to DecodeString owner64: %w", err)
	}
	owner := string(ownerBytes)

	// Check if a caller is the owner of the non-fungible token
	nft, err := _readNFT(ctx, tokenID)
	if err != nil {
		return false, fmt.Errorf("failed to _readNFT nft : %w", err)
	}
	if nft.Owner != owner {
		return false, fmt.Errorf("non-fungible token %s is not owned by %s", tokenID, owner)
	}

	// Delete the token
	nftKey, err := ctx.GetStub().CreateCompositeKey(nftPrefix, []string{tokenID})
	if err != nil {
		return false, fmt.Errorf("failed to CreateCompositeKey tokenID: %w", err)
	}

	err = ctx.GetStub().DelState(nftKey)
	if err != nil {
		return false, fmt.Errorf("failed to DelState nftKey: %w", err)
	}

	// Remove a composite key from the balance of the owner
	balanceKey, err := ctx.GetStub().CreateCompositeKey(balancePrefix, []string{owner, tokenID})
	if err != nil {
		return false, fmt.Errorf("failed to CreateCompositeKey balanceKey %s: %w", balanceKey, err)
	}

	err = ctx.GetStub().DelState(balanceKey)
	if err != nil {
		return false, fmt.Errorf("failed to DelState balanceKey %s: %w", balanceKey, err)
	}

	// Emit the Transfer event
	transferEvent := new(Transfer)
	transferEvent.From = owner
	transferEvent.To = "0x0"
	transferEvent.TokenID = tokenID

	transferEventBytes, err := json.Marshal(transferEvent)
	if err != nil {
		return false, fmt.Errorf("failed to marshal transferEventBytes: %w", err)
	}

	err = ctx.GetStub().SetEvent("Transfer", transferEventBytes)
	if err != nil {
		return false, fmt.Errorf("failed to SetEvent transferEventBytes: %w", err)
	}

	return true, nil
}

// ClientAccountBalance returns the balance of the requesting client's account.
func (c *TokenERC721Contract) ClientAccountBalance(ctx contractapi.TransactionContextInterface) (int, error) {

	// Check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return 0, fmt.Errorf("failed to check if contract is already initialized: %w", err)
	}
	if !initialized {
		return 0, errors.New("contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	// Get ID of submitting client identity
	clientAccountID64, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return 0, fmt.Errorf("failed to GetClientIdentity minter: %w", err)
	}

	clientAccountIDBytes, err := base64.StdEncoding.DecodeString(clientAccountID64)
	if err != nil {
		return 0, fmt.Errorf("failed to DecodeString sender: %w", err)
	}

	clientAccountID := string(clientAccountIDBytes)

	return c.BalanceOf(ctx, clientAccountID), nil
}

// ClientAccountID returns the id of the requesting client's account.
// In this implementation, the client account ID is the clientId itself.
// Users can use this function to get their own account id, which they can then give to others as the payment address
func (c *TokenERC721Contract) ClientAccountID(ctx contractapi.TransactionContextInterface) (string, error) {

	// Check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return "", fmt.Errorf("failed to check if contract is already initialized: %w", err)
	}
	if !initialized {
		return "", errors.New("contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	// Get ID of submitting client identity
	clientAccountID64, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return "", fmt.Errorf("failed to GetClientIdentity minter: %w", err)
	}

	clientAccountBytes, err := base64.StdEncoding.DecodeString(clientAccountID64)
	if err != nil {
		return "", fmt.Errorf("failed to DecodeString clientAccount64: %w", err)
	}
	clientAccount := string(clientAccountBytes)

	return clientAccount, nil
}

// Checks that contract options have been already initialized
func checkInitialized(ctx contractapi.TransactionContextInterface) (bool, error) {
	tokenName, err := ctx.GetStub().GetState(nameKey)
	if err != nil {
		return false, fmt.Errorf("failed to get token name: %w", err)
	}
	if tokenName == nil {
		return false, nil
	}
	return true, nil
}
