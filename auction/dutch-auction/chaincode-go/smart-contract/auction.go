/*
SPDX-License-Identifier: Apache-2.0
*/

package auction

import (
	"bytes"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"sort"

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

type SmartContract struct {
	contractapi.Contract
}

// Auction data
type Auction struct {
	Type         string             `json:"objectType"`
	ItemSold     string             `json:"item"`
	Seller       string             `json:"seller"`
	Quantity     int                `json:"quantity"`
	Orgs         []string           `json:"organizations"`
	PrivateBids  map[string]BidHash `json:"privateBids"`
	RevealedBids map[string]FullBid `json:"revealedBids"`
	Winners      []Winners          `json:"winners"`
	Price        int                `json:"price"`
	Status       string             `json:"status"`
}

// FullBid is the structure of a revealed bid
type FullBid struct {
	Type     string `json:"objectType"`
	Quantity int    `json:"quantity"`
	Price    int    `json:"price"`
	Org      string `json:"org"`
	Buyer    string `json:"buyer"`
}

// BidHash is the structure of a private bid
type BidHash struct {
	Org  string `json:"org"`
	Hash string `json:"hash"`
}

// Winners stores the winners of the auction
type Winners struct {
	Buyer    string `json:"buyer"`
	Quantity int    `json:"quantity"`
}

const bidKeyType = "bid"

// CreateAuction creates on auction on the public channel. The identity that
// submits the transacion becomes the seller of the auction
func (s *SmartContract) CreateAuction(ctx contractapi.TransactionContextInterface, auctionID string, itemsold string, quantity int) error {

	// get ID of submitting client
	clientID, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return fmt.Errorf("failed to get client identity %v", err)
	}

	// get org of submitting client
	clientOrgID, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return fmt.Errorf("failed to get client identity %v", err)
	}

	// Create auction
	bidders := make(map[string]BidHash)
	revealedBids := make(map[string]FullBid)

	auction := Auction{
		Type:         "auction",
		ItemSold:     itemsold,
		Quantity:     quantity,
		Price:        0,
		Seller:       clientID,
		Orgs:         []string{clientOrgID},
		PrivateBids:  bidders,
		RevealedBids: revealedBids,
		Winners:      []Winners{},
		Status:       "open",
	}

	auctionBytes, err := json.Marshal(auction)
	if err != nil {
		return err
	}

	// put auction into state
	err = ctx.GetStub().PutState(auctionID, auctionBytes)
	if err != nil {
		return fmt.Errorf("failed to put auction in public data: %v", err)
	}

	// set the seller of the auction as an endorser
	err = setAssetStateBasedEndorsement(ctx, auctionID, clientOrgID)
	if err != nil {
		return fmt.Errorf("failed setting state based endorsement for new organization: %v", err)
	}

	return nil
}

