## Dutch auction

This example allows you to run a [Dutch auction](https://en.wikipedia.org/wiki/Dutch_auction) that sells multiple items of the same good. All items are sold at the price that clears the auction. You also have the option of adding an auditor organization to the auction. If the organizations running the auction cannot agree, or encounter a technical error that prevents them from updating the auction, one of the auction participants can appeal to an auditor organization. The dutch auction smart contract provides an example of how create a complex signature policy by creating a protobuf and then using the policy for state based endorsement.

This tutorial uses the example smart contract to run an auction in which a single seller wants to sell 100 tickets to multiple bidders. If you chose to add an auditor to the auction, you can appeal to the auditor to end the auction by overriding the standard auction endorsement policy.

## Deploy the chaincode

Change into the test network directory.
```
cd fabric-samples/test-network
```

If the test network is already running, run the following command to bring the network down and start from a clean initial state.
```
./network.sh down
```

You can then run the following command to deploy a new network.
```
./network.sh up createChannel -ca
```

Run the following command to deploy the dutch auction smart contract.
```
./network.sh deployCC -ccn auction -ccp ../auction-dutch/chaincode-go/ -ccep "OR('Org1MSP.peer','Org2MSP.peer')" -ccl go
```

Note that we deploy the smart contract with an endorsement policy of `"OR('Org1MSP.peer','Org2MSP.peer')" ` instead of using the default endorsement policy of the majority of orgs on the channel. Either Org1 or Org2 can create an auction without the endorsement of the other organization.

## Add an auditor (optional)

The smart contract allows you to add an auditor organization to the auction. The auditor can add bids, close the auction, or end the auction if participants cannot cooperate. In this tutorial, we will add the Org3 organization to the test network channel and install an auditor specific version of the dutch auction smart contract. This allows you to use Org3 as the auditor organization.

From the `test-network` directory, issue the following commands to add Org3 to the channel:

```
cd addOrg3
./addOrg3.sh up
```

Navigate back to the test network directory:
```
cd ..
```

Set the following environment to interact with the test network as Org3.
```
export PATH=${PWD}/../bin:$PATH
export FABRIC_CFG_PATH=${PWD}/../config/
export CORE_PEER_TLS_ENABLED=true
export CORE_PEER_LOCALMSPID="Org3MSP"
export CORE_PEER_TLS_ROOTCERT_FILE=${PWD}/organizations/peerOrganizations/org3.example.com/peers/peer0.org3.example.com/tls/ca.crt
export CORE_PEER_MSPCONFIGPATH=${PWD}/organizations/peerOrganizations/org3.example.com/users/Admin@org3.example.com/msp
export CORE_PEER_ADDRESS=localhost:11051
```

To deploy the smart contract on the Org3 peer, we need to use the peer lifecycle chaincode commands to install the chaincode package and approve the chaincode definition as Org3. Run the following command to package the auditor version of the dutch auction smart contract:
```
peer lifecycle chaincode package auction.tar.gz --path ../auction-dutch/chaincode-go-auditor/ --lang golang --label auction_1
```
Install the chaincode package on the Org3 peer:
```
peer lifecycle chaincode install auction.tar.gz
```

The next step is to approve the chaincode as the Org3 admin. This requires getting the package ID of the chaincode that we just installed.
```
peer lifecycle chaincode queryinstalled
```

The command should return a response similar to the following:
```
Installed chaincodes on peer:
Package ID: auction_1:8f0d6b6b5a616a1c2b6a9268418f2ee65718acc3c07ea12e123b189b3fb4fb14, Label: auction_1
```

Save the package ID returned by the command above as an environment variable. The package ID will not be the same for all users, so you need to complete this step using the package ID returned from your console.
```
export CC_PACKAGE_ID=auction_1:8f0d6b6b5a616a1c2b6a9268418f2ee65718acc3c07ea12e123b189b3fb4fb14
```

You can now approve the auction chaincode for Org3:
```
peer lifecycle chaincode approveformyorg -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile "${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem" --channelID mychannel --name auction --version 1.0 --package-id $CC_PACKAGE_ID --sequence 1 --signature-policy "OR('Org1MSP.peer','Org2MSP.peer')"
```

The command will start the dutch auction chaincode on the Org3 peer. Note that we did not update the endorsement policy before we added the auditor organization. Only Org1 and Org2 will be able create an auction. The auditor is added the endorsement policy after the auction is created. Because the auditor does not need to create an auction or create new bids, the auditor can run a different version of the smart contract than the auction participants. The auditor version of the smart contract also adds logic to check that the request is submitted by one of the auction participants before the auditor can intervene.

## Install the application dependencies

We will run the dutch auction using a series of Node.js applications. Change into the `application-javascript` directory:
```
cd fabric-samples/auction-dutch/application-javascript
```

From this directory, run the following command to download the application dependencies if you have not done so already:
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

We can use the CA admins of both organizations to register and enroll the identities of the seller that will create the auction and the bidders who will try to purchase the tickets. Run the following command to register and enroll the seller identity that will create the auction. The seller will belong to Org1.
```
node registerEnrollUser.js org1 seller
```

You should see the logs of the seller wallet being created as well. Run the following commands to register and enroll two bidders from Org1 and another three bidders from Org2:
```
node registerEnrollUser.js org1 bidder1
node registerEnrollUser.js org1 bidder2
node registerEnrollUser.js org2 bidder3
node registerEnrollUser.js org2 bidder4
node registerEnrollUser.js org2 bidder5
```

## Create the auction

The seller from Org1 would like to create an auction to sell 100 tickets. Run the following command to use the seller wallet to run the `createAuction.js` application. The seller needs to provide an auction ID, the item to be sold, and the quantity to be sold to create the auction. The seller uses `withAuditor` to indicate that Org3 will be added as the auditor organization. If you do not want to add an auditor, you can provide a value of `noAuditor`. You will see the application query the auction after it is created.
```
node createAuction.js org1 seller auction1 tickets 100 withAuditor
```

Adding an auditor to the auction creates an endorsement policy with the auditor included. Without the auditor, each organization with sellers or bidders participating in the auction is added to the auction endorsement policy. For example, if the auction had two organizations participating in the auction, the auction endorsement policy would be `AND(Org1, Org2)`. However, if the selling organization decides to add an auditor, the auditor organization would be added to the endorsement policy. If the participating organizations disagree, or if a participant has a technical problem, the auditor can join any one of the participating organizations and agree to update the auction. Extending the example above, if the auction with two organizations added an auditor, the auction endorsement policy would be `OR(AND(Org1, Org2), AND(auditor, OR(Org1, Org2)))`.

## Bid on the auction

We can now use the bidder wallets to submit bids to the auction:

### Bid as bidder1

Bidder1 will create a bid to purchase 50 tickets for 80 dollars.
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
  "buyer": "x509::CN=bidder1,OU=client+OU=org1+OU=department1::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US"
}
```

The `bid.js` application also prints the bidID:
```
*** Result ***SAVE THIS VALUE*** BidID: 6630e1bb06e827a2b77023f63677fae8a0ad43126730e450d3252fa58eeb85b1
```

The BidID acts as the unique identifier for the bid. This ID allows you to query the bid using the `queryBid.js` program and add the bid to the auction. Save the bidID returned by the application as an environment variable in your terminal:
```
export BIDDER1_BID_ID=6630e1bb06e827a2b77023f63677fae8a0ad43126730e450d3252fa58eeb85b1
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
  "item": "tickets",
  "seller": "x509::CN=seller,OU=client+OU=org1+OU=department1::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US",
  "quantity": 100,
  "organizations": [
    "Org1MSP"
  ],
  "privateBids": {
    "\u0000bid\u0000auction1\u00006630e1bb06e827a2b77023f63677fae8a0ad43126730e450d3252fa58eeb85b1\u0000": {
      "org": "Org1MSP",
      "hash": "2f7a62152627d69d73e31b62cd4731d32ecc277de0eef4d30b1235891298abf7"
    }
  },
  "revealedBids": {},
  "winners": [],
  "price": 0,
  "status": "open",
  "auditor": true
}
```

### Bid as bidder2

Let's submit another bid. Bidder2 would like to purchase 40 tickets for 50 dollars.
```
node bid.js org1 bidder2 auction1 40 50
```

Save the Bid ID returned by the application:
```
export BIDDER2_BID_ID=5796569dae2e95242eadc5cf1cf8aa24f5ae072d801e7decb2547530de5a65e8
```

Submit bidder2's bid to the auction:
```
node submitBid.js org1 bidder2 auction1 $BIDDER2_BID_ID
```

### Bid as bidder3 from Org2

Bidder3 will bid for 30 tickets at 70 dollars:
```
node bid.js org2 bidder3 auction1 30 70
```

Save the Bid ID returned by the application:
```
export BIDDER3_BID_ID=d52ea4d9b4bc428d395db2d68323bc12cc9b5c1f8617900f459ccd41c38d3c0a
```

Add bidder3's bid to the auction:
```
node submitBid.js org2 bidder3 auction1 $BIDDER3_BID_ID
```

Because bidder3 belongs to Org2, submitting the bid will add Org2 to the list of participating organizations. You can see the Org2 MSP ID has been added to the list of `"organizations"` in the updated auction returned by the application:
```
*** Result: Auction: {
  "objectType": "auction",
  "item": "tickets",
  "seller": "x509::CN=seller,OU=client+OU=org1+OU=department1::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US",
  "quantity": 100,
  "organizations": [
    "Org1MSP",
    "Org2MSP"
  ],
  "privateBids": {
    "\u0000bid\u0000auction1\u00005796569dae2e95242eadc5cf1cf8aa24f5ae072d801e7decb2547530de5a65e8\u0000": {
      "org": "Org1MSP",
      "hash": "598749480aa3af816a829455e1fdac25a44f31c2ae81f911f85d004f44dbbe6c"
    },
    "\u0000bid\u0000auction1\u00006630e1bb06e827a2b77023f63677fae8a0ad43126730e450d3252fa58eeb85b1\u0000": {
      "org": "Org1MSP",
      "hash": "2f7a62152627d69d73e31b62cd4731d32ecc277de0eef4d30b1235891298abf7"
    },
    "\u0000bid\u0000auction1\u0000d52ea4d9b4bc428d395db2d68323bc12cc9b5c1f8617900f459ccd41c38d3c0a\u0000": {
      "org": "Org2MSP",
      "hash": "bf1e9fb80ea3e29780fe13b4781b6dad28fa83b4b5db68bd7e90252875d152fb"
    }
  },
  "revealedBids": {},
  "winners": [],
  "price": 0,
  "status": "open",
  "auditor": true
}
```

Now that a bid from Org2 has been added to the auction, any updates to the auction need to be endorsed by the Org2 peer. The applications will use the `"organizations"` field to specify which organizations need to endorse submitting a new bid, revealing a bid, or updating the auction status.

### Bid as bidder4

Bidder4 from Org2 would like to purchase 15 tickets for 60 dollars:
```
node bid.js org2 bidder4 auction1 15 60
```

Save the Bid ID returned by the application:
```
export BIDDER4_BID_ID=c6464f984bb01e639a46e58b94c496e8bbd829b5e4fa7ffcc150d9a565d45684
```

Add bidder4's bid to the auction:
```
node submitBid.js org2 bidder4 auction1 $BIDDER4_BID_ID
```

### Bid as bidder5

Bidder5 from Org2 will bid for 20 tickets at 60 dollars:
```
node bid.js org2 bidder5 auction1 20 60
```

Save the Bid ID returned by the application:
```
export BIDDER5_BID_ID=f4024ab09b4dacf0a636927414850dde2a2a5e8ec4601e2a0071f5c233248207
```

Add bidder5's bid to the auction:
```
node submitBid.js org2 bidder5 auction1 $BIDDER5_BID_ID
```


## Close the auction

Now that all five bidders have joined the auction, the seller would like to close the auction and allow buyers to reveal their bids. The seller identity that created the auction needs to submit the transaction:
```
node closeAuction.js org1 seller auction1
```

The application will query the auction to allow you to verify that the auction status has changed to closed.

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
  "item": "tickets",
  "seller": "x509::CN=seller,OU=client+OU=org1+OU=department1::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US",
  "quantity": 100,
  "organizations": [
    "Org1MSP",
    "Org2MSP"
  ],
  "privateBids": {
    "\u0000bid\u0000auction1\u00005796569dae2e95242eadc5cf1cf8aa24f5ae072d801e7decb2547530de5a65e8\u0000": {
      "org": "Org1MSP",
      "hash": "598749480aa3af816a829455e1fdac25a44f31c2ae81f911f85d004f44dbbe6c"
    },
    "\u0000bid\u0000auction1\u00006630e1bb06e827a2b77023f63677fae8a0ad43126730e450d3252fa58eeb85b1\u0000": {
      "org": "Org1MSP",
      "hash": "2f7a62152627d69d73e31b62cd4731d32ecc277de0eef4d30b1235891298abf7"
    },
    "\u0000bid\u0000auction1\u0000c6464f984bb01e639a46e58b94c496e8bbd829b5e4fa7ffcc150d9a565d45684\u0000": {
      "org": "Org2MSP",
      "hash": "eefcadf8e9e5cb8322a6e642ab6d5512d62e6d68f37a72b00f5b0d9e580eddb9"
    },
    "\u0000bid\u0000auction1\u0000d52ea4d9b4bc428d395db2d68323bc12cc9b5c1f8617900f459ccd41c38d3c0a\u0000": {
      "org": "Org2MSP",
      "hash": "bf1e9fb80ea3e29780fe13b4781b6dad28fa83b4b5db68bd7e90252875d152fb"
    },
    "\u0000bid\u0000auction1\u0000f4024ab09b4dacf0a636927414850dde2a2a5e8ec4601e2a0071f5c233248207\u0000": {
      "org": "Org2MSP",
      "hash": "de82232141bac06ea3818146fb650dc9930d45b9ceab506ac66942b119eec094"
    }
  },
  "revealedBids": {
    "\u0000bid\u0000auction1\u00006630e1bb06e827a2b77023f63677fae8a0ad43126730e450d3252fa58eeb85b1\u0000": {
      "objectType": "bid",
      "quantity": 50,
      "price": 80,
      "org": "Org1MSP",
      "buyer": "x509::CN=bidder1,OU=client+OU=org1+OU=department1::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US"
    }
  },
  "winners": [],
  "price": 0,
  "status": "closed",
  "auditor": true
}
```
We will add three more bidders, the second bidder from Org1 and two bidders from Org2. Run the following commands to reveal the bidders:
```
node revealBid.js org1 bidder2 auction1 $BIDDER2_BID_ID
node revealBid.js org2 bidder4 auction1 $BIDDER4_BID_ID
node revealBid.js org2 bidder5 auction1 $BIDDER5_BID_ID
```

