# UTXO token scenario

The UTXO token smart contract demonstrates how to create and transfer fungible tokens using a UTXO (unspent transaction output) model. In a UTXO model, unspent transaction outputs representing a number of tokens can be 'spent' to transfer tokens between participants.
An unspent transaction output can only be spent once, and the full value must be completely spent. A transaction that spends UTXOs as input will also generate new UTXOs as outputs, where the value of the inputs must equal the value of the outputs. As an example, if you own an unspent transaction output representing 5000 tokens, and you need to transfer 100 tokens to a recipient, the transaction would spend the 5000 token UTXO as input, create a new 100 token UTXO output owned by the recipient, and return a new 4900 token UTXO to you as 'change'.

Each UTXO in this sample has a key derived from the transaction id that created it, as well as a number of tokens, and an owner that is authorized to spend the tokens.
Ownership of each UTXO could be represented at the organization level or client identity level. In this sample UTXO ownership is based on a client identity, where the client ID is simply a base64-encoded concatenation of the issuer and subject from the client identity's enrollment certificate. The client ID can therefore be used as the payment address when transferring tokens in a UTXO transaction.

While a transfer transaction spends UTXOs and creates new UTXOs for the recipient(s), a mint transaction can create new UTXOs. In this sample it is assumed that only one organization (played by Org1) is in a central banker role and can mint new tokens owned by their client ID. Any client from any organization can transfer tokens in a UTXO transaction.

In this tutorial, you will mint and transfer tokens as follows:

- A member of Org1 uses the `Mint` function to create a UTXO representing a number of tokens. The `Mint` function reads the certificate information of the client identity that submitted the transaction using the `GetClientIdentity.GetID()` API and assigns the UTXO ownership to the minter client ID.
- The same minter client will then use the `Transfer` function to transfer the requested number of tokens to a recipient. The minted UTXO key gets passed as input to the `Transfer` function, a UTXO output representing the number of transferred tokens gets created for the recipient, and another UTXO output representing the 'change' gets created for the minter. It is assumed that the recipient has provided their client ID to the transfer caller out of band. The recipient can then transfer tokens to other registered users in the same fashion.

## Bring up the test network

You can run the UTXO token transfer scenario using the Fabric test network. Open a command terminal and navigate to the test network directory in your local clone of the `fabric-samples`. We will operate from the `test-network` directory for the remainder of the tutorial.
```
cd fabric-samples/test-network
```

Run the following command to start the test network:
```
./network.sh up createChannel -ca
```

The test network is deployed with two peer organizations. The `createChannel` flag deploys the network with a single channel named `mychannel` with Org1 and Org2 as channel members.
The -ca flag is used to deploy the network using certificate authorities. This allows you to use each organization's CA to register and enroll new users for this tutorial.

## Deploy the smart contract to the channel

You can use the test network script to deploy the UTXO token contract to the channel that was just created. Deploy the smart contract to `mychannel` using the following command:
```
./network.sh deployCC -ccn token_utxo -ccp ../token-utxo/chaincode-go/ -ccl go
```

The above command deploys the go chaincode with short name `token_utxo`. The smart contract will utilize the default endorsement policy of majority of channel members.
Since the channel has two members, this implies that we'll need to get peer endorsements from 2 out of the 2 channel members.

Now you are ready to call the deployed smart contract via peer CLI calls. But let's first create the client identities for our scenario.

## Register identities

The smart contract supports UTXO ownership based on individual client identities from organizations that are members of the channel. In our scenario, the minter of the tokens will be a member of Org1, while the recipient will belong to Org2. To highlight the connection between the `GetClientIdentity().GetID()` API and the information within a user's certificate, we will register two new identities using the Org1 and Org2 Certificate Authorities (CA's), and then use the CA's to generate each identity's certificate and private key.

First, we need to set the following environment variables to use the the Fabric CA client (and subsequent commands)
```
export PATH=${PWD}/../bin:${PWD}:$PATH
export FABRIC_CFG_PATH=$PWD/../config/
```

The terminal we have been using will represent Org1. We will use the Org1 CA to create the minter identity. Set the Fabric CA client home to the MSP of the Org1 CA admin (this identity was generated by the test network script):
```
export FABRIC_CA_CLIENT_HOME=${PWD}/organizations/peerOrganizations/org1.example.com/
```