// Bid is used to add a users bid to the auction. The bid is stored in the private
// data collection on the peer of the bidder's organization. The function returns
// the transaction ID so that users can identify and query their bid
func (s *SmartContract) Bid(ctx contractapi.TransactionContextInterface, auctionID string) (string, error) {

	// get bid from transient map
	transientMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return "", fmt.Errorf("error getting transient: %v", err)
	}

	BidJSON, ok := transientMap["bid"]
	if !ok {
		return "", fmt.Errorf("bid key not found in the transient map")
	}

	// get the implicit collection name using the bidder's organization ID
	collection, err := getCollectionName(ctx)
	if err != nil {
		return "", fmt.Errorf("failed to get implicit collection name: %v", err)
	}

	// the bidder has to target their peer to store the bid
	err = verifyClientOrgMatchesPeerOrg(ctx)
	if err != nil {
		return "", fmt.Errorf("Cannot store bid on this peer, not a member of this org: Error %v", err)
	}

	// the transaction ID is used as a unique index for the bid
	txID := ctx.GetStub().GetTxID()

	// create a composite key using the transaction ID
	bidKey, err := ctx.GetStub().CreateCompositeKey(bidKeyType, []string{auctionID, txID})
	if err != nil {
		return "", fmt.Errorf("failed to create composite key: %v", err)
	}

	// put the bid into the organization's implicit data collection
	err = ctx.GetStub().PutPrivateData(collection, bidKey, BidJSON)
	if err != nil {
		return "", fmt.Errorf("failed to input price into collection: %v", err)
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
		return fmt.Errorf("failed to get client MSP ID: %v", err)
	}

	// get the auction from state
	auctionBytes, err := ctx.GetStub().GetState(auctionID)
	var auctionJSON Auction

	if auctionBytes == nil {
		return fmt.Errorf("Auction not found: %v", auctionID)
	}
	err = json.Unmarshal(auctionBytes, &auctionJSON)
	if err != nil {
		return fmt.Errorf("failed to create auction object JSON: %v", err)
	}

	// the auction needs to be open for users to add their bid
	Status := auctionJSON.Status
	if Status != "open" {
		return fmt.Errorf("cannot join closed or ended auction")
	}

	// get the inplicit collection name of bidder's org
	collection, err := getCollectionName(ctx)
	if err != nil {
		return fmt.Errorf("failed to get implicit collection name: %v", err)
	}

	// use the transaction ID passed as a parameter to create composite bid key
	bidKey, err := ctx.GetStub().CreateCompositeKey(bidKeyType, []string{auctionID, txID})
	if err != nil {
		return fmt.Errorf("failed to create composite key: %v", err)
	}

	// get the hash of the bid if found in private collection
	bidHash, err := ctx.GetStub().GetPrivateDataHash(collection, bidKey)
	if err != nil {
		return fmt.Errorf("failed to read bid bash from collection: %v", err)
	}
	if bidHash == nil {
		return fmt.Errorf("bid hash does not exist: %s", bidKey)
	}

	// store the hash along with the bidder's organization
	NewHash := BidHash{
		Org:  clientOrgID,
		Hash: fmt.Sprintf("%x", bidHash),
	}

	bidders := make(map[string]BidHash)
	bidders = auctionJSON.PrivateBids
	bidders[bidKey] = NewHash
	auctionJSON.PrivateBids = bidders

	// Add the bidding organization to the list of participating organization's if it is not already
	Orgs := auctionJSON.Orgs
	if !(contains(Orgs, clientOrgID)) {
		newOrgs := append(Orgs, clientOrgID)
		auctionJSON.Orgs = newOrgs

		err = addAssetStateBasedEndorsement(ctx, auctionID, clientOrgID)
		if err != nil {
			return fmt.Errorf("failed setting state based endorsement for new organization: %v", err)
		}
	}

	newAuctionBytes, _ := json.Marshal(auctionJSON)

	err = ctx.GetStub().PutState(auctionID, newAuctionBytes)
	if err != nil {
		return fmt.Errorf("failed to update auction: %v", err)
	}

	return nil
}

