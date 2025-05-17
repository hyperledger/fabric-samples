# Nivix Hybrid Blockchain Setup

This document provides step-by-step instructions for setting up the Nivix hybrid blockchain architecture combining Solana and Hyperledger Fabric.

## Overview

The Nivix payment platform uses a hybrid architecture:
- **Solana**: Handles fast and low-cost payment transactions
- **Hyperledger Fabric**: Stores private KYC/AML data and transaction records

## Prerequisites

- Docker v28.1.1+
- Docker Compose v2.35.1+
- Go v1.20+
- Hyperledger Fabric v2.5.12+

## Setup Instructions

### 1. Setting up the Hyperledger Fabric Test Network

```bash
# Navigate to the test network directory
cd /media/shubham/OS/for\ linux\ work/blockchain\ solana/hyperledger/fabric/fabric-samples/test-network

# Bring down any existing network
./network.sh down

# Start a fresh Fabric network
./network.sh up

# Create a channel named "mychannel"
./network.sh createChannel
```

### 2. Preparing the Nivix KYC Chaincode

```bash
# Navigate to the chaincode directory
cd /media/shubham/OS/for\ linux\ work/blockchain\ solana/hyperledger/fabric/fabric-samples/chaincode-nivix-kyc/go/nivix-kyc

# Add required dependencies
go get github.com/hyperledger/fabric-contract-api-go/contractapi
go mod tidy

# Prepare vendor directory
go mod vendor
```

### 3. Create Private Data Collections Configuration

Create a file named `collections_config.json` in the test-network directory:

```json
[
  {
    "name": "kycCollection",
    "policy": "OR('Org1MSP.member', 'Org2MSP.member')",
    "requiredPeerCount": 0,
    "maxPeerCount": 1,
    "blockToLive": 1000000,
    "memberOnlyRead": true,
    "memberOnlyWrite": true,
    "endorsementPolicy": {
      "signaturePolicy": "OR('Org1MSP.member', 'Org2MSP.member')"
    }
  },
  {
    "name": "transactionCollection",
    "policy": "OR('Org1MSP.member', 'Org2MSP.member')",
    "requiredPeerCount": 0,
    "maxPeerCount": 1,
    "blockToLive": 1000000,
    "memberOnlyRead": true,
    "memberOnlyWrite": true,
    "endorsementPolicy": {
      "signaturePolicy": "OR('Org1MSP.member', 'Org2MSP.member')"
    }
  }
]
```

### 4. Deploy the Chaincode

```bash
# Return to the test-network directory
cd /media/shubham/OS/for\ linux\ work/blockchain\ solana/hyperledger/fabric/fabric-samples/test-network

# Install jq (required by scripts)
sudo apt-get update && sudo apt-get install -y jq

# Deploy the chaincode
./network.sh deployCC -ccn nivix-kyc -ccp ../chaincode-nivix-kyc/go/nivix-kyc -ccl go
```

### 5. Update Chaincode with Private Collections

```bash
# Set up environment variables for Org1
export PATH=${PWD}/../bin:$PATH
export FABRIC_CFG_PATH=$PWD/../config/
export CORE_PEER_TLS_ENABLED=true
export CORE_PEER_LOCALMSPID="Org1MSP"
export CORE_PEER_TLS_ROOTCERT_FILE=${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt
export CORE_PEER_MSPCONFIGPATH=${PWD}/organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp
export CORE_PEER_ADDRESS=localhost:7051

# Approve for Org1
peer lifecycle chaincode approveformyorg -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile "${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem" --channelID mychannel --name nivix-kyc --version 1.2 --package-id nivix-kyc_1.1:5ef7a3ff23f8d69ed809eb2863c129d83fa780069026d2615545b4136ae003bd --sequence 3 --collections-config "${PWD}/collections_config.json"

# Set up environment variables for Org2
export CORE_PEER_LOCALMSPID="Org2MSP"
export CORE_PEER_TLS_ROOTCERT_FILE=${PWD}/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt
export CORE_PEER_MSPCONFIGPATH=${PWD}/organizations/peerOrganizations/org2.example.com/users/Admin@org2.example.com/msp
export CORE_PEER_ADDRESS=localhost:9051

# Approve for Org2
peer lifecycle chaincode approveformyorg -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile "${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem" --channelID mychannel --name nivix-kyc --version 1.2 --package-id nivix-kyc_1.1:5ef7a3ff23f8d69ed809eb2863c129d83fa780069026d2615545b4136ae003bd --sequence 3 --collections-config "${PWD}/collections_config.json"

# Switch back to Org1 for commit
export CORE_PEER_LOCALMSPID="Org1MSP"
export CORE_PEER_TLS_ROOTCERT_FILE=${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt
export CORE_PEER_MSPCONFIGPATH=${PWD}/organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp
export CORE_PEER_ADDRESS=localhost:7051

# Commit the chaincode definition
peer lifecycle chaincode commit -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile "${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem" --channelID mychannel --name nivix-kyc --peerAddresses localhost:7051 --tlsRootCertFiles "${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt" --peerAddresses localhost:9051 --tlsRootCertFiles "${PWD}/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt" --version 1.2 --sequence 3 --collections-config "${PWD}/collections_config.json"
```

