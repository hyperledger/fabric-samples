# Asset Transfer Private Data Sample

This app uses fabric-samples/test-network based setup and the companion chaincode asset-transfer-private-data/chaincode-go/ with chaincode endorsement policy as "OR('Org1MSP.peer','Org2MSP.peer')"

For this usecase illustration, we will use both Org1 & Org2 client identity from this same app
In real world the Org1 & Org2 identity will be used in different apps to achieve asset transfer.

For more details refer:
https://hyperledger-fabric.readthedocs.io/en/release-2.4/private_data_tutorial.html#pd-use-case