Let's try to end the auction using the seller identity and see what happens.

```
node endAuction.js org1 seller auction1
```

The output should look something like the following:

```
--> Submit the transaction to end the auction
2021-01-28T16:47:27.501Z - error: [DiscoveryHandler]: compareProposalResponseResults[undefined] - read/writes result sets do not match index=1
2021-01-28T16:47:27.503Z - error: [Transaction]: Error: No valid responses from any peers. Errors:
    peer=undefined, status=grpc, message=Peer endorsements do not match
******** FAILED to submit bid: Error: No valid responses from any peers. Errors:
    peer=undefined, status=grpc, message=Peer endorsements do not match
```

Instead of ending the auction, the transaction results in an endorsement policy failure. The end of the auction needs to be endorsed by Org2. Before endorsing the transaction, the Org2 peer queries its private data collection for any winning bids that have not yet been revealed. Because the price that would clear the auction with the currently revealed bids is lower than the bid of Bidder3, the Org2 peer refuses to endorse the transaction that would end the auction.

In order to end the auction, Org1 would either need to wait for Org2 to reveal the final bid or appeal to the auditor. Depending on if you created the organization with an auditor, you can end the auction with either set of steps.

## End the auction using an auditor

If Org2 is unable to endorse the transaction to end the auction, Org1 can ask the auditor to intervene. The following program gets an endorsement from the Org3 auditor and Org1 to end the auction. As a result, the transaction would meet the auditor component of the state based endorsement policy.
```
node endAuctionwithAuditor org1 seller auction1
```