## Testing the Chaincode

### 1. Store KYC Data

```bash
# Store KYC data for user1
peer chaincode invoke -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile "${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem" -C mychannel -n nivix-kyc --peerAddresses localhost:7051 --tlsRootCertFiles "${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt" --peerAddresses localhost:9051 --tlsRootCertFiles "${PWD}/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt" -c '{"function":"StoreKYCData","Args":["user1", "Sol123456789", "John Doe", "true", "2025-05-17", "10", "a1b2c3d4e5f6"]}'

# Store KYC data for user2
peer chaincode invoke -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile "${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem" -C mychannel -n nivix-kyc --peerAddresses localhost:7051 --tlsRootCertFiles "${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt" --peerAddresses localhost:9051 --tlsRootCertFiles "${PWD}/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt" -c '{"function":"StoreKYCData","Args":["user2", "Sol987654321", "Jane Smith", "true", "2025-05-17", "5", "f6e5d4c3b2a1"]}'
```

### 2. Query KYC Status

```bash
# Query KYC status for user1
peer chaincode query -C mychannel -n nivix-kyc -c '{"function":"GetKYCStatus","Args":["Sol123456789"]}'

# Query KYC status for user2
peer chaincode query -C mychannel -n nivix-kyc -c '{"function":"GetKYCStatus","Args":["Sol987654321"]}'
```

### 3. Record Solana Transaction

```bash
# Record a transaction from Solana
peer chaincode invoke -o localhost:7050 --ordererTLSHostnameOverride orderer.example.com --tls --cafile "${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem" -C mychannel -n nivix-kyc --peerAddresses localhost:7051 --tlsRootCertFiles "${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt" --peerAddresses localhost:9051 --tlsRootCertFiles "${PWD}/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt" -c '{"function":"RecordTransaction","Args":["tx123", "solSig456", "user1", "user2", "100.5", "USDC", "2025-05-17T17:36:00Z"]}'
```

## Understanding the Chaincode

The Nivix KYC chaincode provides the following functions:

1. `StoreKYCData`: Stores KYC information in a private data collection while maintaining a public reference of KYC status.

2. `GetKYCStatus`: Retrieves the public KYC status of a user by their Solana address without exposing personal information.

3. `RecordTransaction`: Records transaction data from Solana on the Hyperledger Fabric network for compliance and auditing purposes.

4. `GetTransactionSummary`: Retrieves a public summary of a transaction by its ID.

## Bringing Down the Network

When you're finished testing, you can bring down the network:

```bash
./network.sh down
```

## Troubleshooting

1. If you encounter chaincode installation errors, ensure that:
   - All required dependencies are installed
   - Go modules are properly set up
   - The chaincode path is correct

2. For issues with private collections:
   - Verify the collections configuration file is properly formatted
   - Check that `requiredPeerCount` is set to 0 if testing with minimal peers
   - Ensure the collection policy matches the channel members

3. If chaincode invocation fails:
   - Check that all parameters are correctly passed
   - Verify the function name matches exactly what's in the chaincode
   - Ensure the chaincode was successfully committed with collections config 