You can register a new minter client identity using the `fabric-ca-client` tool:
```
fabric-ca-client register --caname ca-org1 --id.name minter --id.secret minterpw --id.type client --tls.certfiles ${PWD}/organizations/fabric-ca/org1/tls-cert.pem
```

You can now generate the identity certificates and MSP folder by providing the enroll name and secret to the enroll command:
```
fabric-ca-client enroll -u https://minter:minterpw@localhost:7054 --caname ca-org1 -M ${PWD}/organizations/peerOrganizations/org1.example.com/users/minter@org1.example.com/msp --tls.certfiles ${PWD}/organizations/fabric-ca/org1/tls-cert.pem
```

Run the command below to copy the Node OU configuration file into the minter identity MSP folder.
```
cp ${PWD}/organizations/peerOrganizations/org1.example.com/msp/config.yaml ${PWD}/organizations/peerOrganizations/org1.example.com/users/minter@org1.example.com/msp/config.yaml
```

Open a new terminal to represent Org2 and navigate to fabric-samples/test-network. We'll use the Org2 CA to create the Org2 recipient identity. Set the Fabric CA client home to the MSP of the Org2 CA admin:
```
cd fabric-samples/test-network
export PATH=${PWD}/../bin:${PWD}:$PATH
export FABRIC_CA_CLIENT_HOME=${PWD}/organizations/peerOrganizations/org2.example.com/
```

You can register a recipient client identity using the `fabric-ca-client` tool:
```
fabric-ca-client register --caname ca-org2 --id.name recipient --id.secret recipientpw --id.type client --tls.certfiles ${PWD}/organizations/fabric-ca/org2/tls-cert.pem
```

We can now enroll to generate the identity certificates and MSP folder:
```
fabric-ca-client enroll -u https://recipient:recipientpw@localhost:8054 --caname ca-org2 -M ${PWD}/organizations/peerOrganizations/org2.example.com/users/recipient@org2.example.com/msp --tls.certfiles ${PWD}/organizations/fabric-ca/org2/tls-cert.pem
```

Run the command below to copy the Node OU configuration file into the recipient identity MSP folder.
```
cp ${PWD}/organizations/peerOrganizations/org2.example.com/msp/config.yaml ${PWD}/organizations/peerOrganizations/org2.example.com/users/recipient@org2.example.com/msp/config.yaml
```

## Mint some tokens

Now that we have created the identity of the minter, we can invoke the smart contract to mint some tokens.
Shift back to the Org1 terminal, we'll set the following environment variables to operate the `peer` CLI as the minter identity from Org1.
```
export CORE_PEER_TLS_ENABLED=true
export CORE_PEER_LOCALMSPID="Org1MSP"
export CORE_PEER_MSPCONFIGPATH=${PWD}/organizations/peerOrganizations/org1.example.com/users/minter@org1.example.com/msp
export CORE_PEER_TLS_ROOTCERT_FILE=${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt
export CORE_PEER_ADDRESS=localhost:7051
export TARGET_TLS_OPTIONS="-o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem --peerAddresses localhost:7051 --tlsRootCertFiles ${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt --peerAddresses localhost:9051 --tlsRootCertFiles ${PWD}/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt"
```

The last environment variable above will be utilized within the CLI invoke commands to set the target peers for endorsement, and the target ordering service endpoint and TLS options.

We can then invoke the smart contract to mint 5000 tokens:
```
peer chaincode invoke $TARGET_TLS_OPTIONS -C mychannel -n token_utxo -c '{"function":"Mint","Args":["5000"]}'
```

The mint function validated that the client is a member of the minter organization, and then created a UTXO with 5000 tokens belonging to the minter client identity.
The function returns the UTXO that was created so that we can inspect it. Here is the returned UTXO with JSON formatting applied:
```
{
   "utxo_key":"c3706696c537e7bd6940fedfd52f4a3a630d253297db0ecc2b3ba514b45f5e7c.0",
   "owner":"eDUwOTo6Q049bWludGVyLE9VPWNsaWVudCxPPUh5cGVybGVkZ2VyLFNUPU5vcnRoIENhcm9saW5hLEM9VVM6OkNOPWNhLm9yZzEuZXhhbXBsZS5jb20sTz1vcmcxLmV4YW1wbGUuY29tLEw9RHVyaGFtLFNUPU5vcnRoIENhcm9saW5hLEM9VVM=",
   "amount":5000
}
```