Even though Org2 has not agreed to the end of the auction, the endorsement Org1 is sufficient to end the auction if the auditor agrees. As part of ending the auction, both Org1 and the auditor need to calculate the same price and the same set of winners. Each winning bidder is listed next to the quantity that was allocated to them.
```
*** Result: Auction: {
  "objectType": "auction",
  "item": "tickets",
  "seller": "x509::CN=seller,OU=client+OU=org1+OU=department1::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US",
  "quantity": 100,
  "organizations": [
    "Org1MSP",
    "Org2MSP"
  ],
  "privateBids": {
    "\u0000bid\u0000auction1\u00005796569dae2e95242eadc5cf1cf8aa24f5ae072d801e7decb2547530de5a65e8\u0000": {
      "org": "Org1MSP",
      "hash": "598749480aa3af816a829455e1fdac25a44f31c2ae81f911f85d004f44dbbe6c"
    },
    "\u0000bid\u0000auction1\u00006630e1bb06e827a2b77023f63677fae8a0ad43126730e450d3252fa58eeb85b1\u0000": {
      "org": "Org1MSP",
      "hash": "2f7a62152627d69d73e31b62cd4731d32ecc277de0eef4d30b1235891298abf7"
    },
    "\u0000bid\u0000auction1\u0000c6464f984bb01e639a46e58b94c496e8bbd829b5e4fa7ffcc150d9a565d45684\u0000": {
      "org": "Org2MSP",
      "hash": "eefcadf8e9e5cb8322a6e642ab6d5512d62e6d68f37a72b00f5b0d9e580eddb9"
    },
    "\u0000bid\u0000auction1\u0000d52ea4d9b4bc428d395db2d68323bc12cc9b5c1f8617900f459ccd41c38d3c0a\u0000": {
      "org": "Org2MSP",
      "hash": "bf1e9fb80ea3e29780fe13b4781b6dad28fa83b4b5db68bd7e90252875d152fb"
    },
    "\u0000bid\u0000auction1\u0000f4024ab09b4dacf0a636927414850dde2a2a5e8ec4601e2a0071f5c233248207\u0000": {
      "org": "Org2MSP",
      "hash": "de82232141bac06ea3818146fb650dc9930d45b9ceab506ac66942b119eec094"
    }
  },
  "revealedBids": {
    "\u0000bid\u0000auction1\u00005796569dae2e95242eadc5cf1cf8aa24f5ae072d801e7decb2547530de5a65e8\u0000": {
      "objectType": "bid",
      "quantity": 40,
      "price": 50,
      "org": "Org1MSP",
      "buyer": "x509::CN=bidder2,OU=client+OU=org1+OU=department1::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US"
    },
    "\u0000bid\u0000auction1\u00006630e1bb06e827a2b77023f63677fae8a0ad43126730e450d3252fa58eeb85b1\u0000": {
      "objectType": "bid",
      "quantity": 50,
      "price": 80,
      "org": "Org1MSP",
      "buyer": "x509::CN=bidder1,OU=client+OU=org1+OU=department1::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US"
    },
    "\u0000bid\u0000auction1\u0000c6464f984bb01e639a46e58b94c496e8bbd829b5e4fa7ffcc150d9a565d45684\u0000": {
      "objectType": "bid",
      "quantity": 15,
      "price": 60,
      "org": "Org2MSP",
      "buyer": "x509::CN=bidder4,OU=client+OU=org2+OU=department1::CN=ca.org2.example.com,O=org2.example.com,L=Hursley,ST=Hampshire,C=UK"
    },
    "\u0000bid\u0000auction1\u0000f4024ab09b4dacf0a636927414850dde2a2a5e8ec4601e2a0071f5c233248207\u0000": {
      "objectType": "bid",
      "quantity": 20,
      "price": 60,
      "org": "Org2MSP",
      "buyer": "x509::CN=bidder5,OU=client+OU=org2+OU=department1::CN=ca.org2.example.com,O=org2.example.com,L=Hursley,ST=Hampshire,C=UK"
    }
  },
  "winners": [
    {
      "buyer": "x509::CN=bidder1,OU=client+OU=org1+OU=department1::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US",
      "quantity": 50
    },
    {
      "buyer": "x509::CN=bidder4,OU=client+OU=org2+OU=department1::CN=ca.org2.example.com,O=org2.example.com,L=Hursley,ST=Hampshire,C=UK",
      "quantity": 15
    },
    {
      "buyer": "x509::CN=bidder5,OU=client+OU=org2+OU=department1::CN=ca.org2.example.com,O=org2.example.com,L=Hursley,ST=Hampshire,C=UK",
      "quantity": 20
    },
    {
      "buyer": "x509::CN=bidder2,OU=client+OU=org1+OU=department1::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US",
      "quantity": 15
    }
  ],
  "price": 50,
  "status": "ended",
  "auditor": true
}
```

