## Simple blind auction sample

The simple blind auction sample uses Hyperledger Fabric to run an auction where bids are kept private until the auction period is over. Instead of displaying the full bid on the public ledger, buyers can only see hashes of other bids while bidding is underway. This prevents buyers from changing their bids in response to bids submitted by others. After the bidding period ends, participants reveal their bid to try to win the auction. The organizations participating in the auction verify that a revealed bid matches the hash on the public ledger. Whichever has the highest bid wins.

A user that wants to sell one item can use the smart contract to create an auction. The auction is stored on the channel ledger and can be read by all channel members. The auctions created by the smart contract are run in three steps:
1. Each auction is created with the status **open**. While the auction is open, buyers can add new bids to the auction. The full bids of each buyer are stored in the implicit private data collections of their organization. After the bid is created, the bidder can submit the hash of the bid to the auction. A bid is added to the auction in two steps because the transaction that creates the bid only needs to be endorsed by a peer of the bidders organization, while a transaction that updates the auction may need to be endorsed by multiple organizations. When the bid is added to the auction, the bidder's organization is added to the list of organizations that need to endorse any updates to the auction.
2. The auction is **closed** to prevent additional bids from being added to the auction. After the auction is closed, bidders that submitted bids to the auction can reveal their full bid. Only revealed bids can win the auction.
3. The auction is **ended** to calculate the winner from the set of revealed bids. All organizations participating in the auction calculate the price that clears the auction and the winning bid. The seller can end the auction only if all bidding organizations endorse the same winner and price.

Before endorsing the transaction that ends the auction, each organization queries the implicit private data collection on their peers to check if any organization member has a winning bid that has not yet been revealed. If a winning bid is found, the organization will withhold their endorsement and prevent the auction from being closed. This prevents the seller from ending the auction prematurely, or colluding with buyers to end the auction at an artificially low price.

The sample uses several Fabric features to make the auction private and secure. Bids are stored in private data collections to prevent bids from being distributed to other peers in the channel. When bidding is closed, the auction smart contract uses the `GetPrivateDataHash()` API to verify that the bid stored in private data is the same bid that is being revealed. State based endorsement is used to add the organization of each bidder to the auction endorsement policy. The smart contract uses the `GetClientIdentity.GetID()` API to ensure that only the potential buyer can read their bid from private state and only the seller can close or end the auction.

This tutorial uses the auction smart contract in a scenario where one seller wants to auction a painting. Four potential buyers from two different organizations will submit bids to the auction and try to win the auction.

## Deploy the chaincode

We will run the auction smart contract using the Fabric test network. Open a command terminal and navigate to the test network directory:
```
cd fabric-samples/test-network
```

You can then run the following command to deploy the test network.
```
./network.sh up createChannel -ca
```

Note that we use the `-ca` flag to deploy the network using certificate authorities. We will use the CA to register and enroll our sellers and buyers.

Run the following command to deploy the auction smart contract. We will override the default endorsement policy to allow any channel member to create an auction without requiring an endorsement from another organization.
```
./network.sh deployCC -ccn auction -ccp ../auction/chaincode-go/ -ccl go -ccep "OR('Org1MSP.peer','Org2MSP.peer')"
```

## Install the application dependencies

We will interact with the auction smart contract through a set of Node.js applications. Change into the `application-javascript` directory:
```
cd fabric-samples/auction/application-javascript
```

From this directory, run the following command to download the application dependencies:
```
npm install
```

## Register and enroll the application identities

To interact with the network, you will need to enroll the Certificate Authority administrators of Org1 and Org2. You can use the `enrollAdmin.js` program for this task. Run the following command to enroll the Org1 admin:
```
node enrollAdmin.js org1
```
You should see the logs of the admin wallet being created on your local file system. Now run the command to enroll the CA admin of Org2:
```
node enrollAdmin.js org2
```