Notice that the utxo_key is set to the transaction id, concatenated with ".0" to indicate that this is the first (and only) UTXO output of the transaction. Your transaction id will be different and unique. The owner is set to the minter's client ID, meaning that only this client can spend the UTXO, and the amount is "5000".

The utxo_key that was created will be needed when you spend the UTXO in the Transfer function below. Copy the utxo_key value (including the ".0") so that you'll have it available for the Transfer function.

We can check the minter client's total set of owned UTXOs by calling the `ClientUTXOs` function.
```
peer chaincode query -C mychannel -n token_utxo -c '{"function":"ClientUTXOs","Args":[]}'
```

The same minted UTXO is returned.


## Transfer tokens

The minter intends to transfer 100 tokens to the Org2 recipient, but first the Org2 recipient needs to provide their own client ID as the payment address.
A client can derive their client ID from their own public certificate, but to be sure the client ID is accurate, the contract has a `ClientID` utility function that simply looks at the callers certificate and returns the calling client's ID.
Let's prepare the Org2 terminal by setting the environment variables for the Org2 recipient user.
```
export FABRIC_CFG_PATH=$PWD/../config/
export CORE_PEER_TLS_ENABLED=true
export CORE_PEER_LOCALMSPID="Org2MSP"
export CORE_PEER_MSPCONFIGPATH=${PWD}/organizations/peerOrganizations/org2.example.com/users/recipient@org2.example.com/msp
export CORE_PEER_TLS_ROOTCERT_FILE=${PWD}/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt
export CORE_PEER_ADDRESS=localhost:9051
```

Using the Org2 terminal, the Org2 recipient user can retrieve their own client ID:
```
peer chaincode query -C mychannel -n token_utxo -c '{"function":"ClientID","Args":[]}'
```

The function returns of recipient's client ID:
```
eDUwOTo6Q049cmVjaXBpZW50LE9VPWNsaWVudCxPPUh5cGVybGVkZ2VyLFNUPU5vcnRoIENhcm9saW5hLEM9VVM6OkNOPWNhLm9yZzIuZXhhbXBsZS5jb20sTz1vcmcyLmV4YW1wbGUuY29tLEw9SHVyc2xleSxTVD1IYW1wc2hpcmUsQz1VSw==
```

Let's base64 decode the client ID to make sure it is the Org2 recipient user:
```
echo eDUwOTo6Q049cmVjaXBpZW50LE9VPWNsaWVudCxPPUh5cGVybGVkZ2VyLFNUPU5vcnRoIENhcm9saW5hLEM9VVM6OkNOPWNhLm9yZzIuZXhhbXBsZS5jb20sTz1vcmcyLmV4YW1wbGUuY29tLEw9SHVyc2xleSxTVD1IYW1wc2hpcmUsQz1VSw== | base64 --decode
```

The result shows that the subject and issuer is indeed the recipient user from Org2:
```
x509::CN=recipient,OU=client,O=Hyperledger,ST=North Carolina,C=US::CN=ca.org2.example.com,O=org2.example.com,L=Hursley,ST=Hampshire,C=UK
```

