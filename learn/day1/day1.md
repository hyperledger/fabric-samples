# Hyperledger Fabric Mastery Roadmap

Welcome to the **Hyperledger Fabric (HLF) Mastery Roadmap**! This guide is designed to take you from beginner to expert in HLF by implementing real-world scenarios.

---

## 📌 Day 1: Understanding Hyperledger Fabric & Setting Up the Network

### 🎯 Objectives:
- Understand **Hyperledger Fabric architecture** (Orderers, Peers, MSPs, Channels, Chaincode, CouchDB vs LevelDB, etc.).
- Set up a minimal **Fabric network** using `fabric-samples/test-network`.
- Deploy a **custom Fabric network** using `cryptogen` and `configtxgen`.

### 📂 Folder Structure
Your repository will have the following structure:
```plaintext
hlf/
 ├── fabric-samples/   # Fabric sample network
 ├── day1/             # Documentation for Day 1 progress
 │   ├── day1.md       # Steps, commands, and explanations for Day 1
 │   ├── assets/       # Diagrams or images for understanding HLF
```

### 📖 Learning & Implementation Steps
#### 1️⃣ Understanding Hyperledger Fabric Architecture
- Read **Fabric Docs**: [Hyperledger Fabric Docs](https://hyperledger-fabric.readthedocs.io/en/latest/)
- Key Components:
  - **Orderers**: Maintain transaction order.
  - **Peers**: Maintain the ledger and execute chaincode.
  - **MSP (Membership Service Provider)**: Handles identity & authentication.
  - **Channels**: Private communication pathways.
  - **Chaincode**: Smart contracts running on peers.
  - **Databases**: CouchDB (rich queries) vs LevelDB (key-value store).

#### 2️⃣ Setting Up Fabric Samples Network
- Clone **Fabric Samples** and install prerequisites:
  ```bash
  git clone https://github.com/hyperledger/fabric-samples.git
  cd fabric-samples/test-network
  ./network.sh up createChannel -ca
  ```
- Verify network:
  ```bash
  docker ps
  ```

#### 3️⃣ Bootstrapping a Custom Fabric Network
- Generate crypto material using **cryptogen**:
  ```bash
  cryptogen generate --config=./crypto-config.yaml --output=crypto-config/
  ```
- Create **configtx.yaml** and generate genesis block:
  ```bash
  configtxgen -profile OrdererGenesis -channelID sys-channel -outputBlock ./genesis.block
  ```
- Start network using Docker Compose:
  ```bash
  docker-compose -f docker-compose.yaml up -d
  ```
- Verify network logs:
  ```bash
  docker logs peer0.org1.example.com
  ```

### ✅ Tasks for Day 1
✔ Clone **fabric-samples** and explore test network.
✔ Understand Fabric network components and configurations.
✔ Create `day1.md` and document all steps with issues faced.
✔ Bootstrap a **custom Fabric network**.
✔ Push updates to GitHub repository under `day1/` folder.

---

💡 **Next Steps:** Proceed to **Day 2 - Creating and Managing Channels** 🚀

Let me know if you need more details! 🎯

