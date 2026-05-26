## Usage
This chaincode written for Hyperledger Fabric private data tutorial(https://hyperledger-fabric.readthedocs.io/en/latest/private_data_tutorial.html).

### Deploy chaincode with:
1. ``` cd fabric-samples/asset-transfer-private-data/chaincode-typescript/ ```
2. ``` npm install ```
3. ``` npm run build ```
4. ``` cd fabric-samples/test-network/ ```
5. ``` ./network.sh deployCC -ccn private -ccp ../asset-transfer-private-data/chaincode-typescript/ -ccl typescript  -ccep "OR('Org1MSP.peer','Org2MSP.peer')" -cccg ../asset-transfer-private-data/chaincode-typescript/collections_config.json ```