We can use the CA admins of both organizations to register and enroll the identities of the seller that will create the auction and the bidders who will try to purchase the painting.

Run the following command to register and enroll the seller identity that will create the auction. The seller will belong to Org1.
```
node registerEnrollUser.js org1 seller
```

You should see the logs of the seller wallet being created as well. Run the following commands to register and enroll 2 bidders from Org1 and another 2 bidders from Org2:
```
node registerEnrollUser.js org1 bidder1
node registerEnrollUser.js org1 bidder2
node registerEnrollUser.js org2 bidder3
node registerEnrollUser.js org2 bidder4
```

## Create the auction

The seller from Org1 would like to create an auction to sell a vintage Matchbox painting. Run the following command to use the seller wallet to run the `createAuction.js` application. The program will submit a transaction to the network that creates the auction on the channel ledger. The organization and identity name are passed to the application to use the wallet that was created by the `registerEnrollUser.js` application. The seller needs to provide an ID for the auction and the item to be sold to create the auction:
```
node createAuction.js org1 seller PaintingAuction painting
```

After the transaction is complete, the `createAuction.js` application will query the auction stored in the public channel ledger:
```
*** Result: Auction: {
  "objectType": "auction",
  "item": "painting",
  "seller": "eDUwOTo6Q049c2VsbGVyLE9VPWNsaWVudCtPVT1vcmcxK09VPWRlcGFydG1lbnQxOjpDTj1jYS5vcmcxLmV4YW1wbGUuY29tLE89b3JnMS5leGFtcGxlLmNvbSxMPUR1cmhhbSxTVD1Ob3J0aCBDYXJvbGluYSxDPVVT",
  "organizations": [
    "Org1MSP"
  ],
  "privateBids": {},
  "revealedBids": {},
  "winner": "",
  "price": 0,
  "status": "open"
}
```
The smart contract uses the `GetClientIdentity().GetID()` API to read identity that creates the auction and defines that identity as the auction `"seller"`. You can see the seller information by decoding the `"seller"` string out of base64 format:

```
echo eDUwOTo6Q049c2VsbGVyLE9VPWNsaWVudCtPVT1vcmcxK09VPWRlcGFydG1lbnQxOjpDTj1jYS5vcmcxLmV4YW1wbGUuY29tLE89b3JnMS5leGFtcGxlLmNvbSxMPUR1cmhhbSxTVD1Ob3J0aCBDYXJvbGluYSxDPVVT | base64 --decode
```

The result is the name and issuer of the seller's certificate:
```
x509::CN=org1admin,OU=admin,O=Hyperledger,ST=North Carolina,C=US::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=USn
```

## Bid on the auction

We can now use the bidder wallets to submit bids to the auction:

### Bid as bidder1

Bidder1 will create a bid to purchase the painting for 800 dollars.
```
node bid.js org1 bidder1 PaintingAuction 800
```

The application will query the bid after it is created:
```
*** Result:  Bid: {
  "objectType": "bid",
  "price": 800,
  "org": "Org1MSP",
  "bidder": "eDUwOTo6Q049YmlkZGVyMSxPVT1jbGllbnQrT1U9b3JnMStPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMS5leGFtcGxlLmNvbSxPPW9yZzEuZXhhbXBsZS5jb20sTD1EdXJoYW0sU1Q9Tm9ydGggQ2Fyb2xpbmEsQz1VUw=="
}
```

The bid is stored in the Org1 implicit data collection. The `"bidder"` parameter is the information from the certificate of the user that created the bid. Only this identity will be able can query the bid from private state or reveal the bid during the auction.

The `bid.js` application also prints the bidID:
```
*** Result ***SAVE THIS VALUE*** BidID: 8ef83011a5fb791f75ed008337839426f6b87981519e5d58ef5ada39c3044edd
```

