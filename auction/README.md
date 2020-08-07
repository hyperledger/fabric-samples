## Auction sample

The auction sample uses Hyperledger Fabric to run an auction where bids kept are private from other bidders. Instead of displaying the full bid on the public ledger, buyers can only see hashes of other bids while bidding is underway. This prevents potential buyers from changing their bids in response to bids that have already been submitted. After the bidding period ends, participants can reveal their bid to try to win the auction. The organizations participating in the auction verify that a revealed bid matches the hash on the public ledger.

A user that wants to sell one or more items can use the smart contract to create an auction. The auction is stored on the channel ledger and can be read by all channel members. The auctions created by the smart contract are run in three steps:
1. Each auction is created with the status **open**. While the auction is open, potential buyers can add new bids to the auction. The full bids of each buyer are stored in the implicit private data collection of their organization. After the bid is created, the bidder can submit the hash of the bid to the auction. A bid is added to the auction in two steps because the transaction that creates the bid only needs be endorsed by a peer of the bidder's organization, while a transaction that updates the auction may need to be endorsed by multiple organizations. When the bid is added to the auction, the bidder's organization is added to the list of organizations that need to endorse any updates to the auction.
2. The auction is **closed** to prevent additional bids from being added to the auction. After the auction is closed, the bidders that submitted bids to the auction can reveal their full bid. Only revealed bids can win the auction.
3. The auction is **ended** to calculate the winners from the set of revealed bids. All organizations participating in the auction calculate the price that clears the auction and the winning set of bids. The seller can end the auction only if all bidding organizations endorse the same winners and price.

  Before endorsing the transaction that ends the auction, each organization queries the implicit private data collection on their peers to check if any organization member has a winning bid that has not yet been revealed. If a winning bid is found, the organization will withhold their endorsement and prevent the auction from being closed. This prevents the seller from ending the auction prematurely, or colluding with buyers to end the auction at an artificially low price.

The sample uses several Fabric features to make the auction private and secure. Bids are stored in private data collections to prevent bids from being distributed to other peers in the channel. When bidding is closed, the auction smart contract uses the `GetPrivateDataHash()` API to verify that the bid stored in private data is the same bid as the one that is being revealed. State based endorsement is used to add the organization of each bidder to the auction endorsement policy. The smart contract uses the `GetClientIdentity.GetID()` API to ensure that only the potential buyer can read their bid from private state and only the seller can close or end the auction.