The auction allocates tickets to the highest bids first. Because all 100 tickets are sold after allocating tickets to the bid that was submitted at 50, 50 is the `"price"` that clears the auction.

## End the auction without an auditor

If we did not add an auditor to the auction, we need to add the remaining bid so that Org2 will endorse ending the auction.
```
node revealBid.js org2 bidder3 auction1 $BIDDER3_BID_ID
```

Now that all the winning bids have been revealed, we can submit the transaction to end the auction once more.
```
node endAuction org1 seller auction1
```

The transaction was successfully endorsed by both Org1 and Org2, who both calculated the same price and winners of the auction. Each winning bidder is listed next to the quantity that was allocated to them.
```
*** Result: Auction: {
  "objectType": "auction",
  "item": "tickets",
  "seller": "x509::CN=seller,OU=client+OU=org1+OU=department1::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US",
  "quantity": 100,
  "organizations": [
    "Org1MSP",
    "Org2MSP"
  ],
  "privateBids": {
    "\u0000bid\u0000auction1\u0000482b2a68fbbfae329b0b4bc9d70b90f3a55fdcbae5f5274dec34d438efb6847e\u0000": {
      "org": "Org1MSP",
      "hash": "2f7a62152627d69d73e31b62cd4731d32ecc277de0eef4d30b1235891298abf7"
    },
    "\u0000bid\u0000auction1\u000048d93017ac65cff0dd23406cc29918724fd84c8e7014eee30fd492fef760e6a4\u0000": {
      "org": "Org2MSP",
      "hash": "bf1e9fb80ea3e29780fe13b4781b6dad28fa83b4b5db68bd7e90252875d152fb"
    },
    "\u0000bid\u0000auction1\u00005ba4c856224cdc8209b0e42f30a757331e3fb8a8b660b64a55e1bcf688b745ad\u0000": {
      "org": "Org1MSP",
      "hash": "598749480aa3af816a829455e1fdac25a44f31c2ae81f911f85d004f44dbbe6c"
    },
    "\u0000bid\u0000auction1\u000063c8a192dae1332ae42af890f8a966fea2ae8365ca9746447e014a7c0494d64e\u0000": {
      "org": "Org2MSP",
      "hash": "de82232141bac06ea3818146fb650dc9930d45b9ceab506ac66942b119eec094"
    },
    "\u0000bid\u0000auction1\u000066ff6d8bbe81e98654fc417915808031d49e93cd8d7475f15317d801317254fa\u0000": {
      "org": "Org2MSP",
      "hash": "eefcadf8e9e5cb8322a6e642ab6d5512d62e6d68f37a72b00f5b0d9e580eddb9"
    }
  },
  "revealedBids": {
    "\u0000bid\u0000auction1\u0000482b2a68fbbfae329b0b4bc9d70b90f3a55fdcbae5f5274dec34d438efb6847e\u0000": {
      "objectType": "bid",
      "quantity": 50,
      "price": 80,
      "org": "Org1MSP",
      "buyer": "x509::CN=bidder1,OU=client+OU=org1+OU=department1::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US"
    },
    "\u0000bid\u0000auction1\u000048d93017ac65cff0dd23406cc29918724fd84c8e7014eee30fd492fef760e6a4\u0000": {
      "objectType": "bid",
      "quantity": 30,
      "price": 70,
      "org": "Org2MSP",
      "buyer": "x509::CN=bidder3,OU=client+OU=org2+OU=department1::CN=ca.org2.example.com,O=org2.example.com,L=Hursley,ST=Hampshire,C=UK"
    },
    "\u0000bid\u0000auction1\u00005ba4c856224cdc8209b0e42f30a757331e3fb8a8b660b64a55e1bcf688b745ad\u0000": {
      "objectType": "bid",
      "quantity": 40,
      "price": 50,
      "org": "Org1MSP",
      "buyer": "x509::CN=bidder2,OU=client+OU=org1+OU=department1::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US"
    },
    "\u0000bid\u0000auction1\u000063c8a192dae1332ae42af890f8a966fea2ae8365ca9746447e014a7c0494d64e\u0000": {
      "objectType": "bid",
      "quantity": 20,
      "price": 60,
      "org": "Org2MSP",
      "buyer": "x509::CN=bidder5,OU=client+OU=org2+OU=department1::CN=ca.org2.example.com,O=org2.example.com,L=Hursley,ST=Hampshire,C=UK"
    },
    "\u0000bid\u0000auction1\u000066ff6d8bbe81e98654fc417915808031d49e93cd8d7475f15317d801317254fa\u0000": {
      "objectType": "bid",
      "quantity": 15,
      "price": 60,
      "org": "Org2MSP",
      "buyer": "x509::CN=bidder4,OU=client+OU=org2+OU=department1::CN=ca.org2.example.com,O=org2.example.com,L=Hursley,ST=Hampshire,C=UK"
    }
  },
  "winners": [
    {
      "buyer": "x509::CN=bidder1,OU=client+OU=org1+OU=department1::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US",
      "quantity": 50
    },
    {
      "buyer": "x509::CN=bidder3,OU=client+OU=org2+OU=department1::CN=ca.org2.example.com,O=org2.example.com,L=Hursley,ST=Hampshire,C=UK",
      "quantity": 30
    },
    {
      "buyer": "x509::CN=bidder4,OU=client+OU=org2+OU=department1::CN=ca.org2.example.com,O=org2.example.com,L=Hursley,ST=Hampshire,C=UK",
      "quantity": 15
    },
    {
      "buyer": "x509::CN=bidder5,OU=client+OU=org2+OU=department1::CN=ca.org2.example.com,O=org2.example.com,L=Hursley,ST=Hampshire,C=UK",
      "quantity": 5
    }
  ],
  "price": 60,
  "status": "ended",
  "auditor": false
}
```

The auction allocates tickets to the highest bids first. Because all 100 tickets are sold after allocating tickets to the bids that were submitted at 60, 60 is the `"price"` that clears the auction. The first 80 tickets are allocated to Bidder1 and Bidder3. The remaining 20 tickers are allocated to Bidder4 and Bidder5. When bids are tied, the auction smart contract fills the smaller bids first. As a result, Bidder4 is awarded their full bid of 15 tickets, while Bidder5 is allocated the remaining 5 tickets.

## Clean up

When your are done using the auction smart contract, you can bring down the network and clean up the environment. In the `auction-dutch/application-javascript` directory, run the following command to remove the wallets used to run the applications:
```
rm -rf wallet
```

You can then navigate to the test network directory and bring down the network:
````
cd ../../test-network/
./network.sh down
````