The BidID acts as the unique identifier for the bid. This ID allows you to query the bid using the `queryBid.js` program and add the bid to the auction. Save the bidID returned by the application as an environment variable in your terminal:
```
export BIDDER1_BID_ID=8ef83011a5fb791f75ed008337839426f6b87981519e5d58ef5ada39c3044edd
```
This value will be different for each transaction, so you will need to use the value returned in your terminal.

Now that the bid has been created, you can submit the bid to the auction. Run the following command to submit the bid that was just created:
```
node submitBid.js org1 bidder1 PaintingAuction $BIDDER1_BID_ID
```

The hash of bid will be added to the list private bids in that have been submitted to `PaintingAuction`. Storing the hash in the public auction allows users to accurately reveal the bid after bidding is closed. The application will query the auction to verify that the bid was added:
```
*** Result: Auction: {
  "objectType": "auction",
  "item": "painting",
  "seller": "eDUwOTo6Q049c2VsbGVyLE9VPWNsaWVudCtPVT1vcmcxK09VPWRlcGFydG1lbnQxOjpDTj1jYS5vcmcxLmV4YW1wbGUuY29tLE89b3JnMS5leGFtcGxlLmNvbSxMPUR1cmhhbSxTVD1Ob3J0aCBDYXJvbGluYSxDPVVT",
  "organizations": [
    "Org1MSP"
  ],
  "privateBids": {
    "\u0000bid\u0000PaintingAuction\u00008ef83011a5fb791f75ed008337839426f6b87981519e5d58ef5ada39c3044edd\u0000": {
      "org": "Org1MSP",
      "hash": "5cb50a17b5a21c02fc01306e3e9b54f4db67e9a440552ce898bbd7daa62dce0f"
    }
  },
  "revealedBids": {},
  "winner": "",
  "price": 0,
  "status": "open"
}
```

### Bid as bidder2

Let's submit another bid. Bidder2 would like to purchase the painting for 500 dollars.
```
node bid.js org1 bidder2 PaintingAuction 500
```

Save the Bid ID returned by the application:
```
export BIDDER2_BID_ID=915a908c8f2c368f4a3aedd73176656af81ddfab000b11629503403f3d97b185
```

Submit bidder2's bid to the auction:
```
node submitBid.js org1 bidder2 PaintingAuction $BIDDER2_BID_ID
```

### Bid as bidder3 from Org2

Bidder3 will bid 700 dollars for the painting:
```
node bid.js org2 bidder3 PaintingAuction 700
```

Save the Bid ID returned by the application:
```
export BIDDER3_BID_ID=5e4e637c68833b178739575f6fe09820b019551a8cfbb43a4d172e0aa864dfad
```

Add bidder3's bid to the auction:
```
node submitBid.js org2 bidder3 PaintingAuction $BIDDER3_BID_ID
```

Because bidder3 belongs to Org2, submitting the bid will add Org2 to the list of participating organizations. You can see the Org2 MSP ID has been added to the list of `"organizations"` in the updated auction returned by the application:
```
*** Result: Auction: {
  "objectType": "auction",
  "item": "painting",
  "seller": "eDUwOTo6Q049c2VsbGVyLE9VPWNsaWVudCtPVT1vcmcxK09VPWRlcGFydG1lbnQxOjpDTj1jYS5vcmcxLmV4YW1wbGUuY29tLE89b3JnMS5leGFtcGxlLmNvbSxMPUR1cmhhbSxTVD1Ob3J0aCBDYXJvbGluYSxDPVVT",
  "organizations": [
    "Org1MSP",
    "Org2MSP"
  ],
  "privateBids": {
    "\u0000bid\u0000PaintingAuction\u00005e4e637c68833b178739575f6fe09820b019551a8cfbb43a4d172e0aa864dfad\u0000": {
      "org": "Org2MSP",
      "hash": "40107eab7a99dfc2f25d02b8ab840f12fd802a9f86d8d42b78d7b4409b2c15bd"
    },
    "\u0000bid\u0000PaintingAuction\u00008ef83011a5fb791f75ed008337839426f6b87981519e5d58ef5ada39c3044edd\u0000": {
      "org": "Org1MSP",
      "hash": "5cb50a17b5a21c02fc01306e3e9b54f4db67e9a440552ce898bbd7daa62dce0f"
    },
    "\u0000bid\u0000PaintingAuction\u0000915a908c8f2c368f4a3aedd73176656af81ddfab000b11629503403f3d97b185\u0000": {
      "org": "Org1MSP",
      "hash": "a458df18b12dffe4ae6d56a270134c2d55bd53fface034bd24381d0073d46a45"
    }
  },
  "revealedBids": {},
  "winner": "",
  "price": 0,
  "status": "open"
}
```