This tutorial uses the auction smart contract in a scenario where one seller wants to sell a valuable painting. Four potential buyers from two different organizations will submit bids to purchase the painting. You can also use the auction smart contract implement a [Dutch auction](https://en.wikipedia.org/wiki/Dutch_auction) for multiple items of the same type. To run an auction that sells multiple items, see [Running a Dutch auction](dutch-auction).

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
./network.sh deployCC -ccn auction -ccp ../auction/chaincode-go/ -ccep "OR('Org1MSP.peer','Org2MSP.peer')"
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

The seller from Org1 would like to create an auction for the painting. Run the following command to use the seller wallet to run the `createAuction.js` application. The program will submit a transaction to the network that creates the auction on the channel ledger. The organization and identity name are passed to the application to use the wallet that was created by the `registerEnrollUser.js` application. The seller needs to provide an ID for the auction, the item to be sold, and the quantity to be sold to create the auction:
```
node createAuction.js org1 seller auction1 painting 1
```

After the transaction is complete, the `createAuction.js` application will query the auction stored in the public channel ledger:
```
*** Result: Auction: {
  "objectType": "auction",
  "item": "painting",
  "seller": "eDUwOTo6Q049c2VsbGVyLE9VPWNsaWVudCtPVT1vcmcxK09VPWRlcGFydG1lbnQxOjpDTj1jYS5vcmcxLmV4YW1wbGUuY29tLE89b3JnMS5leGFtcGxlLmNvbSxMPUR1cmhhbSxTVD1Ob3J0aCBDYXJvbGluYSxDPVVT",
  "quantity": 1,
  "organizations": [
    "Org1MSP"
  ],
  "privateBids": {},
  "revealedBids": {},
  "winners": [],
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
node bid.js org1 bidder1 auction1 1 800
```

The application will query the bid after it is created:
```
*** Result:  Bid: {
  "objectType": "bid",
  "quantity": 1,
  "price": 800,
  "org": "Org1MSP",
  "buyer": "eDUwOTo6Q049YmlkZGVyMSxPVT1jbGllbnQrT1U9b3JnMStPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMS5leGFtcGxlLmNvbSxPPW9yZzEuZXhhbXBsZS5jb20sTD1EdXJoYW0sU1Q9Tm9ydGggQ2Fyb2xpbmEsQz1VUw=="
}
```

The bid is stored in the Org1 implicit data collection. The `"buyer"` parameter is the information from the certificate of the user that created the bid. Only this identity will be able to query the bid from private state or reveal the bid during the auction.

The `bid.js` application also prints the bidID:
```
*** Result ***SAVE THIS VALUE*** BidID: 68d3de99032700d92b2d753c79d5aff2ca79378b169efb6a70ca3e0ea43acb1b
```

The BidID acts as the unique identifier for the bid. This ID allows you to query the bid using the `queryBid.js` program and add the bid to the auction. Save the bidID returned by the application as an environment variable in your terminal:
```
export BIDDER1_BID_ID=68d3de99032700d92b2d753c79d5aff2ca79378b169efb6a70ca3e0ea43acb1b
```
This value will be different for each transaction, so you will need to use the value returned in your terminal.

Now that the bid has been created, you can submit the bid to the auction. Run the following command to submit the bid that was just created:
```
node submitBid.js org1 bidder1 auction1 $BIDDER1_BID_ID
```

The hash of bid is added to the list of private bids in that have been submitted to `auction1`. Storing the hash on the public auction ledger allows users to prove the accuracy of the bids they reveal once bidding is closed. The application queries the auction to verify that the bid was added:
```
*** Result: Auction: {
  "objectType": "auction",
  "item": "painting",
  "seller": "eDUwOTo6Q049c2VsbGVyLE9VPWNsaWVudCtPVT1vcmcxK09VPWRlcGFydG1lbnQxOjpDTj1jYS5vcmcxLmV4YW1wbGUuY29tLE89b3JnMS5leGFtcGxlLmNvbSxMPUR1cmhhbSxTVD1Ob3J0aCBDYXJvbGluYSxDPVVT",
  "quantity": 1,
  "organizations": [
    "Org1MSP"
  ],
  "privateBids": {
    "\u0000bid\u0000auction1\u000003382872f8f9dc94f211385d3d127f155e22de1bee8e112dcb90beb3e78f2722\u0000": {
      "org": "Org1MSP",
      "hash": "9345d28bb40b7026cc9e14743147356ae1cac99ced8190fe72c9be69fbfc4b71"
    }
  },
  "revealedBids": {},
  "winners": [],
  "price": 0,
  "status": "open"
}
```

### Bid as bidder2

Let's submit another bid. Bidder2 would like to purchase the painting for 500 dollars.
```
node bid.js org1 bidder2 auction1 1 500
```

Save the Bid ID returned by the application:
```
export BIDDER2_BID_ID=77dc57876a1bc5cb798863f07eaab6f041608e55c60900b2cbcf0acc7723e8ec
```

Submit bidder2's bid to the auction:
```
node submitBid.js org1 bidder2 auction1 $BIDDER2_BID_ID
```

### Bid as bidder3 from Org2

Bidder3 will bid 700 dollars for the painting:
```
node bid.js org2 bidder3 auction1 1 700
```

Save the Bid ID returned by the application:
```
export BIDDER3_BID_ID=7e2d0c33d0ff1030d855e5fb76f2a4eb30589549b4cb6e581da17d3705cbf77e
```

Add bidder3's bid to the auction:
```
node submitBid.js org2 bidder3 auction1 $BIDDER3_BID_ID
```

Because bidder3 belongs to Org2, submitting the bid will add Org2 to the list of participating organizations. You can see the Org2 MSP ID has been added to the list of `"organizations"` in the updated auction returned by the application:
```
*** Result: Auction: {
  "objectType": "auction",
  "item": "painting",
  "seller": "eDUwOTo6Q049c2VsbGVyLE9VPWNsaWVudCtPVT1vcmcxK09VPWRlcGFydG1lbnQxOjpDTj1jYS5vcmcxLmV4YW1wbGUuY29tLE89b3JnMS5leGFtcGxlLmNvbSxMPUR1cmhhbSxTVD1Ob3J0aCBDYXJvbGluYSxDPVVT",
  "quantity": 1,
  "organizations": [
    "Org1MSP",
    "Org2MSP"
  ],
  "privateBids": {
    "\u0000bid\u0000auction1\u000003382872f8f9dc94f211385d3d127f155e22de1bee8e112dcb90beb3e78f2722\u0000": {
      "org": "Org1MSP",
      "hash": "9345d28bb40b7026cc9e14743147356ae1cac99ced8190fe72c9be69fbfc4b71"
    },
    "\u0000bid\u0000auction1\u0000347e08fbbf766a0f3678c3c6e05b0613026fa4d4619ad80869773938ed893f8d\u0000": {
      "org": "Org2MSP",
      "hash": "ecaf72ecd6daf27889434491b0e5580c94bd682d8b0283c142b085d51fcf1f69"
    },
    "\u0000bid\u0000auction1\u0000e624a5f04fb8878e0d20bf10649f06113771eba6ea4c58f46d17264d1b492a46\u0000": {
      "org": "Org1MSP",
      "hash": "8434bfe683c4436484176e6c9b21a5d684c0710fce9fd2506af4e05bb0f8a3ea"
    }
  },
  "revealedBids": {},
  "winners": [],
  "price": 0,
  "status": "open"
}
```

Now that a bid from Org2 has been added to the auction, any updates to the auction need to be endorsed by the Org2 peer. The applications will use the `"organizations"` field to specify which organizations need to endorse submitting a new bid, revealing a bid, or updating the auction status.

### Bid as bidder4

Bidder4 from Org2 would like to purchase the painting for 900 dollars:
```
node bid.js org2 bidder4 auction1 1 900
```

Save the Bid ID returned by the application:
```
export BIDDER4_BID_ID=083478f1af2ba391a5b8d7c590cfb790aedf11578d64ebc4e5efe793555ff212
```

Add bidder4's bid to the auction:
```
node submitBid.js org2 bidder4 auction1 $BIDDER4_BID_ID
```

## Close the auction

Now that all four bidders have joined the auction, the seller would like to close the auction and allow buyers to reveal their bids. The seller identity that created the auction needs to submit the transaction:
```
node closeAuction.js org1 seller auction1
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
node revealBid.js org1 bidder1 auction1 $BIDDER1_BID_ID
```

The full bid details, including the quantity and price, are now visible:
```
*** Result: Auction: {
  "objectType": "auction",
  "item": "painting",
  "seller": "eDUwOTo6Q049c2VsbGVyLE9VPWNsaWVudCtPVT1vcmcxK09VPWRlcGFydG1lbnQxOjpDTj1jYS5vcmcxLmV4YW1wbGUuY29tLE89b3JnMS5leGFtcGxlLmNvbSxMPUR1cmhhbSxTVD1Ob3J0aCBDYXJvbGluYSxDPVVT",
  "quantity": 100,
  "organizations": [
    "Org1MSP",
    "Org2MSP"
  ],
  "privateBids": {
    "\u0000bid\u0000auction1\u00002fcaee344b361a95701dc03ca4417c6c158845f9e4be3b2555b17c2b691c5ec6\u0000": {
      "org": "Org2MSP",
      "hash": "d6f661d8b664244ce55065edc2fd95a982221888bb19afdad931b185e187ed4f"
    },
    "\u0000bid\u0000auction1\u000083d529d37ea3518c67135fa8b9bf0bff33053bed0e2f38cb6a7ff75882efcf95\u0000": {
      "org": "Org2MSP",
      "hash": "4446c7eb0e2d64165a916ee996348a18716f4c97e632d58d5a8c20eeec5a9238"
    },
    "\u0000bid\u0000auction1\u00009e445520af04c8a3c05b484408f0406c9b09503903a9ed9b093f5d894f83dfea\u0000": {
      "org": "Org1MSP",
      "hash": "584dbad2269a44afb42bdbc7a7a4c08a7cd50deece1eb3fc38d4e49b9342f270"
    },
    "\u0000bid\u0000auction1\u0000f3304fd81664f304706ff39b5a978af94bc3e055e5bf32aed1c25b35df24c136\u0000": {
      "org": "Org1MSP",
      "hash": "bbcd0c7c376e6681a76d8c5482c97f8bdcda55c90c5478100c3aef17815c4fd3"
    }
  },
  "revealedBids": {
    "\u0000bid\u0000auction1\u00009e445520af04c8a3c05b484408f0406c9b09503903a9ed9b093f5d894f83dfea\u0000": {
      "objectType": "bid",
      "quantity": 1,
      "price": 800,
      "org": "Org1MSP",
      "buyer": "eDUwOTo6Q049YmlkZGVyMSxPVT1jbGllbnQrT1U9b3JnMStPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMS5leGFtcGxlLmNvbSxPPW9yZzEuZXhhbXBsZS5jb20sTD1EdXJoYW0sU1Q9Tm9ydGggQ2Fyb2xpbmEsQz1VUw=="
    }
  },
  "winners": [],
  "price": 0,
  "status": "closed"
}
```

Bidder3 from Org2 will also reveal their bid:
```
node revealBid.js org2 bidder3 auction1 $BIDDER3_BID_ID
```

If the action ended now, Bidder1 would win the auction. Let's try to end the auction using the seller identity and see what happens.

```
node endAuction.js org1 seller auction1
```

Instead of ending the auction, the transaction results in an endorsement policy failure. The end of the auction needs to be endorsed by Org2. Before endorsing the transaction, the Org2 peer queries the Org2 implicit data collection to look for a winning bid that has not yet been revealed. Because Bidder4 created a bid that is above the winning price, the Org2 peer refuses to endorse the transaction that ends the auction.

Before we can end the auction, we need to reveal the bid from bidder4.
```
node revealBid.js org2 bidder4 auction1 $BIDDER4_BID_ID
```

Bidder2 from Org1 would not win the auction in either case. As a result, Bidder2 decides not to reveal their bid.

## End the auction

Now that the winning bids have been revealed, we can end the auction:
```
node endAuction org1 seller auction1
```

The transaction was successfully endorsed by both Org1 and Org2, who both calculated the same price and winner of the auction.
```
*** Result: Auction: {
  "objectType": "auction",
  "item": "painting",
  "seller": "eDUwOTo6Q049c2VsbGVyLE9VPWNsaWVudCtPVT1vcmcxK09VPWRlcGFydG1lbnQxOjpDTj1jYS5vcmcxLmV4YW1wbGUuY29tLE89b3JnMS5leGFtcGxlLmNvbSxMPUR1cmhhbSxTVD1Ob3J0aCBDYXJvbGluYSxDPVVT",
  "quantity": 1,
  "organizations": [
    "Org1MSP",
    "Org2MSP"
  ],
  "privateBids": {
    "\u0000bid\u0000auction1\u000003382872f8f9dc94f211385d3d127f155e22de1bee8e112dcb90beb3e78f2722\u0000": {
      "org": "Org1MSP",
      "hash": "9345d28bb40b7026cc9e14743147356ae1cac99ced8190fe72c9be69fbfc4b71"
    },
    "\u0000bid\u0000auction1\u0000347e08fbbf766a0f3678c3c6e05b0613026fa4d4619ad80869773938ed893f8d\u0000": {
      "org": "Org2MSP",
      "hash": "ecaf72ecd6daf27889434491b0e5580c94bd682d8b0283c142b085d51fcf1f69"
    },
    "\u0000bid\u0000auction1\u0000e624a5f04fb8878e0d20bf10649f06113771eba6ea4c58f46d17264d1b492a46\u0000": {
      "org": "Org1MSP",
      "hash": "8434bfe683c4436484176e6c9b21a5d684c0710fce9fd2506af4e05bb0f8a3ea"
    },
    "\u0000bid\u0000auction1\u0000f67eed2c00812d02482d40a652d353c505f3161cd6837077443ee2f425413f5c\u0000": {
      "org": "Org2MSP",
      "hash": "0c49d016c894b5623bc0be211409ebb0561c272c65f9faf2959002b0168b238c"
    }
  },
  "revealedBids": {
    "\u0000bid\u0000auction1\u000003382872f8f9dc94f211385d3d127f155e22de1bee8e112dcb90beb3e78f2722\u0000": {
      "objectType": "bid",
      "quantity": 1,
      "price": 800,
      "org": "Org1MSP",
      "buyer": "eDUwOTo6Q049YmlkZGVyMSxPVT1jbGllbnQrT1U9b3JnMStPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMS5leGFtcGxlLmNvbSxPPW9yZzEuZXhhbXBsZS5jb20sTD1EdXJoYW0sU1Q9Tm9ydGggQ2Fyb2xpbmEsQz1VUw=="
    },
    "\u0000bid\u0000auction1\u0000347e08fbbf766a0f3678c3c6e05b0613026fa4d4619ad80869773938ed893f8d\u0000": {
      "objectType": "bid",
      "quantity": 1,
      "price": 700,
      "org": "Org2MSP",
      "buyer": "eDUwOTo6Q049YmlkZGVyMyxPVT1jbGllbnQrT1U9b3JnMitPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMi5leGFtcGxlLmNvbSxPPW9yZzIuZXhhbXBsZS5jb20sTD1IdXJzbGV5LFNUPUhhbXBzaGlyZSxDPVVL"
    },
    "\u0000bid\u0000auction1\u0000f67eed2c00812d02482d40a652d353c505f3161cd6837077443ee2f425413f5c\u0000": {
      "objectType": "bid",
      "quantity": 1,
      "price": 900,
      "org": "Org2MSP",
      "buyer": "eDUwOTo6Q049YmlkZGVyNCxPVT1jbGllbnQrT1U9b3JnMitPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMi5leGFtcGxlLmNvbSxPPW9yZzIuZXhhbXBsZS5jb20sTD1IdXJzbGV5LFNUPUhhbXBzaGlyZSxDPVVL"
    }
  },
  "winners": [
    {
      "buyer": "eDUwOTo6Q049YmlkZGVyNCxPVT1jbGllbnQrT1U9b3JnMitPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMi5leGFtcGxlLmNvbSxPPW9yZzIuZXhhbXBsZS5jb20sTD1IdXJzbGV5LFNUPUhhbXBzaGlyZSxDPVVL",
      "quantity": 1
    }
  ],
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
