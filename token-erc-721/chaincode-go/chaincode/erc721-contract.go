package chaincode

import (
	"encoding/base64"
	"encoding/json"
	"fmt"

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
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

func _readNFT(ctx contractapi.TransactionContextInterface, tokenId string) (*Nft, error) {
	nftKey, err := ctx.GetStub().CreateCompositeKey(nftPrefix, []string{tokenId})
	if err != nil {
		return nil, fmt.Errorf("failed to CreateCompositeKey %s: %v", tokenId, err)
	}

	nftBytes, err := ctx.GetStub().GetState(nftKey)
	if err != nil {
		return nil, fmt.Errorf("failed to GetState %s: %v", tokenId, err)
	}

	nft := new(Nft)
	err = json.Unmarshal(nftBytes, nft)
	if err != nil {
		return nil, fmt.Errorf("failed to Unmarshal nftBytes: %v", err)
	}

	return nft, nil
}

func _nftExists(ctx contractapi.TransactionContextInterface, tokenId string) bool {
	nftKey, err := ctx.GetStub().CreateCompositeKey(nftPrefix, []string{tokenId})
	if err != nil {
		panic("error creating CreateCompositeKey:" + err.Error())
	}

	nftBytes, err := ctx.GetStub().GetState(nftKey)
	if err != nil {
		panic("error GetState nftBytes:" + err.Error())
	}

	return len(nftBytes) > 0
}

// BalanceOf counts all non-fungible tokens assigned to an owner
// param owner {String} An owner for whom to query the balance
// returns {int} The number of non-fungible tokens owned by the owner, possibly zero
func (c *TokenERC721Contract) BalanceOf(ctx contractapi.TransactionContextInterface, owner string) int {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		panic("failed to check if contract ia already initialized:" + err.Error())
	}
	if !initialized {
		panic("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	// There is a key record for every non-fungible token in the format of balancePrefix.owner.tokenId.
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

// OwnerOf finds the owner of a non-fungible token
// param {String} tokenId The identifier for a non-fungible token
// returns {String} Return the owner of the non-fungible token
func (c *TokenERC721Contract) OwnerOf(ctx contractapi.TransactionContextInterface, tokenId string) (string, error) {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return "", fmt.Errorf("failed to check if contract ia already initialized: %v", err)
	}
	if !initialized {
		return "", fmt.Errorf("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	nft, err := _readNFT(ctx, tokenId)
	if err != nil {
		return "", fmt.Errorf("could not process OwnerOf for tokenId: %w", err)
	}

	return nft.Owner, nil
}

// Approve changes or reaffirms the approved client for a non-fungible token
// param {String} operator The new approved client
// param {String} tokenId the non-fungible token to approve
// returns {Boolean} Return whether the approval was successful or not
func (c *TokenERC721Contract) Approve(ctx contractapi.TransactionContextInterface, operator string, tokenId string) (bool, error) {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return false, fmt.Errorf("failed to check if contract ia already initialized: %v", err)
	}
	if !initialized {
		return false, fmt.Errorf("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	sender64, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return false, fmt.Errorf("failed to GetClientIdentity: %v", err)
	}

	senderBytes, err := base64.StdEncoding.DecodeString(sender64)
	if err != nil {
		return false, fmt.Errorf("failed to DecodeString senderBytes: %v", err)
	}
	sender := string(senderBytes)

	nft, err := _readNFT(ctx, tokenId)
	if err != nil {
		return false, fmt.Errorf("failed to _readNFT: %v", err)
	}

	// Check if the sender is the current owner of the non-fungible token
	// or an authorized operator of the current owner
	owner := nft.Owner
	operatorApproval, err := c.IsApprovedForAll(ctx, owner, sender)
	if err != nil {
		return false, fmt.Errorf("failed to get IsApprovedForAll: %v", err)
	}
	if owner != sender && !operatorApproval {
		return false, fmt.Errorf("the sender is not the current owner nor an authorized operator")
	}

	// Update the approved operator of the non-fungible token
	nft.Approved = operator
	nftKey, err := ctx.GetStub().CreateCompositeKey(nftPrefix, []string{tokenId})
	if err != nil {
		return false, fmt.Errorf("failed to CreateCompositeKey %s: %v", nftKey, err)
	}

	nftBytes, err := json.Marshal(nft)
	if err != nil {
		return false, fmt.Errorf("failed to marshal nftBytes: %v", err)
	}

	err = ctx.GetStub().PutState(nftKey, nftBytes)
	if err != nil {
		return false, fmt.Errorf("failed to PutState for nftKey: %v", err)
	}

	return true, nil
}

// SetApprovalForAll enables or disables approval for a third party ("operator")
// to manage all the message sender's assets
// param {String} operator A client to add to the set of authorized operators
// param {Boolean} approved True if the operator is approved, false to revoke approval
// returns {Boolean} Return whether the approval was successful or not
func (c *TokenERC721Contract) SetApprovalForAll(ctx contractapi.TransactionContextInterface, operator string, approved bool) (bool, error) {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return false, fmt.Errorf("failed to check if contract ia already initialized: %v", err)
	}
	if !initialized {
		return false, fmt.Errorf("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	sender64, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return false, fmt.Errorf("failed to GetClientIdentity: %v", err)
	}

	senderBytes, err := base64.StdEncoding.DecodeString(sender64)
	if err != nil {
		return false, fmt.Errorf("failed to DecodeString sender: %v", err)
	}
	sender := string(senderBytes)

	nftApproval := new(Approval)
	nftApproval.Owner = sender
	nftApproval.Operator = operator
	nftApproval.Approved = approved

	approvalKey, err := ctx.GetStub().CreateCompositeKey(approvalPrefix, []string{sender, operator})
	if err != nil {
		return false, fmt.Errorf("failed to CreateCompositeKey: %v", err)
	}

	approvalBytes, err := json.Marshal(nftApproval)
	if err != nil {
		return false, fmt.Errorf("failed to marshal approvalBytes: %v", err)
	}

	err = ctx.GetStub().PutState(approvalKey, approvalBytes)
	if err != nil {
		return false, fmt.Errorf("failed to PutState approvalBytes: %v", err)
	}

	// Emit the ApprovalForAll event
	err = ctx.GetStub().SetEvent("ApprovalForAll", approvalBytes)
	if err != nil {
		return false, fmt.Errorf("failed to SetEvent ApprovalForAll: %v", err)
	}

	return true, nil
}

// IsApprovedForAll returns if a client is an authorized operator for another client
// param {String} owner The client that owns the non-fungible tokens
// param {String} operator The client that acts on behalf of the owner
// returns {Boolean} Return true if the operator is an approved operator for the owner, false otherwise
func (c *TokenERC721Contract) IsApprovedForAll(ctx contractapi.TransactionContextInterface, owner string, operator string) (bool, error) {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return false, fmt.Errorf("failed to check if contract ia already initialized: %v", err)
	}
	if !initialized {
		return false, fmt.Errorf("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	approvalKey, err := ctx.GetStub().CreateCompositeKey(approvalPrefix, []string{owner, operator})
	if err != nil {
		return false, fmt.Errorf("failed to CreateCompositeKey: %v", err)
	}
	approvalBytes, err := ctx.GetStub().GetState(approvalKey)
	if err != nil {
		return false, fmt.Errorf("failed to GetState approvalBytes %s: %v", approvalBytes, err)
	}

	if len(approvalBytes) < 1 {
		return false, nil
	}

	approval := new(Approval)
	err = json.Unmarshal(approvalBytes, approval)
	if err != nil {
		return false, fmt.Errorf("failed to Unmarshal: %v, string %s", err, string(approvalBytes))
	}

	return approval.Approved, nil

}

// GetApproved returns the approved client for a single non-fungible token
// param {String} tokenId the non-fungible token to find the approved client for
// returns {Object} Return the approved client for this non-fungible token, or null if there is none
func (c *TokenERC721Contract) GetApproved(ctx contractapi.TransactionContextInterface, tokenId string) (string, error) {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return "false", fmt.Errorf("failed to check if contract ia already initialized: %v", err)
	}
	if !initialized {
		return "false", fmt.Errorf("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	nft, err := _readNFT(ctx, tokenId)
	if err != nil {
		return "false", fmt.Errorf("failed GetApproved for tokenId : %v", err)
	}
	return nft.Approved, nil
}

// TransferFrom transfers the ownership of a non-fungible token
// from one owner to another owner
// param {String} from The current owner of the non-fungible token
// param {String} to The new owner
// param {String} tokenId the non-fungible token to transfer
// returns {Boolean} Return whether the transfer was successful or not

func (c *TokenERC721Contract) TransferFrom(ctx contractapi.TransactionContextInterface, from string, to string, tokenId string) (bool, error) {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return false, fmt.Errorf("failed to check if contract ia already initialized: %v", err)
	}
	if !initialized {
		return false, fmt.Errorf("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	// Get ID of submitting client identity
	sender64, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return false, fmt.Errorf("failed to GetClientIdentity: %v", err)
	}

	senderBytes, err := base64.StdEncoding.DecodeString(sender64)
	if err != nil {
		return false, fmt.Errorf("failed to DecodeString sender: %v", err)
	}
	sender := string(senderBytes)

	nft, err := _readNFT(ctx, tokenId)
	if err != nil {
		return false, fmt.Errorf("failed to _readNFT : %v", err)
	}

	owner := nft.Owner
	operator := nft.Approved
	operatorApproval, err := c.IsApprovedForAll(ctx, owner, sender)
	if err != nil {
		return false, fmt.Errorf("failed to get IsApprovedForAll : %v", err)
	}
	if owner != sender && operator != sender && !operatorApproval {
		return false, fmt.Errorf("the sender is not the current owner nor an authorized operator")
	}

	// Check if `from` is the current owner
	if owner != from {
		return false, fmt.Errorf("the from is not the current owner")
	}

	// Clear the approved client for this non-fungible token
	nft.Approved = ""

	// Overwrite a non-fungible token to assign a new owner.
	nft.Owner = to
	nftKey, err := ctx.GetStub().CreateCompositeKey(nftPrefix, []string{tokenId})
	if err != nil {
		return false, fmt.Errorf("failed to CreateCompositeKey: %v", err)
	}

	nftBytes, err := json.Marshal(nft)
	if err != nil {
		return false, fmt.Errorf("failed to marshal approval: %v", err)
	}

	err = ctx.GetStub().PutState(nftKey, nftBytes)
	if err != nil {
		return false, fmt.Errorf("failed to PutState nftBytes %s: %v", nftBytes, err)
	}

	// Remove a composite key from the balance of the current owner
	balanceKeyFrom, err := ctx.GetStub().CreateCompositeKey(balancePrefix, []string{from, tokenId})
	if err != nil {
		return false, fmt.Errorf("failed to CreateCompositeKey from: %v", err)
	}

	err = ctx.GetStub().DelState(balanceKeyFrom)
	if err != nil {
		return false, fmt.Errorf("failed to DelState balanceKeyFrom %s: %v", nftBytes, err)
	}

	// Save a composite key to count the balance of a new owner
	balanceKeyTo, err := ctx.GetStub().CreateCompositeKey(balancePrefix, []string{to, tokenId})
	if err != nil {
		return false, fmt.Errorf("failed to CreateCompositeKey to: %v", err)
	}
	err = ctx.GetStub().PutState(balanceKeyTo, []byte{0})
	if err != nil {
		return false, fmt.Errorf("failed to PutState balanceKeyTo %s: %v", balanceKeyTo, err)
	}

	// Emit the Transfer event
	transferEvent := new(Transfer)
	transferEvent.From = from
	transferEvent.To = to
	transferEvent.TokenId = tokenId

	transferEventBytes, err := json.Marshal(transferEvent)
	if err != nil {
		return false, fmt.Errorf("failed to marshal transferEventBytes: %v", err)
	}

	err = ctx.GetStub().SetEvent("Transfer", transferEventBytes)
	if err != nil {
		return false, fmt.Errorf("failed to SetEvent transferEventBytes %s: %v", transferEventBytes, err)
	}
	return true, nil
}

// ============== ERC721 metadata extension ===============

// Name returns a descriptive name for a collection of non-fungible tokens in this contract
// returns {String} Returns the name of the token

func (c *TokenERC721Contract) Name(ctx contractapi.TransactionContextInterface) (string, error) {

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

// Symbol returns an abbreviated name for non-fungible tokens in this contract.
// returns {String} Returns the symbol of the token

func (c *TokenERC721Contract) Symbol(ctx contractapi.TransactionContextInterface) (string, error) {

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

// TokenURI returns a distinct Uniform Resource Identifier (URI) for a given token.
// param {string} tokenId The identifier for a non-fungible token
// returns {String} Returns the URI of the token

func (c *TokenERC721Contract) TokenURI(ctx contractapi.TransactionContextInterface, tokenId string) (string, error) {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return "", fmt.Errorf("failed to check if contract ia already initialized: %v", err)
	}
	if !initialized {
		return "", fmt.Errorf("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	nft, err := _readNFT(ctx, tokenId)
	if err != nil {
		return "", fmt.Errorf("failed to get TokenURI: %v", err)
	}
	return nft.TokenURI, nil
}

// ============== ERC721 enumeration extension ===============
// TotalSupply counts non-fungible tokens tracked by this contract.
//
// @param {Context} ctx the transaction context
// @returns {Number} Returns a count of valid non-fungible tokens tracked by this contract,
// where each one of them has an assigned and queryable owner.

func (c *TokenERC721Contract) TotalSupply(ctx contractapi.TransactionContextInterface) int {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		panic("failed to check if contract ia already initialized:" + err.Error())
	}
	if !initialized {
		panic("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	// There is a key record for every non-fungible token in the format of nftPrefix.tokenId.
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
// Set information for a token and intialize contract.
// param {String} name The name of the token
// param {String} symbol The symbol of the token

func (c *TokenERC721Contract) Initialize(ctx contractapi.TransactionContextInterface, name string, symbol string) (bool, error) {
	// Check minter authorization - this sample assumes Org1 is the issuer with privilege to set the name and symbol
	clientMSPID, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return false, fmt.Errorf("failed to get clientMSPID: %v", err)
	}
	if clientMSPID != "Org1MSP" {
		return false, fmt.Errorf("client is not authorized to set the name and symbol of the token")
	}

	bytes, err := ctx.GetStub().GetState(nameKey)
	if err != nil {
		return false, fmt.Errorf("failed to get Name: %v", err)
	}
	if bytes != nil {
		return false, fmt.Errorf("contract options are already set, client is not authorized to change them")
	}

	err = ctx.GetStub().PutState(nameKey, []byte(name))
	if err != nil {
		return false, fmt.Errorf("failed to PutState nameKey %s: %v", nameKey, err)
	}

	err = ctx.GetStub().PutState(symbolKey, []byte(symbol))
	if err != nil {
		return false, fmt.Errorf("failed to PutState symbolKey %s: %v", symbolKey, err)
	}

	return true, nil
}

// Mint a new non-fungible token
// param {String} tokenId Unique ID of the non-fungible token to be minted
// param {String} tokenURI URI containing metadata of the minted non-fungible token
// returns {Object} Return the non-fungible token object

func (c *TokenERC721Contract) MintWithTokenURI(ctx contractapi.TransactionContextInterface, tokenId string, tokenURI string) (*Nft, error) {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return nil, fmt.Errorf("failed to check if contract ia already initialized: %v", err)
	}
	if !initialized {
		return nil, fmt.Errorf("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	// Check minter authorization - this sample assumes Org1 is the issuer with privilege to mint a new token
	clientMSPID, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return nil, fmt.Errorf("failed to get clientMSPID: %v", err)
	}

	if clientMSPID != "Org1MSP" {
		return nil, fmt.Errorf("client is not authorized to set the name and symbol of the token")
	}

	// Get ID of submitting client identity
	minter64, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return nil, fmt.Errorf("failed to get minter id: %v", err)
	}

	minterBytes, err := base64.StdEncoding.DecodeString(minter64)
	if err != nil {
		return nil, fmt.Errorf("failed to DecodeString minter64: %v", err)
	}
	minter := string(minterBytes)

	// Check if the token to be minted does not exist
	exists := _nftExists(ctx, tokenId)
	if exists {
		return nil, fmt.Errorf("the token %s is already minted.: %v", tokenId, err)
	}

	// Add a non-fungible token
	nft := new(Nft)
	nft.TokenId = tokenId
	nft.Owner = minter
	nft.TokenURI = tokenURI

	nftKey, err := ctx.GetStub().CreateCompositeKey(nftPrefix, []string{tokenId})
	if err != nil {
		return nil, fmt.Errorf("failed to CreateCompositeKey to nftKey: %v", err)
	}

	nftBytes, err := json.Marshal(nft)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal nft: %v", err)
	}

	err = ctx.GetStub().PutState(nftKey, nftBytes)
	if err != nil {
		return nil, fmt.Errorf("failed to PutState nftBytes %s: %v", nftBytes, err)
	}

	// A composite key would be balancePrefix.owner.tokenId, which enables partial
	// composite key query to find and count all records matching balance.owner.*
	// An empty value would represent a delete, so we simply insert the null character.

	balanceKey, err := ctx.GetStub().CreateCompositeKey(balancePrefix, []string{minter, tokenId})
	if err != nil {
		return nil, fmt.Errorf("failed to CreateCompositeKey to balanceKey: %v", err)
	}

	err = ctx.GetStub().PutState(balanceKey, []byte{'\u0000'})
	if err != nil {
		return nil, fmt.Errorf("failed to PutState balanceKey %s: %v", nftBytes, err)
	}

	// Emit the Transfer event
	transferEvent := new(Transfer)
	transferEvent.From = "0x0"
	transferEvent.To = minter
	transferEvent.TokenId = tokenId

	transferEventBytes, err := json.Marshal(transferEvent)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal transferEventBytes: %v", err)
	}

	err = ctx.GetStub().SetEvent("Transfer", transferEventBytes)
	if err != nil {
		return nil, fmt.Errorf("failed to SetEvent transferEventBytes %s: %v", transferEventBytes, err)
	}

	return nft, nil
}

// Burn a non-fungible token
// param {String} tokenId Unique ID of a non-fungible token
// returns {Boolean} Return whether the burn was successful or not
func (c *TokenERC721Contract) Burn(ctx contractapi.TransactionContextInterface, tokenId string) (bool, error) {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return false, fmt.Errorf("failed to check if contract ia already initialized: %v", err)
	}
	if !initialized {
		return false, fmt.Errorf("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	owner64, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return false, fmt.Errorf("failed to GetClientIdentity owner64: %v", err)
	}

	ownerBytes, err := base64.StdEncoding.DecodeString(owner64)
	if err != nil {
		return false, fmt.Errorf("failed to DecodeString owner64: %v", err)
	}
	owner := string(ownerBytes)

	// Check if a caller is the owner of the non-fungible token
	nft, err := _readNFT(ctx, tokenId)
	if err != nil {
		return false, fmt.Errorf("failed to _readNFT nft : %v", err)
	}
	if nft.Owner != owner {
		return false, fmt.Errorf("non-fungible token %s is not owned by %s", tokenId, owner)
	}

	// Delete the token
	nftKey, err := ctx.GetStub().CreateCompositeKey(nftPrefix, []string{tokenId})
	if err != nil {
		return false, fmt.Errorf("failed to CreateCompositeKey tokenId: %v", err)
	}

	err = ctx.GetStub().DelState(nftKey)
	if err != nil {
		return false, fmt.Errorf("failed to DelState nftKey: %v", err)
	}

	// Remove a composite key from the balance of the owner
	balanceKey, err := ctx.GetStub().CreateCompositeKey(balancePrefix, []string{owner, tokenId})
	if err != nil {
		return false, fmt.Errorf("failed to CreateCompositeKey balanceKey %s: %v", balanceKey, err)
	}

	err = ctx.GetStub().DelState(balanceKey)
	if err != nil {
		return false, fmt.Errorf("failed to DelState balanceKey %s: %v", balanceKey, err)
	}

	// Emit the Transfer event
	transferEvent := new(Transfer)
	transferEvent.From = owner
	transferEvent.To = "0x0"
	transferEvent.TokenId = tokenId

	transferEventBytes, err := json.Marshal(transferEvent)
	if err != nil {
		return false, fmt.Errorf("failed to marshal transferEventBytes: %v", err)
	}

	err = ctx.GetStub().SetEvent("Transfer", transferEventBytes)
	if err != nil {
		return false, fmt.Errorf("failed to SetEvent transferEventBytes: %v", err)
	}

	return true, nil
}

// ClientAccountBalance returns the balance of the requesting client's account.
// returns {Number} Returns the account balance
func (c *TokenERC721Contract) ClientAccountBalance(ctx contractapi.TransactionContextInterface) (int, error) {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return 0, fmt.Errorf("failed to check if contract ia already initialized: %v", err)
	}
	if !initialized {
		return 0, fmt.Errorf("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	// Get ID of submitting client identity
	clientAccountID64, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return 0, fmt.Errorf("failed to GetClientIdentity minter: %v", err)
	}

	clientAccountIDBytes, err := base64.StdEncoding.DecodeString(clientAccountID64)
	if err != nil {
		return 0, fmt.Errorf("failed to DecodeString sender: %v", err)
	}

	clientAccountID := string(clientAccountIDBytes)

	return c.BalanceOf(ctx, clientAccountID), nil
}

// ClientAccountID returns the id of the requesting client's account.
// In this implementation, the client account ID is the clientId itself.
// Users can use this function to get their own account id, which they can then give to others as the payment address

func (c *TokenERC721Contract) ClientAccountID(ctx contractapi.TransactionContextInterface) (string, error) {

	//check if contract has been intilized first
	initialized, err := checkInitialized(ctx)
	if err != nil {
		return "", fmt.Errorf("failed to check if contract ia already initialized: %v", err)
	}
	if !initialized {
		return "", fmt.Errorf("Contract options need to be set before calling any function, call Initialize() to initialize contract")
	}

	// Get ID of submitting client identity
	clientAccountID64, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return "", fmt.Errorf("failed to GetClientIdentity minter: %v", err)
	}

	clientAccountBytes, err := base64.StdEncoding.DecodeString(clientAccountID64)
	if err != nil {
		return "", fmt.Errorf("failed to DecodeString clientAccount64: %v", err)
	}
	clientAccount := string(clientAccountBytes)

	return clientAccount, nil
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
