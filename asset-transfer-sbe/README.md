# Sample to demonstrate State-Based Endorsements (SBE)

## Introduction to SBE
Fabric allows different ways to set endorsements for transactions on the network. The default method is specified in chaincode definition, which is agreed by channel members and committed to the channel. However, there are cases where it may be necessary for a particular Key (public channel state or private data collection state) to have a different endorsement policy. State-Based endorsement allows endorsement policies to be overridden for the specified Key's. State-Based Endorsements are also known as Key Level Endorsements. To learn more about endorsement policies and State-Based endorsements, visit the [Fabric Endorsement Policies documentation](https://hyperledger-fabric.readthedocs.io/en/master/endorsement-policies.html).


Fabric Shim Method's for State-Based endorsements (below references from fabric-shim, ChaincodeStub for Node):
- setStateValidationParameter(key: string, ep: Uint8Array): Promise<void>;
- getStateValidationParameter(key: string): Promise<Uint8Array>;
- setPrivateDataValidationParameter(collection: string, key: string, ep: Uint8Array): Promise<void>;
- getPrivateDataValidationParameter(collection: string, key: string): Promise<Uint8Array>;


## About the SBE Asset Transfer Sample
Using this sample we demonstrate State-Based Endorsements (SBE), and set a different endorsement policy for a specified Key, instead of the default chaincode policy. This sample is deployed and demonstrated on the fabric-samples, Test network (2 Org network, Org1 and Org2). The endorsement policy for chaincodes deployed in this network defaults to majority, that is both Org's need to endorse transactions on the network. To bootstrap the Test Network, and deploy the SBE Asset Transfer Sample chaincode, visit the [Test Network documentation](https://hyperledger-fabric.readthedocs.io/en/latest/test_network.html).

This sample demonstrates modifying the default chaincode policy (requiring endorsements from both Org Peers), and changing it to a less restrictive policy requiring endorsement only from a specific Org Peer for the specified Key. Endorsements can also be modified to be more restrictive for a particular Key in a similar way.

On Asset creation, State-Based endorsements for the Asset Key is set to either Org1 or Org2 Peer, depending on the client Org creating the Asset. Creation of Asset still needs the default chaincode endorsement policy, and hence will need to be endorsed by both Org's (Org1 & Org2). However, all future updates for the Asset Key will use the modified endorsements as per the State-Based endorsements set during Asset creation.

Hence transactions like UpdateAsset or TransferAsset will not succeed if endorsed by the non-owner Org Peer.

Sample invokations to demonstrate State-Based Endorsements (SBE) on test-network, using client identity of Org1
* Create Asset (requires Org1 & Org2 Peer to endorse as per the default chaincode endorsement policy, however future updates will need only Org1 Peer to endorse, as the State-Based endorsement policy is set for assetId Key during the createAsset transaction)
```bash
peer chaincode invoke -o localhost:7050 --waitForEvent --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n sbe --peerAddresses localhost:7051 --tlsRootCertFiles ${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt --peerAddresses localhost:9051 --tlsRootCertFiles ${PWD}/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt -c '{"function":"CreateAsset","Args":["asset1","100","Org1User1"]}'
```
* Read & Verify Asset (Use this to verify changes for UpdateAsset & TransferAsset)
```bash
peer chaincode query -C mychannel -n sbe -c '{"Args":["ReadAsset","asset1"]}'
```
* Update Asset (requires Org1 Peer to endorse as per SBE specified for assetId Key, but since Org2 Peer is endorsing it will result in ENDORSEMENT_POLICY_FAILURE by the peer during endorsement validations for the transaction)
```bash
peer chaincode invoke -o localhost:7050 --waitForEvent --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n sbe --peerAddresses localhost:9051 --tlsRootCertFiles ${PWD}/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt -c '{"function":"UpdateAsset","Args":["asset1","200"]}'
```
* Update Asset succeeds (requires Org1 Peer to endorse as per SBE specified for assetId Key, and Org1 Peer is endorsing here)
```bash
peer chaincode invoke -o localhost:7050 --waitForEvent --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n sbe --peerAddresses localhost:7051 --tlsRootCertFiles ${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt -c '{"function":"UpdateAsset","Args":["asset1","200"]}'
```
* Transfer Asset (requires Org1 Peer to endorse as per SBE specified for assetId Key, but since Org2 Peer is endorsing it will result in ENDORSEMENT_POLICY_FAILURE by the peer during endorsement validations for the transaction)
```bash
peer chaincode invoke -o localhost:7050 --waitForEvent --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n sbe --peerAddresses localhost:9051 --tlsRootCertFiles ${PWD}/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt -c '{"function":"TransferAsset","Args":["asset1","Org2User1","Org2MSP"]}'
```
* Transfer Asset succeeds (requires Org1 Peer to endorse as per SBE specified for assetId Key, and Org1 Peer is endorsing here)
```bash
peer chaincode invoke -o localhost:7050 --waitForEvent --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n sbe --peerAddresses localhost:7051 --tlsRootCertFiles ${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt -c '{"function":"TransferAsset","Args":["asset1","Org2User1","Org2MSP"]}'
```
* Update Asset (now requires Org2 Peer to endorse as per SBE specified for assetId Key, but since Org1 Peer is endorsing it will result in ENDORSEMENT_POLICY_FAILURE by the peer during endorsement validations for the transaction)
```bash
peer chaincode invoke -o localhost:7050 --waitForEvent --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n sbe --peerAddresses localhost:7051 --tlsRootCertFiles ${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt -c '{"function":"UpdateAsset","Args":["asset1","300"]}'
```
* Update Asset succeeds (requires Org2 Peer to endorse as per SBE specified for assetId Key, and Org2 Peer is endorsing here)
```bash
peer chaincode invoke -o localhost:7050 --waitForEvent --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n sbe --peerAddresses localhost:9051 --tlsRootCertFiles ${PWD}/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt -c '{"function":"UpdateAsset","Args":["asset1","300"]}'
```
