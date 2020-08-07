## Auction sample

The auction sample demonstrates how Hyperledger Fabric can be used to run an auction where bids kept private from other bidders. Instead of displaying the full bid on the public ledger, only a hash of the bid is revealed while bidding is is open. This prevents potential buyers from reading that were already submitted and changing their bids in response. After the bidding period has ended, participants can reveal their bid. The organizations participating in the auction verify that the bid being revealed matches the hash before the full bid is added to the auction. The auction sample smart contract implements a [Dutch auction](https://en.wikipedia.org/wiki/Dutch_auction) for multiple items of the same type. All items are sold at the price of the lowest winning bid.

A potential seller can use the smart contract to create an auction that sells one or more items. The auction is stoed on the public channel ledger and can be read by all channel members. The auctions created by the smart contract are run in three steps:
1. Each auction is created with the status **open**. While the auction is open, bidders can join the auction by adding a hashed bid. Potential buyers store their bids in the implicit private data collection of their organization. After the bid is created, the bidder can add the hash of the bid to the auction. The bid is added to the auction in two steps because the transaction that creates the bid only needs be endorsed by the the peer of the bidders organization, while a transaction that updates the auction may need to be endorsed by multiple organizations. When the bid is added to the auction, the bidders organization is added to he list of organizations that need to endorse any auction updates.
2. When the bidding period is over, the auction is **closed** to prevent additional bids from being added to the auction. The auction being closed allows bidders to reveal their full bids, as long as they reveal the same bid that they added to the auction in the previous step. Only bids that have been revealed are eligible to win the auction.
3. The auction is **ended** to calculate the winners from the set of revealed bids. All organizations participating in the auction calculate the winning bids and the price that clears the auction. The seller can end the auction only if all organizations endorse the same winners and price.

  Before each organization endorses the transaction that ends the auction, the organization queries their private data collection to check no bidder from their organization has created a bid that is higher than the winning price that has not yet been revealed. If there is a winning bid that has not yet been added to the auction, the organization will withhold their endorsement and prevent the auction from being closed. This prevents the seller from ending the auction prematurely, or colluding with buyers to end the auction at an artificially low price?

The sample uses several Fabric features that facilitate privacy and security. Bids are stored in the implicit private data collections to prevent bids from being distributed to other peers in the channel. When bidding is closed, the auction smart contract uses the `GetPrivateDataHash()` API to verify that hash of the bids being stored in private data collections match the hash that was added to the auction and the hash of the revealed bid. State based endorsement is used to add the organization of each bidder to the auction endorsement policy.  The smart contract uses the `GetClientIdentity.GetID()` API to ensure that only the potential buyer can read their bid from the private state and only seller can close or end the auction.

This tutorial uses the smart contract to create an auction to sell 100 tickets to an event. Four bidders that belong to two organizations will bid on the tickets.

## Deploy the chaincode

You can run the auction smart contract using the Fabric test network. Open a command terminal and navigate to the test network directory:
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

Run the following command in the `application-javascript` directory to download the application dependencies:
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

We can use the CA admins of both organizations to register and enroll the identities the seller that will create the auction and the bidders who will try to purchase the tickets.

Run the following command to register and enroll the seller identity that will create the auction. The seller will belong to Org1.
```
node registerEnrollUser.js org1 seller
```

You should see the logs of the seller wallet being created as well. Run the following commands to register 2 bidders from Org1 and another 2 bidders from Org2:
```
node registerEnrollUser.js org1 bidder1
node registerEnrollUser.js org1 bidder2
node registerEnrollUser.js org2 bidder3
node registerEnrollUser.js org2 bidder4
```

## Create the auction

The seller from Org1 would like to create an auction to sell 100 tickets to an event. Run the following command to use the seller wallet to run the `createAuction.js` program. The application will submit a transaction to the network creating the auction on the channel ledger. The organization and identity name are passed to the application to use the wallet that was created by the`registerEnrollUser.js` application. The seller needs to provide the auctionID, the item to be sold, and the quantity to be sold to create the auction:
```
node createAuction.js org1 seller auction1 tickets 100
```

After the transaction is complete, the `createAuction.js` application will query the auction stored in the public channel ledger:
```
*** Result: Auction: {
  "objectType": "auction",
  "ID": "tickets",
  "seller": "eDUwOTo6Q049c2VsbGVyLE9VPWNsaWVudCtPVT1vcmcxK09VPWRlcGFydG1lbnQxOjpDTj1jYS5vcmcxLmV4YW1wbGUuY29tLE89b3JnMS5leGFtcGxlLmNvbSxMPUR1cmhhbSxTVD1Ob3J0aCBDYXJvbGluYSxDPVVT",
  "quantity": 100,
  "bidingOrgs": [
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

The result is the common name and issuer of the seller's certificate:
```
x509::CN=org1admin,OU=admin,O=Hyperledger,ST=North Carolina,C=US::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=USn
```

## Bid on the auction

We can now use the bidder identities to bid for the auction.

### Bid as bidder1

Bidder1 will enter a bid for 50 tickets for 70 dollars.
```
node bid.js org1 bidder1 auction1 50 80
```

The application will query the bid after it is created:
```
*** Result:  Bid: {
  "objectType": "bid",
  "quantity": 50,
  "price": 80,
  "org": "Org1MSP",
  "buyer": "eDUwOTo6Q049YmlkZGVyMSxPVT1jbGllbnQrT1U9b3JnMStPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMS5leGFtcGxlLmNvbSxPPW9yZzEuZXhhbXBsZS5jb20sTD1EdXJoYW0sU1Q9Tm9ydGggQ2Fyb2xpbmEsQz1VUw=="
}
```

Each bid is stored in the implicit data collection of their respective organizations. You can use the `queryBid.js` program to read a bid that is stored in a peers private data collection. The `"buyer"` identity in the bid is the certificate information from the user that created the bid. Only this identity will be able can query the bid from private state or reveal the bid during the auction.

The `bid.js` application also prints the bidID:
```
*** Result ***SAVE THIS VALUE*** BidID: 68d3de99032700d92b2d753c79d5aff2ca79378b169efb6a70ca3e0ea43acb1b
```

The BidID acts as the unique identifier of the bid. This ID allows you to query the bid and add the bid to the auction. Save the bidID from the query result as an environment variable in your terminal:
```
export BIDDER1_BID_ID=68d3de99032700d92b2d753c79d5aff2ca79378b169efb6a70ca3e0ea43acb1b
```
This value will be different for each transaction, so you will need to use the value returned by your application.

After the bid is created in private state, you can add the bid to the auction. Run the following command to add the bid that was just created to the auction:
```
node addBid.js org1 bidder1 auction1 $BIDDER1_BID_ID
```

The hash of bid will be added to the list private bids in that have been added to `auction1`. Storing the hash in the public auction allows users to accurately reveal the bid after bidding is closed. After the bid is added, the application will query the auction to verify that it was updated:
```
*** Result: Auction: {
  "objectType": "auction",
  "ID": "tickets",
  "seller": "eDUwOTo6Q049c2VsbGVyLE9VPWNsaWVudCtPVT1vcmcxK09VPWRlcGFydG1lbnQxOjpDTj1jYS5vcmcxLmV4YW1wbGUuY29tLE89b3JnMS5leGFtcGxlLmNvbSxMPUR1cmhhbSxTVD1Ob3J0aCBDYXJvbGluYSxDPVVT",
  "quantity": 100,
  "bidingOrgs": [
    "Org1MSP"
  ],
  "privateBids": {
    "\u0000bid\u0000auction1\u000068d3de99032700d92b2d753c79d5aff2ca79378b169efb6a70ca3e0ea43acb1b\u0000": {
      "org": "Org1MSP",
      "hash": "584dbad2269a44afb42bdbc7a7a4c08a7cd50deece1eb3fc38d4e49b9342f270"
    }
  },
  "revealedBids": {},
  "winners": [],
  "price": 0,
  "status": "open"
}
```

### Bid as bidder2

Let's submit another bid. Bidder2 would like to purchase 40 tickets for 50 dollars.
```
node bid.js org1 bidder2 auction1 40 50
```

Save the Bid ID returned by the application:
```
export BIDDER2_BID_ID=77dc57876a1bc5cb798863f07eaab6f041608e55c60900b2cbcf0acc7723e8ec
```

Add bidder2's bid to the auction:
```
node addBid.js org1 bidder2 auction1 $BIDDER2_BID_ID
```

### Bid as bidder3 from Org2

Bidder3 will bid for 40 tickets for 70 dollars:
```
node bid.js org2 bidder3 auction1 30 70
```

Save the Bid ID returned by the application:
```
export BIDDER3_BID_ID=7e2d0c33d0ff1030d855e5fb76f2a4eb30589549b4cb6e581da17d3705cbf77e
```

Add bidder3's bid to the auction:
```
node addBid.js org2 bidder3 auction1 $BIDDER3_BID_ID
```

Because bidder3 belongs to Org2, the last transaction will add Org2 to the list of participating organizations. You can see the Org2 MSP ID has been added to the list of `"biddingOrgs"` in the updated auction returned by the application:
```
*** Result: Auction: {
  "objectType": "auction",
  "ID": "tickets",
  "seller": "eDUwOTo6Q049c2VsbGVyLE9VPWNsaWVudCtPVT1vcmcxK09VPWRlcGFydG1lbnQxOjpDTj1jYS5vcmcxLmV4YW1wbGUuY29tLE89b3JnMS5leGFtcGxlLmNvbSxMPUR1cmhhbSxTVD1Ob3J0aCBDYXJvbGluYSxDPVVT",
  "quantity": 100,
  "bidingOrgs": [
    "Org1MSP",
    "Org2MSP"
  ],
  "privateBids": {
    "\u0000bid\u0000auction1\u00005eb3b492a693f0063986519053cc50e1a374b94532a9fa0ef2866a1294afa2f7\u0000": {
      "org": "Org2MSP",
      "hash": "4446c7eb0e2d64165a916ee996348a18716f4c97e632d58d5a8c20eeec5a9238"
    },
    "\u0000bid\u0000auction1\u000068d3de99032700d92b2d753c79d5aff2ca79378b169efb6a70ca3e0ea43acb1b\u0000": {
      "org": "Org1MSP",
      "hash": "584dbad2269a44afb42bdbc7a7a4c08a7cd50deece1eb3fc38d4e49b9342f270"
    },
    "\u0000bid\u0000auction1\u000077dc57876a1bc5cb798863f07eaab6f041608e55c60900b2cbcf0acc7723e8ec\u0000": {
      "org": "Org1MSP",
      "hash": "bbcd0c7c376e6681a76d8c5482c97f8bdcda55c90c5478100c3aef17815c4fd3"
    }
  },
  "revealedBids": {},
  "winners": [],
  "price": 0,
  "status": "open"
}
```

Now that a bid from Org2 has been added to the auction, any auction updated need to be endorsed by the Org2 peer. The applications will use `"bidingOrgs"` field to specify which organizations need to endorse a transaction that adds a bid to the organization, reveals a bid, or change the bid status before submitting the transaction.

### Bid as bidder4

Bidder4 from Org2 would like to purchase 60 tickets for 60 dollars:
```
node bid.js org2 bidder4 auction1 60 60
```

Save the Bid ID returned by the application:
```
export BIDDER4_BID_ID=083478f1af2ba391a5b8d7c590cfb790aedf11578d64ebc4e5efe793555ff212
```

Add bidder2's bid to the auction:
```
node addBid.js org2 bidder4 auction1 $BIDDER4_BID_ID
```

## Close the auction

Now that 4 bidders have joined the auction, the seller would like to close the auction and allow buyers to reveal their bids. The seller identity that created the auction needs to update the auction:
```
node closeAuction.js org1 seller auction1
```

The application will query the auction so you can verify that the auction status has changed to closed. As a test, you can try to create and add a new bid to the auction. The result will be an endorsement policy failure.

## Reveal bids

After the auction is closed, bidders can try to win the auction by revealing their bids. The transaction to reveal a bid needs to pass four checks:
1. The status of the auction is closed.
2. The transaction needs to be issued by the identity that created the bid.
3. The hash of the revealed bid matches the hash of the bid on the channel ledger. This confirms that the bid is the same as what is stored in the organizations private data collection.
4. The hash of the revealed bid matches the hash that was added to the auction. This confirms that the bid was not altered after the auction was closed.

Use the `revealBid.js` application to reveal the bid of Bidder1:
```
node revealBid.js org1 bidder1 auction1 $BIDDER1_BID_ID
```

The full bid details, including the quantity and price, have beed added to the auction:
```
*** Result: Auction: {
  "objectType": "auction",
  "ID": "tickets",
  "seller": "eDUwOTo6Q049c2VsbGVyLE9VPWNsaWVudCtPVT1vcmcxK09VPWRlcGFydG1lbnQxOjpDTj1jYS5vcmcxLmV4YW1wbGUuY29tLE89b3JnMS5leGFtcGxlLmNvbSxMPUR1cmhhbSxTVD1Ob3J0aCBDYXJvbGluYSxDPVVT",
  "quantity": 100,
  "bidingOrgs": [
    "Org1MSP",
    "Org2MSP"
  ],
  "privateBids": {
    "\u0000bid\u0000auction1\u00002d0639c7a4ccc139c3b349b3637986748d460a4304c93a025c345ee208b0ebcb\u0000": {
      "org": "Org2MSP",
      "hash": "d6f661d8b664244ce55065edc2fd95a982221888bb19afdad931b185e187ed4f"
    },
    "\u0000bid\u0000auction1\u00004886fdaa7edacbc22285cf88c5413ab08e4d17eff4e1681ec1b90a318a8c7253\u0000": {
      "org": "Org1MSP",
      "hash": "bbcd0c7c376e6681a76d8c5482c97f8bdcda55c90c5478100c3aef17815c4fd3"
    },
    "\u0000bid\u0000auction1\u00007e2d0c33d0ff1030d855e5fb76f2a4eb30589549b4cb6e581da17d3705cbf77e\u0000": {
      "org": "Org2MSP",
      "hash": "4446c7eb0e2d64165a916ee996348a18716f4c97e632d58d5a8c20eeec5a9238"
    },
    "\u0000bid\u0000auction1\u00009b9fbd48a05e0e971efe4f57d28d9f09c976f0b8abed4482dc84d32e2d8dea55\u0000": {
      "org": "Org1MSP",
      "hash": "584dbad2269a44afb42bdbc7a7a4c08a7cd50deece1eb3fc38d4e49b9342f270"
    }
  },
  "revealedBids": {
    "\u0000bid\u0000auction1\u00009b9fbd48a05e0e971efe4f57d28d9f09c976f0b8abed4482dc84d32e2d8dea55\u0000": {
      "objectType": "bid",
      "quantity": 50,
      "price": 80,
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
node revealBid.js org2 bidder4 auction1 $BIDDER4_BID_ID
```

If the action ended now, the winners of the auction would be Bidder1 and Bidder3 Let's try to end the auction as the seller to see what happens.


```
node endAuction.js org1 seller auction1
```

Instead of ending the auction, the transaction results in an endorsement policy failure. The end of the auction needs to be endorsed by Org2. Before endorsing the transaction, the Org2 peer queries its private data collection to check if any of the bids in its collection would be a potential winner of the auction. If a winning bid has not yet been revealed by its user, the Org2 peer does not endorse the transaction. This protects the bidders from Org2, and prevents the seller from ending the auction prematurely.

In order to end the auction, we need to reveal the bid from bidder3.
```
node revealBid.js org2 bidder3 auction1 $BIDDER3_BID_ID
```

Bidder2 from Org1 would not win the auction in either case, as a sufficient number of higher bids have been already been revealed. As a result, Bidder2 decides not to reveal their bid.

## End the auction

Now that the winning bids have been revealed, we can now end the auction:
```
node endAuction org1 seller auction1
```

The transaction was successfully endorsed by both Org1 and Org2, who both calculated the same price and winners of the auction. Each winning bidder is listed next to the quantity that was allocated to them. The bid from bidder4 clears the auction, and as a result the `"price"` of the ended auction is 60. Because Bidder1 and Bidder3 bid above that price, the first 80 tickets were allocated to them. Bidder4 was allocated the remaining 20 tickets that were left.
```
*** Result: Auction: {
  "objectType": "auction",
  "ID": "tickets",
  "seller": "eDUwOTo6Q049c2VsbGVyLE9VPWNsaWVudCtPVT1vcmcxK09VPWRlcGFydG1lbnQxOjpDTj1jYS5vcmcxLmV4YW1wbGUuY29tLE89b3JnMS5leGFtcGxlLmNvbSxMPUR1cmhhbSxTVD1Ob3J0aCBDYXJvbGluYSxDPVVT",
  "quantity": 100,
  "bidingOrgs": [
    "Org1MSP",
    "Org2MSP"
  ],
  "privateBids": {
    "\u0000bid\u0000auction1\u00002d0639c7a4ccc139c3b349b3637986748d460a4304c93a025c345ee208b0ebcb\u0000": {
      "org": "Org2MSP",
      "hash": "d6f661d8b664244ce55065edc2fd95a982221888bb19afdad931b185e187ed4f"
    },
    "\u0000bid\u0000auction1\u00004886fdaa7edacbc22285cf88c5413ab08e4d17eff4e1681ec1b90a318a8c7253\u0000": {
      "org": "Org1MSP",
      "hash": "bbcd0c7c376e6681a76d8c5482c97f8bdcda55c90c5478100c3aef17815c4fd3"
    },
    "\u0000bid\u0000auction1\u00007e2d0c33d0ff1030d855e5fb76f2a4eb30589549b4cb6e581da17d3705cbf77e\u0000": {
      "org": "Org2MSP",
      "hash": "4446c7eb0e2d64165a916ee996348a18716f4c97e632d58d5a8c20eeec5a9238"
    },
    "\u0000bid\u0000auction1\u00009b9fbd48a05e0e971efe4f57d28d9f09c976f0b8abed4482dc84d32e2d8dea55\u0000": {
      "org": "Org1MSP",
      "hash": "584dbad2269a44afb42bdbc7a7a4c08a7cd50deece1eb3fc38d4e49b9342f270"
    }
  },
  "revealedBids": {
    "\u0000bid\u0000auction1\u00002d0639c7a4ccc139c3b349b3637986748d460a4304c93a025c345ee208b0ebcb\u0000": {
      "objectType": "bid",
      "quantity": 60,
      "price": 60,
      "org": "Org2MSP",
      "buyer": "eDUwOTo6Q049YmlkZGVyNCxPVT1jbGllbnQrT1U9b3JnMitPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMi5leGFtcGxlLmNvbSxPPW9yZzIuZXhhbXBsZS5jb20sTD1IdXJzbGV5LFNUPUhhbXBzaGlyZSxDPVVL"
    },
    "\u0000bid\u0000auction1\u00007e2d0c33d0ff1030d855e5fb76f2a4eb30589549b4cb6e581da17d3705cbf77e\u0000": {
      "objectType": "bid",
      "quantity": 30,
      "price": 70,
      "org": "Org2MSP",
      "buyer": "eDUwOTo6Q049YmlkZGVyMyxPVT1jbGllbnQrT1U9b3JnMitPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMi5leGFtcGxlLmNvbSxPPW9yZzIuZXhhbXBsZS5jb20sTD1IdXJzbGV5LFNUPUhhbXBzaGlyZSxDPVVL"
    },
    "\u0000bid\u0000auction1\u00009b9fbd48a05e0e971efe4f57d28d9f09c976f0b8abed4482dc84d32e2d8dea55\u0000": {
      "objectType": "bid",
      "quantity": 50,
      "price": 80,
      "org": "Org1MSP",
      "buyer": "eDUwOTo6Q049YmlkZGVyMSxPVT1jbGllbnQrT1U9b3JnMStPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMS5leGFtcGxlLmNvbSxPPW9yZzEuZXhhbXBsZS5jb20sTD1EdXJoYW0sU1Q9Tm9ydGggQ2Fyb2xpbmEsQz1VUw=="
    }
  },
  "winners": [
    {
      "buyer": "eDUwOTo6Q049YmlkZGVyMSxPVT1jbGllbnQrT1U9b3JnMStPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMS5leGFtcGxlLmNvbSxPPW9yZzEuZXhhbXBsZS5jb20sTD1EdXJoYW0sU1Q9Tm9ydGggQ2Fyb2xpbmEsQz1VUw==",
      "quantity": 50
    },
    {
      "buyer": "eDUwOTo6Q049YmlkZGVyMyxPVT1jbGllbnQrT1U9b3JnMitPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMi5leGFtcGxlLmNvbSxPPW9yZzIuZXhhbXBsZS5jb20sTD1IdXJzbGV5LFNUPUhhbXBzaGlyZSxDPVVL",
      "quantity": 30
    },
    {
      "buyer": "eDUwOTo6Q049YmlkZGVyNCxPVT1jbGllbnQrT1U9b3JnMitPVT1kZXBhcnRtZW50MTo6Q049Y2Eub3JnMi5leGFtcGxlLmNvbSxPPW9yZzIuZXhhbXBsZS5jb20sTD1IdXJzbGV5LFNUPUhhbXBzaGlyZSxDPVVL",
      "quantity": 20
    }
  ],
  "price": 60,
  "status": "ended"
}
```

## Clean up

When your are done using the auction smart contract, you can bring down the network and clean up the environment. In the `auction/application-javascript` directory, run the following command to remove the wallets that were created to use run the applications:
```
rm -rf wallet
```

You can then navigate to the test network directory and bring down the network:
````
cd ../../test-network/
./network.sh down
````