Now that a bid from Org2 has been added to the auction, any updates to the auction need to be endorsed by the Org2 peer. The applications will use `"organizations"` field to specify which organizations need to endorse submitting a new bid, revealing a bid, or updating the auction status.

### Bid as bidder4

Bidder4 from Org2 would like to purchase the painting for 900 dollars:
```
node bid.js org2 bidder4 PaintingAuction 900
```

Save the Bid ID returned by the application:
```
export BIDDER4_BID_ID=49466271ae879bd009e75a60730a12bfa986e75f263202ab81ccd3deec544a35
```

Add bidder4's bid to the auction:
```
node submitBid.js org2 bidder4 PaintingAuction $BIDDER4_BID_ID
```

## Close the auction

Now that all four bidders have joined the auction, the seller would like to close the auction and allow buyers to reveal their bids. The seller identity that created the auction needs to submit the transaction:
```
node closeAuction.js org1 seller PaintingAuction
```

The application will query the auction to allow you to verify that the auction status has changed to closed. As a test, you can try to create and submit a new bid to verify that no new bids can be added to the auction.

## Reveal bids

After the auction is closed, bidders can try to win the auction by revealing their bids. The transaction to reveal a bid needs to pass four checks:
1. The auction is closed.
2. The transaction was submitted by the identity that created the bid.
3. The hash of the revealed bid matches the hash of the bid on the channel ledger. This confirms that the bid is the same as the bid that is stored in the private data collection.
4. The hash of the revealed bid matches the hash that was submitted to the auction. This confirms that the bid was not altered after the auction was closed.

Use the `revealBid.js` application to reveal the bid of Bidder1:
```
node revealBid.js org1 bidder1 PaintingAuction $BIDDER1_BID_ID
```

The full bid details, including the price, are now visible:
```
*** Result: Auction: {
  "objectType": "auction",
  "item": "painting",
  "seller": "eDUwOTo6Q049c2VsbGVyLE9VPWNsaWVudCtPVT1vcmcxK09VPWRlcGFydG1lbnQxOjpDTj1jYS5vcmcxLmV4YW1wbGUuY29tLE89b3JnMS5leGFtcGxlLmNvbSxMPUR1cmhhbSxTVD1Ob3J0aCBDYXJvbGluYSxDPVVT",
  "organizations": [
    "Org1MSP",
    "Org2MSP"
  ],
  "privateBids": {
    "\u0000bid\u0000PaintingAuction\u000049466271ae879bd009e75a60730a12bfa986e75f263202ab81ccd3deec544a35\u0000": {
      "org": "Org2MSP",
      "hash": "b8eaeb4422b93abdfe4ccb6aa11b745b3d1cb072a99bd3eb3618f081fb1b1f89"
    },
    "\u0000bid\u0000PaintingAuction\u00005e4e637c68833b178739575f6fe09820b019551a8cfbb43a4d172e0aa864dfad\u0000": {
      "org": "Org2MSP",
      "hash": "40107eab7a99dfc2f25d02b8ab840f12fd802a9f86d8d42b78d7b4409b2c15bd"
    },
    "\u0000bid\u0000PaintingAuction\u00008ef83011a5fb791f75ed008337839426f6b87981519e5d58ef5ada39c3044edd\u0000": {
      "org": "Org1MSP",
      "hash": "5cb50a17b5a21c02fc01306e3e9b54f4db67e9a440552ce898bbd7daa62dce0f"
    },
    "\u0000bid\u0000PaintingAuction\u0000915a908c8f2c368f4a3aedd73176656af81ddfab000b11629503403f3d97b185\u0000": {
      "org": "Org1MSP",
      "hash": "a458df18b12dffe4ae6d56a270134c2d55bd53fface034bd24381d0073d46a45"
    }
  },
  "revealedBids": {
    "\u0000bid\u0000PaintingAuction\u00008ef83011a5fb791f75ed008337839426f6b87981519e5d58ef5ada39c3044edd\u0000": {
      "objectType": "bid",
      "price": 800,
      "org": "Org1MSP",
      "bidder": "eDUwOTo6Q049YmlkZGVyMSxPVT1jbGllbnQrT1U9b3JnMStPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMS5leGFtcGxlLmNvbSxPPW9yZzEuZXhhbXBsZS5jb20sTD1EdXJoYW0sU1Q9Tm9ydGggQ2Fyb2xpbmEsQz1VUw=="
    }
  },
  "winner": "",
  "price": 0,
  "status": "closed"
}
```

