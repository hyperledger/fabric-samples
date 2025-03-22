# Hyperledger Fabric Test Network Setup

## Prerequisites
Ensure you have the following installed before proceeding:
- **Docker** (Latest stable version)
- **Docker Compose**
- **Go** (>=1.20)
- **Node.js & NPM** (For chaincode development, optional)
- **jq, wget, curl**
- **Hyperledger Fabric binaries and samples**

---

## 1️⃣ Download Hyperledger Fabric Binaries & Samples
If you faced issues with missing peer binaries, ensure you download them correctly:

```bash
curl -sSL https://bit.ly/2ysbOFE | bash -s -- 2.5.0
```
This will download the **Fabric binaries**, **Docker images**, and **samples** for version `2.5.0`.

If you need a different version, replace `2.5.0` with the desired version.

---

## 2️⃣ Export Environment Variables
After downloading, set the environment variables to ensure Fabric binaries are accessible:

```bash
export PATH=$PATH:$HOME/go/fabric-samples/bin
export FABRIC_CFG_PATH=$HOME/go/fabric-samples/config
```

To make these exports permanent, add them to your **~/.bashrc** or **~/.zshrc** file:
```bash
echo 'export PATH=$PATH:$HOME/go/fabric-samples/bin' >> ~/.zshrc
echo 'export FABRIC_CFG_PATH=$HOME/go/fabric-samples/config' >> ~/.zshrc
source ~/.zshrc
```

---

## 3️⃣ Start the Fabric Network
Navigate to the **test-network** directory:
```bash
cd ~/go/fabric-samples/test-network
```
Then start the network with:
```bash
./network.sh down
./network.sh up createChannel -ca
```
This will:
- Bring up the Fabric network (Orderer + Peers)
- Create a channel (`mychannel`)
- Use Certificate Authorities (CAs) for identity management

---

## 4️⃣ Deploy Chaincode
To deploy the **basic** chaincode:
```bash
./network.sh deployCC -ccn basic -ccp ../asset-transfer-basic/chaincode-go -ccl go
```

Verify the deployment:
```bash
peer chaincode query -C mychannel -n basic -c '{"Args":["GetAllAssets"]}'
```

---

## 5️⃣ Set Organization Environment Variables
To interact with the network as **Org1**, export these variables:
```bash
export CORE_PEER_TLS_ENABLED=true
export CORE_PEER_LOCALMSPID="Org1MSP"
export CORE_PEER_TLS_ROOTCERT_FILE=${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt
export CORE_PEER_MSPCONFIGPATH=${PWD}/organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp
export CORE_PEER_ADDRESS=localhost:7051
```
For **Org2**, use:
```bash
export CORE_PEER_LOCALMSPID="Org2MSP"
export CORE_PEER_TLS_ROOTCERT_FILE=${PWD}/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt
export CORE_PEER_MSPCONFIGPATH=${PWD}/organizations/peerOrganizations/org2.example.com/users/Admin@org2.example.com/msp
export CORE_PEER_ADDRESS=localhost:9051
```

---

## 6️⃣ Invoke Chaincode
To **initialize the ledger**:
```bash
peer chaincode invoke -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile ${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem -C mychannel -n basic -c '{"Args":["InitLedger"]}'
```
⚠️ **Note:** Do NOT use `--isInit` as the new Fabric chaincode model doesn't require it.

To **query the ledger**:
```bash
peer chaincode query -C mychannel -n basic -c '{"Args":["GetAllAssets"]}'
```

---

## 7️⃣ Shutdown the Network
To stop and clean up everything:
```bash
./network.sh down
```

This will:
- Remove all containers
- Delete crypto materials and channel artifacts

---

## Troubleshooting

### 🛑 `peer: command not found`
- Ensure the Fabric binaries are added to `PATH`.
- Run: `export PATH=$PATH:$HOME/go/fabric-samples/bin`

### 🛑 `Config File "core" Not Found`
- Ensure `FABRIC_CFG_PATH` is set correctly.
- Run: `export FABRIC_CFG_PATH=$HOME/go/fabric-samples/config`

### 🛑 `Cannot run peer because cannot init crypto`
- The MSP path is incorrect or missing.
- Verify it exists: `ls -l $FABRIC_CFG_PATH/msp`
- Try regenerating the MSP: `./network.sh down && ./network.sh up createChannel -ca`

---

This README provides a clear step-by-step guide to setting up and troubleshooting your Hyperledger Fabric network. 🚀