// RevealBid is used by a bidder to reveal their bid after the auction is closed
func (s *SmartContract) RevealBid(ctx contractapi.TransactionContextInterface, auctionID string, txID string) error {

	// get bid from transient map
	transientMap, err := ctx.GetStub().GetTransient()
	if err != nil {
		return fmt.Errorf("error getting transient: %v", err)
	}

	transientBidJSON, ok := transientMap["bid"]
	if !ok {
		return fmt.Errorf("bid key not found in the transient map")
	}

	// get implicit collection name of organization ID
	collection, err := getCollectionName(ctx)
	if err != nil {
		return fmt.Errorf("failed to get implicit collection name: %v", err)
	}

	// use transaction ID to create composit bid key
	bidKey, err := ctx.GetStub().CreateCompositeKey(bidKeyType, []string{auctionID, txID})
	if err != nil {
		return fmt.Errorf("failed to create composite key: %v", err)
	}

	// get bid hash of bid if private bid on the public ledger
	bidHash, err := ctx.GetStub().GetPrivateDataHash(collection, bidKey)
	if err != nil {
		return fmt.Errorf("failed to read bid bash from collection: %v", err)
	}
	if bidHash == nil {
		return fmt.Errorf("bid hash does not exist: %s", bidKey)
	}

	// get auction from public state
	auctionBytes, err := ctx.GetStub().GetState(auctionID)
	if err != nil {
		return fmt.Errorf("failed to get auction %v: %v", auctionID, err)
	}
	if auctionBytes == nil {
		return fmt.Errorf("Auction interest object %v not found", auctionID)
	}

	var auctionJSON Auction
	err = json.Unmarshal(auctionBytes, &auctionJSON)
	if err != nil {
		return fmt.Errorf("failed to create auction object JSON: %v", err)
	}

	// Complete a series of three checks before we add the bid to the auction

	// check 1: check that the auction is closed. We cannot reveal an
	// bid to an open auction
	Status := auctionJSON.Status
	if Status != "closed" {
		return fmt.Errorf("cannot reveal bid for open or ended auction")
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

	bidders := auctionJSON.PrivateBids
	privateBidHashString := bidders[bidKey].Hash

	onChainBidHashString := fmt.Sprintf("%x", bidHash)
	if privateBidHashString != onChainBidHashString {
		return fmt.Errorf("hash %s for bid JSON %s does not match hash in auction: %s, bidder must have changed bid",
			privateBidHashString,
			transientBidJSON,
			onChainBidHashString,
		)
	}

	// we can add the bid to the auction if all checks have passed
	type transientBidInput struct {
		Quantity int    `json:"quantity"`
		Price    int    `json:"price"`
		Org      string `json:"org"`
		Buyer    string `json:"buyer"`
	}

	// unmarshal bid imput
	var bidInput transientBidInput
	err = json.Unmarshal(transientBidJSON, &bidInput)
	if err != nil {
		return fmt.Errorf("failed to unmarshal JSON: %v", err)
	}

	// Get ID of submitting client identity
	clientID, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return fmt.Errorf("failed to get client identity %v", err)
	}

	// marshal transient parameters and ID and MSPID into bid object
	NewBid := FullBid{
		Type:     bidKeyType,
		Quantity: bidInput.Quantity,
		Price:    bidInput.Price,
		Org:      bidInput.Org,
		Buyer:    bidInput.Buyer,
	}

	// check 4: make sure that the transaction is being submitted is the bidder
	if bidInput.Buyer != clientID {
		return fmt.Errorf("Permission denied, client id %v is not the owner of the bid", clientID)
	}

	revealedBids := make(map[string]FullBid)
	revealedBids = auctionJSON.RevealedBids
	revealedBids[bidKey] = NewBid
	auctionJSON.RevealedBids = revealedBids

	newAuctionBytes, _ := json.Marshal(auctionJSON)

	// put auction with bid added back into state
	err = ctx.GetStub().PutState(auctionID, newAuctionBytes)
	if err != nil {
		return fmt.Errorf("failed to update auction: %v", err)
	}

	return nil
}

// CloseAuction can be used by the seller to close the auction. This prevents
// bids from being added to the auction, and allows users to reveal their bid
func (s *SmartContract) CloseAuction(ctx contractapi.TransactionContextInterface, auctionID string) error {

	auctionBytes, err := ctx.GetStub().GetState(auctionID)
	if err != nil {
		return fmt.Errorf("failed to get auction %v: %v", auctionID, err)
	}

	if auctionBytes == nil {
		return fmt.Errorf("Auction interest object %v not found", auctionID)
	}

	var auctionJSON Auction
	err = json.Unmarshal(auctionBytes, &auctionJSON)
	if err != nil {
		return fmt.Errorf("failed to create auction object JSON: %v", err)
	}

	// the auction can only be closed by the seller

	// get ID of submitting client
	clientID, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return fmt.Errorf("failed to get client identity %v", err)
	}

	Seller := auctionJSON.Seller
	if Seller != clientID {
		return fmt.Errorf("auction can only be closed by seller: %v", err)
	}

	Status := auctionJSON.Status
	if Status != "open" {
		return fmt.Errorf("cannot close auction that is not open")
	}

	auctionJSON.Status = string("closed")

	closedAuction, _ := json.Marshal(auctionJSON)

	err = ctx.GetStub().PutState(auctionID, closedAuction)
	if err != nil {
		return fmt.Errorf("failed to close auction: %v", err)
	}

	return nil
}