Bidder3 from Org2 will also reveal their bid:
```
node revealBid.js org2 bidder3 PaintingAuction $BIDDER3_BID_ID
```

If the auction ended now, Bidder1 would win. Let's try to end the auction using the seller identity and see what happens.

```
node endAuction.js org1 seller PaintingAuction
```

The output should look something like the following:

```
--> Submit the transaction to end the auction
2020-11-06T13:16:11.591Z - warn: [TransactionEventHandler]: strategyFail: commit failure for transaction "99feade5b7ec223839200867b57d18971c3e9f923efc95aaeec720727f927366": TransactionError: Commit of transaction 99feade5b7ec223839200867b57d18971c3e9f923efc95aaeec720727f927366 failed on peer peer0.org1.example.com:7051 with status ENDORSEMENT_POLICY_FAILURE
******** FAILED to submit bid: TransactionError: Commit of transaction 99feade5b7ec223839200867b57d18971c3e9f923efc95aaeec720727f927366 failed on peer peer0.org1.example.com:7051 with status ENDORSEMENT_POLICY_FAILURE
```

Instead of ending the auction, the transaction results in an endorsement policy failure. The end of the auction needs to be endorsed by Org2. Before endorsing the transaction, the Org2 peer queries its private data collection for any winning bids that have not yet been revealed. Because Bidder4 created a bid that is above the winning price, the Org2 peer refuses to endorse the transaction that would end the auction.

Before we can end the auction, we need to reveal the bid from bidder4.
```
node revealBid.js org2 bidder4 PaintingAuction $BIDDER4_BID_ID
```

Bidder2 from Org1 would not win the auction in either case. As a result, Bidder2 decides not to reveal their bid.

## End the auction

Now that the winning bids have been revealed, we can end the auction:
```
node endAuction org1 seller PaintingAuction
```