After the Org2 recipient provides their client ID to the minter, the minter can initiate a transfer of tokens. We'll pass in the utxo_key of the UTXO with 5000 tokens to spend, and request that two new UTXOs get created, a UTXO with 100 tokens for the recipient, and a UTXO with 4900 tokens for the minter as the 'change'. Since the contract will create the UTXO output keys, we'll initially leave the output keys blank.
Back in the Org1 terminal, request the UTXO transfer. **Replace YOUR_UTXO_KEY below with the key you saved earlier**:
```
peer chaincode invoke $TARGET_TLS_OPTIONS -C mychannel -n token_utxo -c '{"function":"Transfer","Args":["[\"YOUR_UTXO_KEY\"]"," [{\"utxo_key\":\"\",\"owner\":\"eDUwOTo6Q049cmVjaXBpZW50LE9VPWNsaWVudCxPPUh5cGVybGVkZ2VyLFNUPU5vcnRoIENhcm9saW5hLEM9VVM6OkNOPWNhLm9yZzIuZXhhbXBsZS5jb20sTz1vcmcyLmV4YW1wbGUuY29tLEw9SHVyc2xleSxTVD1IYW1wc2hpcmUsQz1VSw==\",\"amount\":100},{\"utxo_key\":\"\",\"owner\":\"eDUwOTo6Q049bWludGVyLE9VPWNsaWVudCxPPUh5cGVybGVkZ2VyLFNUPU5vcnRoIENhcm9saW5hLEM9VVM6OkNOPWNhLm9yZzEuZXhhbXBsZS5jb20sTz1vcmcxLmV4YW1wbGUuY29tLEw9RHVyaGFtLFNUPU5vcnRoIENhcm9saW5hLEM9VVM=\",\"amount\":4900}]"]}'
```

The `Transfer` function verifies that the calling client owns the input UTXO, and that the sum of the input amounts equals the sum of the output amounts. It will then delete (spend) the input UTXO, and create the two output UTXOs. If you passed the incorrect UTXO input key, or requested UTXO output values that don't total 5000, you'll get an error indicating as such.

The new UTXO outputs are returned in the successful response:
```
[{\"utxo_key\":\"e51c3d19e92326f772e49e8a3e58f2bbf72bc3905e55fcd649b97a91b9b2cf44.0\",\"owner\":\"eDUwOTo6Q049cmVjaXBpZW50LE9VPWNsaWVudCxPPUh5cGVybGVkZ2VyLFNUPU5vcnRoIENhcm9saW5hLEM9VVM6OkNOPWNhLm9yZzIuZXhhbXBsZS5jb20sTz1vcmcyLmV4YW1wbGUuY29tLEw9SHVyc2xleSxTVD1IYW1wc2hpcmUsQz1VSw==\",\"amount\":100},{\"utxo_key\":\"e51c3d19e92326f772e49e8a3e58f2bbf72bc3905e55fcd649b97a91b9b2cf44.1\",\"owner\":\"eDUwOTo6Q049bWludGVyLE9VPWNsaWVudCxPPUh5cGVybGVkZ2VyLFNUPU5vcnRoIENhcm9saW5hLEM9VVM6OkNOPWNhLm9yZzEuZXhhbXBsZS5jb20sTz1vcmcxLmV4YW1wbGUuY29tLEw9RHVyaGFtLFNUPU5vcnRoIENhcm9saW5hLEM9VVM=\",\"amount\":4900}]
```

While still in the Org1 terminal, let's request the minter's UTXOs again:
```
peer chaincode query -C mychannel -n token_utxo -c '{"function":"ClientUTXOs","Args":[]}'
```

The new UTXO worth 4900 tokens is returned.

And then using the Org2 terminal, let's request the recipient's UTXOs:
```
peer chaincode query -C mychannel -n token_utxo -c '{"function":"ClientUTXOs","Args":[]}'
```

The new UTXO worth 100 tokens is returned.

Congratulations, you've transferred 100 tokens! The Org2 recipient can now transfer tokens to other registered users in the same manner.

## Clean up

When you are finished, you can bring down the test network. The command will remove all the nodes of the test network, and delete any ledger data that you created:
```
./network.sh down
```

## Contract extension ideas

You can extend the basic UTXO token sample to meet other requirements. For example:

* Rather than using the default 'majority' endorsement policy, you could set the endorsement policy to a subset of organizations that represent trust anchors for the contract execution.
* You could utilize anonymous payment addresses on the UTXO outputs based on private-public key pairs, instead of UTXOs assigned to a client ID. In order to spend the tokens, the client would have to sign the transfer input as proof that they own the address private key, which the contract would then validate, similar to the Bitcoin model in the permissionless blockchain space. However, in a permissioned blockchain such as Fabric, only registered clients are authorized to participate. Furthermore, if you don't want to leak the registered client identity associated with each address, the clients could be registered using an Identity Mixer MSP, so that the client itself is also anonymous in each of the token transactions.