// EndAuction both changes the auction status to closed and calculates the winners
// of the auction
func (s *SmartContract) EndAuction(ctx contractapi.TransactionContextInterface, auctionID string) error {

	auctionBytes, err := ctx.GetStub().GetState(auctionID)
	if err != nil {
		return fmt.Errorf("failed to get auction %v: %v", auctionID, err)
	}

	if auctionBytes == nil {
		return fmt.Errorf("Auction interest object %v not found", auctionID)
	}

	var auctionJSON Auction
	err = json.Unmarshal(auctionBytes, &auctionJSON)
	if err != nil {
		return fmt.Errorf("failed to create auction object JSON: %v", err)
	}

	// Check that the auction is being ended by the seller

	// get ID of submitting client
	clientID, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return fmt.Errorf("failed to get client identity %v", err)
	}

	Seller := auctionJSON.Seller
	if Seller != clientID {
		return fmt.Errorf("auction can only be ended by seller: %v", err)
	}

	Status := auctionJSON.Status
	if Status != "closed" {
		return fmt.Errorf("Can only end a closed auction")
	}

	// get the list of revealed bids

	revealedBidMap := auctionJSON.RevealedBids
	if len(auctionJSON.RevealedBids) == 0 {
		return fmt.Errorf("No bids have been revealed, cannot end auction: %v", err)
	}

	// sort the map of revealed bids to make it easier to calculate winners
	// if bids are tied, fill smaller bids first
	var Bidders []FullBid

	for _, bid := range revealedBidMap {
		Bidders = append(Bidders, bid)
	}

	sort.Slice(Bidders, func(p, q int) bool {
		if Bidders[p].Price > Bidders[q].Price {
			return true
		}
		if Bidders[p].Price < Bidders[q].Price {
			return false
		}
		return Bidders[p].Quantity < Bidders[q].Quantity
	})

	i := 0
	remainingQuantity := auctionJSON.Quantity

	// calculate the winners
	for remainingQuantity > 0 {

		// create the next winning bid
		winner := Winners{
			Buyer:    Bidders[i].Buyer,
			Quantity: Bidders[i].Quantity,
		}

		// add them to the list of winners and change the winning price
		auctionJSON.Winners = append(auctionJSON.Winners, winner)
		auctionJSON.Price = Bidders[i].Price

		// Calculate the quantity that goes to the winner
		// if there is sufficient quantity to give them the full bid
		if remainingQuantity > Bidders[i].Quantity {
			remainingQuantity = remainingQuantity - Bidders[i].Quantity

			// if there is not, give the remainder
		} else {
			auctionJSON.Winners[i].Quantity = remainingQuantity
			remainingQuantity = 0
		}
		i++
	}

	// check if there is a winning bid that has yet to be revealed
	err = queryAllBids(ctx, auctionJSON.Price, auctionJSON.RevealedBids, auctionJSON.PrivateBids)
	if err != nil {
		return fmt.Errorf("Cannot close auction: %v", err)
	}

	auctionJSON.Status = string("ended")

	closedAuction, _ := json.Marshal(auctionJSON)

	err = ctx.GetStub().PutState(auctionID, closedAuction)
	if err != nil {
		return fmt.Errorf("failed to close auction: %v", err)
	}
	return nil
}