The transaction was successfully endorsed by both Org1 and Org2, who both calculated the same price and winner. The winning bidder is listed along with the price:
```
*** Result: Auction: {
  "objectType": "auction",
  "item": "painting",
  "seller": "eDUwOTo6Q049c2VsbGVyLE9VPWNsaWVudCtPVT1vcmcxK09VPWRlcGFydG1lbnQxOjpDTj1jYS5vcmcxLmV4YW1wbGUuY29tLE89b3JnMS5leGFtcGxlLmNvbSxMPUR1cmhhbSxTVD1Ob3J0aCBDYXJvbGluYSxDPVVT",
  "organizations": [
    "Org1MSP",
    "Org2MSP"
  ],
  "privateBids": {
    "\u0000bid\u0000PaintingAuction\u000049466271ae879bd009e75a60730a12bfa986e75f263202ab81ccd3deec544a35\u0000": {
      "org": "Org2MSP",
      "hash": "b8eaeb4422b93abdfe4ccb6aa11b745b3d1cb072a99bd3eb3618f081fb1b1f89"
    },
    "\u0000bid\u0000PaintingAuction\u00005e4e637c68833b178739575f6fe09820b019551a8cfbb43a4d172e0aa864dfad\u0000": {
      "org": "Org2MSP",
      "hash": "40107eab7a99dfc2f25d02b8ab840f12fd802a9f86d8d42b78d7b4409b2c15bd"
    },
    "\u0000bid\u0000PaintingAuction\u00008ef83011a5fb791f75ed008337839426f6b87981519e5d58ef5ada39c3044edd\u0000": {
      "org": "Org1MSP",
      "hash": "5cb50a17b5a21c02fc01306e3e9b54f4db67e9a440552ce898bbd7daa62dce0f"
    },
    "\u0000bid\u0000PaintingAuction\u0000915a908c8f2c368f4a3aedd73176656af81ddfab000b11629503403f3d97b185\u0000": {
      "org": "Org1MSP",
      "hash": "a458df18b12dffe4ae6d56a270134c2d55bd53fface034bd24381d0073d46a45"
    }
  },
  "revealedBids": {
    "\u0000bid\u0000PaintingAuction\u000049466271ae879bd009e75a60730a12bfa986e75f263202ab81ccd3deec544a35\u0000": {
      "objectType": "bid",
      "price": 900,
      "org": "Org2MSP",
      "bidder": "eDUwOTo6Q049YmlkZGVyNCxPVT1jbGllbnQrT1U9b3JnMitPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMi5leGFtcGxlLmNvbSxPPW9yZzIuZXhhbXBsZS5jb20sTD1IdXJzbGV5LFNUPUhhbXBzaGlyZSxDPVVL"
    },
    "\u0000bid\u0000PaintingAuction\u00005e4e637c68833b178739575f6fe09820b019551a8cfbb43a4d172e0aa864dfad\u0000": {
      "objectType": "bid",
      "price": 700,
      "org": "Org2MSP",
      "bidder": "eDUwOTo6Q049YmlkZGVyMyxPVT1jbGllbnQrT1U9b3JnMitPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMi5leGFtcGxlLmNvbSxPPW9yZzIuZXhhbXBsZS5jb20sTD1IdXJzbGV5LFNUPUhhbXBzaGlyZSxDPVVL"
    },
    "\u0000bid\u0000PaintingAuction\u00008ef83011a5fb791f75ed008337839426f6b87981519e5d58ef5ada39c3044edd\u0000": {
      "objectType": "bid",
      "price": 800,
      "org": "Org1MSP",
      "bidder": "eDUwOTo6Q049YmlkZGVyMSxPVT1jbGllbnQrT1U9b3JnMStPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMS5leGFtcGxlLmNvbSxPPW9yZzEuZXhhbXBsZS5jb20sTD1EdXJoYW0sU1Q9Tm9ydGggQ2Fyb2xpbmEsQz1VUw=="
    }
  },
  "winner": "eDUwOTo6Q049YmlkZGVyNCxPVT1jbGllbnQrT1U9b3JnMitPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMi5leGFtcGxlLmNvbSxPPW9yZzIuZXhhbXBsZS5jb20sTD1IdXJzbGV5LFNUPUhhbXBzaGlyZSxDPVVL",
  "price": 900,
  "status": "ended"
}
```

## Clean up

When your are done using the auction smart contract, you can bring down the network and clean up the environment. In the `auction/application-javascript` directory, run the following command to remove the wallets used to run the applications:
```
rm -rf wallet
```

You can then navigate to the test network directory and bring down the network:
````
cd ../../test-network/
./network.sh down
````
