/*
SPDX-License-Identifier: Apache-2.0
*/

package auction

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"

	"github.com/hyperledger/fabric-contract-api-go/v2/contractapi"
)

type SmartContract struct {
	contractapi.Contract
}

// Auction data
type Auction struct {
	Type         string             `json:"objectType"`
	ItemSold     string             `json:"item"`
	Seller       string             `json:"seller"`
	Orgs         []string           `json:"organizations"`
	PrivateBids  map[string]BidHash `json:"privateBids"`
	RevealedBids map[string]FullBid `json:"revealedBids"`
	Winner       string             `json:"winner"`
	Price        int                `json:"price"`
	Status       string             `json:"status"`
}

// FullBid is the structure of a revealed bid
type FullBid struct {
	Type   string `json:"objectType"`
	Price  int    `json:"price"`
	Org    string `json:"org"`
	Bidder string `json:"bidder"`
}

// BidHash is the structure of a private bid
type BidHash struct {
	Org  string `json:"org"`
	Hash string `json:"hash"`
}

const bidKeyType = "bid"

// CreateAuction creates on auction on the public channel. The identity that
// submits the transacion becomes the seller of the auction
func (s *SmartContract) CreateAuction(ctx contractapi.TransactionContextInterface, auctionID string, itemsold string) error {

	// get ID of submitting client
	clientID, err := s.GetSubmittingClientIdentity(ctx)
	if err != nil {
		return fmt.Errorf("failed to get client identity %w", err)
	}

	// get org of submitting client
	clientOrgID, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return fmt.Errorf("failed to get client identity %w", err)
	}

	// Create auction
	bidders := make(map[string]BidHash)
	revealedBids := make(map[string]FullBid)

	auction := Auction{
		Type:         "auction",
		ItemSold:     itemsold,
		Price:        0,
		Seller:       clientID,
		Orgs:         []string{clientOrgID},
		PrivateBids:  bidders,
		RevealedBids: revealedBids,
		Winner:       "",
		Status:       "open",
	}

	auctionJSON, err := json.Marshal(auction)
	if err != nil {
		return err
	}

	// put auction into state
	err = ctx.GetStub().PutState(auctionID, auctionJSON)
	if err != nil {
		return fmt.Errorf("failed to put auction in public data: %w", err)
	}

	// set the seller of the auction as an endorser
	err = setAssetStateBasedEndorsement(ctx, auctionID, clientOrgID)
	if err != nil {
		return fmt.Errorf("failed setting state based endorsement for new organization: %w", err)
	}

	return nil
}

// Bid is used to add a user's bid to the auction. The bid is stored in the private
// data collection on the peer of the bidder's organization. The function returns
// the transaction ID so that users can identify and query their bid
func (s *SmartContract) Bid(ctx contractapi.TransactionContextInterface, auctionID string) (string, error) {

	// get bid from transient map
	transientMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return "", fmt.Errorf("error getting transient: %w", err)
	}

	BidJSON, ok := transientMap["bid"]
	if !ok {
		return "", errors.New("bid key not found in the transient map")
	}

	// get the implicit collection name using the bidder's organization ID
	collection, err := getCollectionName(ctx)
	if err != nil {
		return "", fmt.Errorf("failed to get implicit collection name: %w", err)
	}

	// the bidder has to target their peer to store the bid
	err = verifyClientOrgMatchesPeerOrg(ctx)
	if err != nil {
		return "", fmt.Errorf("cannot store bid on this peer, not a member of this org: Error %w", err)
	}

	// the transaction ID is used as a unique index for the bid
	txID := ctx.GetStub().GetTxID()

	// create a composite key using the transaction ID
	bidKey, err := ctx.GetStub().CreateCompositeKey(bidKeyType, []string{auctionID, txID})
	if err != nil {
		return "", fmt.Errorf("failed to create composite key: %w", err)
	}

	// put the bid into the organization's implicit data collection
	err = ctx.GetStub().PutPrivateData(collection, bidKey, BidJSON)
	if err != nil {
		return "", fmt.Errorf("failed to input price into collection: %w", err)
	}

	// return the trannsaction ID so that the uset can identify their bid
	return txID, nil
}

