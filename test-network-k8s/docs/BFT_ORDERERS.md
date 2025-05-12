# Working with BFT Orderers

This guide explains how to run `test-network-k8s` with the BFT consensus type.

In the current `test-network-k8s`, a Fabric network with three orderers managed by Org0 is started when using the Raft consensus type. In contrast, when specifying BFT, the network starts with four orderers (`org0-orderer1`, `org0-orderer2`, `org0-orderer3`, and `org0-orderer4`) with the BFT consensus type.

## Running Peers and BFT Orderers, Creating a Channel

Since BFT Orderers are supported only in Fabric v3.0 and later, you must specify `3.0` or later for the `TEST_NETWORK_FABRIC_VERSION` environment variable. Additionally, set `TEST_NETWORK_ORDERER_TYPE` to `bft` to start the Fabric network and create a channel with BFT consensus type. For example:

```shell
export TEST_NETWORK_FABRIC_VERSION=3.1
export TEST_NETWORK_ORDERER_TYPE=bft

./network kind
./network cluster init
./network up
./network channel create
```

The `configtx.yaml` template used for constructing the channel genesis block with BFT consensus type can be found [here](../config/org0/bft/configtx-template.yaml).

## Deploying and Using Chaincode

After creating the channel with the BFT consensus type, you can deploy and interact with chaincode. For example:

```shell
./network chaincode deploy asset-transfer-basic ../asset-transfer-basic/chaincode-java
./network chaincode invoke asset-transfer-basic '{"Args":["InitLedger"]}'
./network chaincode query  asset-transfer-basic '{"Args":["ReadAsset","asset1"]}'
```

## Troubleshooting

If you encounter issues, try the following troubleshooting steps.

First, run the following command to verify that the environment variables are correctly applied and that the Fabric image and binary versions match:

```shell
$ ./network

Fabric image versions: Peer (3.1.1), CA (1.5.15)
Fabric binary versions: Peer (3.1.1), CA (1.5.15)

--- Fabric Information
Fabric Version          : 3.1
Fabric CA Version       : 1.5
Container Registry      : hyperledger
Network name            : test-network
Ingress domain          : localho.st
Channel name            : mychannel
Orderer type            : bft

(...)
```

If old Fabric binaries or network artifacts remain, they may cause issues.
In such cases, clean up the environment using the following commands:

```shell
./network down
./network unkind

rm -r bin
```
