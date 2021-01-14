# Attribute based access control

The `asset-transfer-abac` sample demonstrates the use of Attribute-based access control within the context of a simple asset transfer scenario. The sample also uses authorization based on individual client identities to allow the users that interact with the network to own assets on the blockchain ledger.

Attribute-Based Access Control (ABAC) refers to the ability to restrict access to certain functionality within a smart contract based on the attributes within a users certificate. For example, you may want certain functions within a smart contract to be used only by application administrators. ABAC allows organizations to provide elevated access to certain users without requiring those users be administrators of the network. For more information, see [Attribute-based access control](https://hyperledger-fabric-ca.readthedocs.io/en/latest/users-guide.html#attribute-based-access-control) in the Fabric CA users guide.

The `asset-transfer-abac` smart contract allows you to create assets that can be updated or transferred by the asset owner. However, the ability to create or remove assets from the ledger is restricted to identities with the `abac.creator=true` attribute. The identity that creates the asset is assigned as the asset owner. Only the owner can transfer the asset to a new owner or update the asset properties. In the course of the tutorial, we will use the Fabric CA client to create identities with the attribute required to create a new asset. We will then transfer the asset to another identity and demonstrate how the `GetID()` function can be used to enforce asset ownership. We will then use the owner identity to delete the asset. Both Attribute based access control and the `GetID()` function are provided by the [Client Identity Chaincode Library](https://github.com/hyperledger/fabric-chaincode-go/blob/master/pkg/cid/README.md).

## Start the network and deploy the smart contract

We can use the Fabric test network to deploy and interact with the `asset-transfer-abac` smart contract. Run the following command to change into the test network directory and bring down any running nodes:
```
cd fabric-samples/test-network
./network.sh down
```

Run the following command to deploy the test network using Certificate Authorities:
```
./network.sh up createChannel -ca
```

You can then use the test network script to deploy the `asset-transfer-abac` smart contract to a channel on the network:
```
./network.sh deployCC -ccn abac -ccp ../asset-transfer-abac/chaincode-go/ -ccl go
```

## Register identities with attributes

We can use the one of the test network Certificate Authorities to register and enroll identities with the attribute of `abac.creator=true`. First, we need to set the following environment variables in order to use the Fabric CA client.
```
export PATH=${PWD}/../bin:${PWD}:$PATH
export FABRIC_CFG_PATH=$PWD/../config/
```

We will create the identities using the Org1 CA. Set the Fabric CA client home to the MSP of the Org1 CA admin:
```
export FABRIC_CA_CLIENT_HOME=${PWD}/organizations/peerOrganizations/org1.example.com/
```

There are two ways to generate certificates with attributes added. We will use both methods and create two identities in the process. The first method is to specify that the attribute be added to the certificate by default when the identity is registered. The following command will register an identity named creator1 with the attribute of `abac.creator=true`.

```
fabric-ca-client register --id.name creator1 --id.secret creator1pw --id.type client --id.affiliation org1 --id.attrs 'abac.creator=true:ecert' --tls.certfiles "${PWD}/organizations/fabric-ca/org1/tls-cert.pem"
```

The `ecert` suffix adds the attribute to the certificate automatically when the identity is enrolled. As a result, the following enroll command will contain the attribute that was provided in the registration command.

```
fabric-ca-client enroll -u https://creator1:creator1pw@localhost:7054 --caname ca-org1 -M "${PWD}/organizations/peerOrganizations/org1.example.com/users/creator1@org1.example.com/msp" --tls.certfiles "${PWD}/organizations/fabric-ca/org1/tls-cert.pem"
```

Now that we have enrolled the identity, run the command below to copy the Node OU configuration file into the creator1 MSP folder.
```
cp "${PWD}/organizations/peerOrganizations/org1.example.com/msp/config.yaml" "${PWD}/organizations/peerOrganizations/org1.example.com/users/creator1@org1.example.com/msp/config.yaml"
```

The second method is to request that the attribute be added upon enrollment. The following command will register an identity named creator2 with the same `abac.creator` attribute.
```
fabric-ca-client register --id.name creator2 --id.secret creator2pw --id.type client --id.affiliation org1 --id.attrs 'abac.creator=true:' --tls.certfiles "${PWD}/organizations/fabric-ca/org1/tls-cert.pem"
```

The following enroll command will add the attribute to the certificate:

```
fabric-ca-client enroll -u https://creator2:creator2pw@localhost:7054 --caname ca-org1 --enrollment.attrs "abac.creator" -M "${PWD}/organizations/peerOrganizations/org1.example.com/users/creator2@org1.example.com/msp" --tls.certfiles "${PWD}/organizations/fabric-ca/org1/tls-cert.pem"
```

Run the command below to copy the Node OU configuration file into the creator2 MSP folder.
```
cp "${PWD}/organizations/peerOrganizations/org1.example.com/msp/config.yaml" "${PWD}/organizations/peerOrganizations/org1.example.com/users/creator2@org1.example.com/msp/config.yaml"
```

## Create an asset

You can use either identity with the `abac.creator=true` attribute to create an asset using the `asset-transfer-abac` smart contract. We will set the following environment variables to use the first identity that was generated, creator1:

```
export CORE_PEER_TLS_ENABLED=true
export CORE_PEER_LOCALMSPID="Org1MSP"
export CORE_PEER_MSPCONFIGPATH=${PWD}/organizations/peerOrganizations/org1.example.com/users/creator1@org1.example.com/msp
export CORE_PEER_TLS_ROOTCERT_FILE=${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt
export CORE_PEER_ADDRESS=localhost:7051
export TARGET_TLS_OPTIONS=(-o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile "${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem" --peerAddresses localhost:7051 --tlsRootCertFiles "${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt" --peerAddresses localhost:9051 --tlsRootCertFiles "${PWD}/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt")
```

Run the following command to create Asset1:
```
peer chaincode invoke "${TARGET_TLS_OPTIONS[@]}" -C mychannel -n abac -c '{"function":"CreateAsset","Args":["Asset1","blue","20","100"]}'
```

You can use the command below to query the asset on the ledger:
```
peer chaincode query -C mychannel -n abac -c '{"function":"ReadAsset","Args":["Asset1"]}'
```
The result will list the creator1 identity as the asset owner. The `GetID()` API reads the name and issuer from the certificate of the identity that submitted the transaction and assigns that identity as the asset owner:
```
{"ID":"Asset1","color":"blue","size":20,"owner":"x509::CN=creator1,OU=client+OU=org1,O=Hyperledger,ST=North Carolina,C=US::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US","appraisedValue":100}
```

## Transfer the asset

As the owner of Asset1, the creator1 identity has the ability to transfer the asset to another owner. In order to transfer the asset, the owner needs to provide the name and issuer of the new owner to the `TransferAsset` function. The `asset-transfer-abac` smart contract has a `GetSubmittingClientIdentity` function that allows users to retrieve their certificate information and provide it to the asset owner out of band (we omit this step). Issue the command below to transfer Asset1 to the user1 identity from Org1 that was created when the test network was deployed:
```
export RECIPIENT="x509::CN=user1,OU=client,O=Hyperledger,ST=North Carolina,C=US::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US"
peer chaincode invoke "${TARGET_TLS_OPTIONS[@]}" -C mychannel -n abac -c '{"function":"TransferAsset","Args":["Asset1","'"$RECIPIENT"'"]}'
```
Query the ledger to verify that the asset has a new owner:
```
peer chaincode query -C mychannel -n abac -c '{"function":"ReadAsset","Args":["Asset1"]}'
```
We can see that Asset1 with is now owned by User1:
```
{"ID":"Asset1","color":"blue","size":20,"owner":"x509::CN=user1,OU=client,O=Hyperledger,ST=North Carolina,C=US::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US","appraisedValue":100}
```

## Update the asset

Now that the asset has been transferred, the new owner can update the asset properties. The smart contract uses the `GetID()` API to ensure that the update is being submitted by the asset owner. To demonstrate the difference between identity and attribute based access control, lets try to update the asset using the creator1 identity first:
```
peer chaincode invoke "${TARGET_TLS_OPTIONS[@]}" -C mychannel -n abac -c '{"function":"UpdateAsset","Args":["Asset1","green","20","100"]}'
```

Even though creator1 can create new assets, the smart contract detects that the transaction was not submitted by the identity that owns the asset, user1. The command returns the following error:
```
Error: endorsement failure during invoke. response: status:500 message:"submitting client not authorized to update asset, does not own asset"
```

Run the following command to operate as the asset owner by setting the MSP path to User1:
```
export CORE_PEER_MSPCONFIGPATH=${PWD}/organizations/peerOrganizations/org1.example.com/users/User1@org1.example.com/msp
```

We can now update the asset. Run the following command to change the asset color from blue to green. All other aspects of the asset will remain unchanged.
```
peer chaincode invoke "${TARGET_TLS_OPTIONS[@]}" -C mychannel -n abac -c '{"function":"UpdateAsset","Args":["Asset1","green","20","100"]}'
```
Run the query command again to verify that the asset has changed color:
```
peer chaincode query -C mychannel -n abac -c '{"function":"ReadAsset","Args":["Asset1"]}'
```
The result will display that Asset1 is now green:
```
{"ID":"Asset1","color":"green","size":20,"owner":"x509::CN=user1,OU=client,O=Hyperledger,ST=North Carolina,C=US::CN=ca.org1.example.com,O=org1.example.com,L=Durham,ST=North Carolina,C=US","appraisedValue":100}
```

## Delete the asset

The owner also has the ability to delete the asset. Run the following command to remove Asset1 from the ledger:
```
peer chaincode invoke "${TARGET_TLS_OPTIONS[@]}" -C mychannel -n abac -c '{"function":"DeleteAsset","Args":["Asset1"]}'
```

If you query the ledger once more, you will see that Asset1 no longer exists:
```
peer chaincode query -C mychannel -n abac -c '{"function":"ReadAsset","Args":["Asset1"]}'
```

While we are operating as User1, we can demonstrate attribute based access control by trying to create an asset using an identity without the `abac.creator=true` attribute. Run the following command to try to create Asset1 as User1:
```
peer chaincode invoke "${TARGET_TLS_OPTIONS[@]}" -C mychannel -n abac -c '{"function":"CreateAsset","Args":["Asset2","red","20","100"]}'
```

The smart contract will return the following error:
```
Error: endorsement failure during invoke. response: status:500 message:"submitting client not authorized to create asset, does not have abac.creator role"
```

## Clean up

When you are finished, you can run the following command to bring down the test network:
```
./network.sh down
```