// SubmitBid is used by the bidder to add the hash of that bid stored in private data to the
// auction. Note that this function alters the auction in private state, and needs
// to meet the auction endorsement policy. Transaction ID is used identify the bid
func (s *SmartContract) SubmitBid(ctx contractapi.TransactionContextInterface, auctionID string, txID string) error {

	// get the MSP ID of the bidder's org
	clientOrgID, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return fmt.Errorf("failed to get client MSP ID: %w", err)
	}

	// get the auction from public state
	auction, err := s.QueryAuction(ctx, auctionID)
	if err != nil {
		return fmt.Errorf("failed to get auction from public state %w", err)
	}

	// the auction needs to be open for users to add their bid
	Status := auction.Status
	if Status != "open" {
		return errors.New("cannot join closed or ended auction")
	}

	// get the inplicit collection name of bidder's org
	collection, err := getCollectionName(ctx)
	if err != nil {
		return fmt.Errorf("failed to get implicit collection name: %w", err)
	}

	// use the transaction ID passed as a parameter to create composite bid key
	bidKey, err := ctx.GetStub().CreateCompositeKey(bidKeyType, []string{auctionID, txID})
	if err != nil {
		return fmt.Errorf("failed to create composite key: %w", err)
	}

	// get the hash of the bid stored in private data collection
	bidHash, err := ctx.GetStub().GetPrivateDataHash(collection, bidKey)
	if err != nil {
		return fmt.Errorf("failed to read bid bash from collection: %w", err)
	}
	if bidHash == nil {
		return fmt.Errorf("bid hash does not exist: %s", bidKey)
	}

	// store the hash along with the bidder's organization
	NewHash := BidHash{
		Org:  clientOrgID,
		Hash: hex.EncodeToString(bidHash),
	}

	auction.PrivateBids[bidKey] = NewHash

	// Add the bidding organization to the list of participating organizations if it is not already
	Orgs := auction.Orgs
	if !(contains(Orgs, clientOrgID)) {
		newOrgs := append(Orgs, clientOrgID)
		auction.Orgs = newOrgs

		err = addAssetStateBasedEndorsement(ctx, auctionID, clientOrgID)
		if err != nil {
			return fmt.Errorf("failed setting state based endorsement for new organization: %w", err)
		}
	}

	newAuctionJSON, _ := json.Marshal(auction)

	err = ctx.GetStub().PutState(auctionID, newAuctionJSON)
	if err != nil {
		return fmt.Errorf("failed to update auction: %w", err)
	}

	return nil
}

// RevealBid is used by a bidder to reveal their bid after the auction is closed
func (s *SmartContract) RevealBid(ctx contractapi.TransactionContextInterface, auctionID string, txID string) error {

	// get bid from transient map
	transientMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return fmt.Errorf("error getting transient: %w", err)
	}

	transientBidJSON, ok := transientMap["bid"]
	if !ok {
		return errors.New("bid key not found in the transient map")
	}

	// get implicit collection name of organization ID
	collection, err := getCollectionName(ctx)
	if err != nil {
		return fmt.Errorf("failed to get implicit collection name: %w", err)
	}

	// use transaction ID to create composite bid key
	bidKey, err := ctx.GetStub().CreateCompositeKey(bidKeyType, []string{auctionID, txID})
	if err != nil {
		return fmt.Errorf("failed to create composite key: %w", err)
	}

	// get bid hash of bid if private bid on the public ledger
	bidHash, err := ctx.GetStub().GetPrivateDataHash(collection, bidKey)
	if err != nil {
		return fmt.Errorf("failed to read bid bash from collection: %w", err)
	}
	if bidHash == nil {
		return fmt.Errorf("bid hash does not exist: %s", bidKey)
	}

	// get auction from public state
	auction, err := s.QueryAuction(ctx, auctionID)
	if err != nil {
		return fmt.Errorf("failed to get auction from public state %w", err)
	}

	// Complete a series of three checks before we add the bid to the auction

	// check 1: check that the auction is closed. We cannot reveal a
	// bid to an open auction
	Status := auction.Status
	if Status != "closed" {
		return errors.New("cannot reveal bid for open or ended auction")
	}

	// check 2: check that hash of revealed bid matches hash of private bid
	// on the public ledger. This checks that the bidder is telling the truth
	// about the value of their bid

	hash := sha256.New()
	hash.Write(transientBidJSON)
	calculatedBidJSONHash := hash.Sum(nil)

	// verify that the hash of the passed immutable properties matches the on-chain hash
	if !bytes.Equal(calculatedBidJSONHash, bidHash) {
		return fmt.Errorf("hash %x for bid JSON %s does not match hash in auction: %x",
			calculatedBidJSONHash,
			transientBidJSON,
			bidHash,
		)
	}

	// check 3; check hash of relealed bid matches hash of private bid that was
	// added earlier. This ensures that the bid has not changed since it
	// was added to the auction

	privateBidHashString := auction.PrivateBids[bidKey].Hash

	onChainBidHashString := hex.EncodeToString(bidHash)
	if privateBidHashString != onChainBidHashString {
		return fmt.Errorf("hash %s for bid JSON %s does not match hash in auction: %s, bidder must have changed bid",
			privateBidHashString,
			transientBidJSON,
			onChainBidHashString,
		)
	}

	// we can add the bid to the auction if all checks have passed
	type transientBidInput struct {
		Price  int    `json:"price"`
		Org    string `json:"org"`
		Bidder string `json:"bidder"`
	}

	// unmarshal bid input
	var bidInput transientBidInput
	err = json.Unmarshal(transientBidJSON, &bidInput)
	if err != nil {
		return fmt.Errorf("failed to unmarshal JSON: %w", err)
	}

	// Get ID of submitting client identity
	clientID, err := s.GetSubmittingClientIdentity(ctx)
	if err != nil {
		return fmt.Errorf("failed to get client identity %w", err)
	}

	// marshal transient parameters and ID and MSPID into bid object
	NewBid := FullBid{
		Type:   bidKeyType,
		Price:  bidInput.Price,
		Org:    bidInput.Org,
		Bidder: bidInput.Bidder,
	}

	// check 4: make sure that the transaction is being submitted is the bidder
	if bidInput.Bidder != clientID {
		return fmt.Errorf("permission denied, client id %v is not the owner of the bid", clientID)
	}

	auction.RevealedBids[bidKey] = NewBid

	newAuctionJSON, _ := json.Marshal(auction)

	// put auction with bid added back into state
	err = ctx.GetStub().PutState(auctionID, newAuctionJSON)
	if err != nil {
		return fmt.Errorf("failed to update auction: %w", err)
	}

	return nil
}

