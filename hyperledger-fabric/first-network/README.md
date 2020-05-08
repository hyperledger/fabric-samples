## Distributed Ledger Support for FHIR Resources

Distributed Ledger support for FHIR resources is derived from the Hyperledger Fabric [first network](http://hyperledger-fabric.readthedocs.io/en/latest/build_network.html) in [hyperledger/fabric-samples](https://github.com/hyperledger/fabric-samples). The provided fhir-data smart contract supports storage of FHIR patient resources in the blockchain.

### Deploy the blockchain network and contract

Navigate to the hyperledger fabric directory  
```
fabric-emr-demo/hyperledger-fabric/first-network
```

Set environment variables  
```
. ./env.sh
```

Bring up the blockchain and deploy the FHIR contract  
```
./byfn.sh up -c channel1 -l javascript -p "../../fabric-samples/chaincode/fhir-data" -m fhir-data -e 1
```

### Send FHIR transactions

Exec into the CLI container  
```
docker exec -it cli sh
```

Navigate to the scripts directory  
```
cd scripts
```

Set environment variables  
```
. ./env.sh
```

Operate as org1  
```
. ./org1.sh
```

Send a test transaction  
```
peer chaincode invoke -o orderer.example.com:7050 --isInit --tls true --cafile $CAFILE -C channel1 -n fhir-data --peerAddresses peer0.org1.example.com:7051 --tlsRootCertFiles $ORG1TLS --peerAddresses peer0.org2.example.com:9051 --tlsRootCertFiles $ORG2TLS -c '{"function":"initLedger","Args":[]}' --waitForEvent
```
You should see the following result  
```
INFO 003 Chaincode invoke successful. result: status:200
```

Post a patient resource  
```
peer chaincode invoke -o orderer.example.com:7050 --tls true --cafile $CAFILE -C $CHANNEL_NAME -n fhir-data --peerAddresses peer0.org1.example.com:7051 --tlsRootCertFiles $ORG1TLS --peerAddresses peer0.org2.example.com:9051 --tlsRootCertFiles $ORG2TLS -c '{"function":"addPatient","Args":["001","{\"resourceType\":\"Patient\",\"id\":\"001\",\"text\":{\"status\":\"generated\",\"div\":\"\"},\"active\":\"true\",\"name\":[{\"use\":\"official\",\"family\":\"Donald\",\"given\":[\"Duck\"]}],\"gender\":\"male\",\"organization\":{\"reference\":\"Organization\/1\",\"display\":\"Walt Disney Corp\"}}"]}' --waitForEvent
```
You should see the following result  
```
INFO 003 Chaincode invoke successful. result: status:200
```

Query for the patient resource you just posted  
```
peer chaincode invoke -o orderer.example.com:7050 --tls true --cafile $CAFILE -C $CHANNEL_NAME -n fhir-data --peerAddresses peer0.org1.example.com:7051 --tlsRootCertFiles $ORG1TLS --peerAddresses peer0.org2.example.com:9051 --tlsRootCertFiles $ORG2TLS -c '{"function":"queryPatient","Args":["001"]}' --waitForEvent
```
You should see the following result  
```
INFO 003 Chaincode invoke successful. result: status:200 payload:"{\"resourceType\":\"Patient\",\"id\":\"001\",\"text\":{\"status\":\"generated\",\"div\":\"\"},\"active\":\"true\",\"name\":[{\"use\":\"official\",\"family\":\"Donald\",\"given\":[\"Duck\"]}],\"gender\":\"male\",\"organization\":{\"reference\":\"Organization/1\",\"display\":\"Walt Disney Corp\"}}"
```

Replace the patient record  
```
peer chaincode invoke -o orderer.example.com:7050 --tls true --cafile $CAFILE -C $CHANNEL_NAME -n fhir-data --peerAddresses peer0.org1.example.com:7051 --tlsRootCertFiles $ORG1TLS --peerAddresses peer0.org2.example.com:9051 --tlsRootCertFiles $ORG2TLS -c '{"function":"replacePatient","Args":["001","{\"resourceType\":\"Patient\",\"id\":\"001\",\"text\":{\"status\":\"generated\",\"div\":\"\"},\"active\":\"true\",\"name\":[{\"use\":\"official\",\"family\":\"Duck\",\"given\":[\"Donald\"]}],\"gender\":\"male\",\"organization\":{\"reference\":\"Organization\/1\",\"display\":\"Walt Disney Corp\"}}"]}' --waitForEven
```
You should see the following result  
```
INFO 003 Chaincode invoke successful. result: status:200
```

Update the patient record by adding a new JSON section  
```
peer chaincode invoke -o orderer.example.com:7050 --tls true --cafile $CAFILE -C $CHANNEL_NAME -n fhir-data --peerAddresses peer0.org1.example.com:7051 --tlsRootCertFiles $ORG1TLS --peerAddresses peer0.org2.example.com:9051 --tlsRootCertFiles $ORG2TLS -c '{"function":"updatePatient","Args":["001","{\"contact\":[{\"relationship\":[{\"coding\":[{\"system\":\"http:\/\/terminology.hl7.org\/CodeSystem\/v2-0131\",\"code\":\"E\"}]}]}]}"]}' --waitForEvent
```
You should see the following result  
```
INFO 003 Chaincode invoke successful. result: status:200
```

When you are finished running transactions, type `exit` to exit the CLI container.

To bring down the Hyperledger Fabric network  
```
./byfn.sh down -c channel1
```  
Note: Bringing down the network will cause you to lose the transactions you have submitted.