// CloseAuction can be used by the seller to close the auction. This prevents
// bids from being added to the auction, and allows users to reveal their bid
func (s *SmartContract) CloseAuction(ctx contractapi.TransactionContextInterface, auctionID string) error {

	// get auction from public state
	auction, err := s.QueryAuction(ctx, auctionID)
	if err != nil {
		return fmt.Errorf("failed to get auction from public state %w", err)
	}

	// the auction can only be closed by the seller

	// get ID of submitting client
	clientID, err := s.GetSubmittingClientIdentity(ctx)
	if err != nil {
		return fmt.Errorf("failed to get client identity %w", err)
	}

	Seller := auction.Seller
	if Seller != clientID {
		return fmt.Errorf("auction can only be closed by seller: %w", err)
	}

	Status := auction.Status
	if Status != "open" {
		return errors.New("cannot close auction that is not open")
	}

	auction.Status = string("closed")

	closedAuctionJSON, _ := json.Marshal(auction)

	err = ctx.GetStub().PutState(auctionID, closedAuctionJSON)
	if err != nil {
		return fmt.Errorf("failed to close auction: %w", err)
	}

	return nil
}

// EndAuction both changes the auction status to closed and calculates the winners
// of the auction
func (s *SmartContract) EndAuction(ctx contractapi.TransactionContextInterface, auctionID string) error {

	// get auction from public state
	auction, err := s.QueryAuction(ctx, auctionID)
	if err != nil {
		return fmt.Errorf("failed to get auction from public state %w", err)
	}

	// Check that the auction is being ended by the seller

	// get ID of submitting client
	clientID, err := s.GetSubmittingClientIdentity(ctx)
	if err != nil {
		return fmt.Errorf("failed to get client identity %w", err)
	}

	Seller := auction.Seller
	if Seller != clientID {
		return fmt.Errorf("auction can only be ended by seller: %w", err)
	}

	Status := auction.Status
	if Status != "closed" {
		return errors.New("can only end a closed auction")
	}

	// get the list of revealed bids
	revealedBidMap := auction.RevealedBids
	if len(auction.RevealedBids) == 0 {
		return fmt.Errorf("no bids have been revealed, cannot end auction: %w", err)
	}

	// determine the highest bid
	for _, bid := range revealedBidMap {
		if bid.Price > auction.Price {
			auction.Winner = bid.Bidder
			auction.Price = bid.Price
		}
	}

	// check if there is a winning bid that has yet to be revealed
	err = checkForHigherBid(ctx, auction.Price, auction.RevealedBids, auction.PrivateBids)
	if err != nil {
		return fmt.Errorf("cannot end auction: %w", err)
	}

	auction.Status = "ended"

	endedAuctionJSON, _ := json.Marshal(auction)

	err = ctx.GetStub().PutState(auctionID, endedAuctionJSON)
	if err != nil {
		return fmt.Errorf("failed to end auction: %w", err)
	}
	return nil
}
