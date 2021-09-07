
# Kubernetes Test Network 

This directory includes a set of kubernetes deployment manifests, scripts, and configuration files suitable 
for running the Hyperledger Fabric test network on a local [KIND](https://kind.sigs.k8s.io/docs/user/quick-start/#installation) 
cluster.

This is currently an experimental branch.  No attempt has been made to optimize or streamline the actual 
deployment to kubernetes - no helm charts, operators, kustomization overlays, etc. are involved at this 
early genesis.  This is merely a set of kube manifests suitable for replicating the test network on 
Kubernetes. 


## Areas for Improvement

- [ ] Introduce `fabctl` as a bridge between objectives running locally and activities running remotely (`network.sh` equivalent, e.g. see [fabric-hyper-kube](https://github.com/hyperledgendary/fabric-hyper-kube))
- [ ] Provide simple scripts or CLI driver routines (e.g. `network.sh up` -> `kubectl apply ...`)
- [ ] `cryptogen` -> Configure a CA
- [ ] couchdb state database
- [ ] KIND is only one path to a Kube.  Check that we are also in good shape with minikube, IBM Fyre, IKS, aws, OCP, azure, etc.
- [ ] Use kustomize, ~helm~, operator, etc. etc. to properly integrate and install. 
- [ ] The manifests directly pull 2.3.2 fabric images and have an imagePullPolicy: Always.  Find a better technique to pull :latest tag from docker hub or the kind control plane. 
- [ ] The fabric config files (2.3.2) are also hard-wired into the /config folder.  It would be nice if this project could use the fab release archive (or better - directly from git), and override the stanzas in core.yaml (e.g. externalBuilder)
- [ ] Publish [fabric-ccs-builder](https://github.com/hyperledgendary/fabric-ccs-builder) image to docker hub 
- [ ] Publish [asset-transfer-basic](../asset-transfer-basic/chaincode-external) and external chaincode sample images to docker hub.
- [ ] The peer deployments currently mount the chaincode application bundle into a volume at launch time.  This is wrong - chaincode bundles must come AFTER the peers have been deployed, and should not force a peer pod restart.
- [ ] Pick out the CC_PACKAGE_ID from `peer chaincode install` and load into a configmap / k8s secret / env 
- [ ] Configure multiple pvc - one per network node, rather than one shared volume for all network elements.
- [ ] Configure the Fabric REST sample - needs attention in configuring connection profiles, pems, CAs, and signing keys.

## Prerequisites

- [Docker](https://www.docker.com)
- [kubectl](https://kubernetes.io/docs/tasks/tools/) 
- [KIND](https://kind.sigs.k8s.io/docs/user/quick-start/#installation)
- [fabric-ccs-builder](#fabric-ccs-builder) docker image

### Fabric CCS Builder

Smart contracts running on Kubernetes rely extensively on the [Chaincode as a Service](https://hyperledger-fabric.readthedocs.io/en/latest/cc_service.html)
deployment pattern.  This test network uses the [fabric-ccs-builder](https://github.com/jkneubuh/fabric-ccs-builder/tree/feature/docker-bundle)
image `release`, `build`, and `detect` binaries, copied into the peer pods via an init container at 
deployment time.  Before starting the test network, build the ccs image locally and push to the KIND control plane:

```shell
git clone https://github.com/hyperledgendary/fabric-ccs-builder.git /tmp/fabric-ccs-builder

docker build -t hyperledgendary/fabric-ccs-builder /tmp/fabric-ccs-builder
```

## Test Network

### Kube

Create a Kubernetes cluster and [load docker images](https://kind.sigs.k8s.io/docs/user/quick-start/#loading-an-image-into-your-cluster) into the KIND control plane.  
```shell
kind create cluster

kind load docker-image hyperledgendary/fabric-ccs-builder
```

Create a dedicated namespace and persistent volume for the test-network:
```shell
kubectl create -f kube/pv-fabric.yaml
kubectl create -f kube/ns-test-network.yaml
kubectl -n test-network create -f kube/pvc-fabric.yaml
```

### Network Config

```shell
kubectl -n test-network create configmap fabric-config --from-file=config/
kubectl -n test-network create configmap chaincode-config --from-file=chaincode/
```

### Channel Artifacts

```shell
kubectl -n test-network create -f kube/debug.yaml
kubectl -n test-network create -f kube/job-crypto-config.yaml
kubectl -n test-network create -f kube/job-orderer-genesis.yaml
kubectl -n test-network create -f kube/job-create-channel-config.yaml
kubectl -n test-network create -f kube/job-update-org1-anchor-peers.yaml
kubectl -n test-network create -f kube/job-update-org2-anchor-peers.yaml
```
(Wait for these jobs to complete.  It can take a few seconds for images to be pulled from docker hub.)

### Orderers
```shell
kubectl -n test-network apply -f kube/orderer1.yaml
kubectl -n test-network apply -f kube/orderer2.yaml
kubectl -n test-network apply -f kube/orderer3.yaml
```

### Peers
```shell
kubectl -n test-network apply -f kube/org1-peer1.yaml
kubectl -n test-network apply -f kube/org1-peer2.yaml
kubectl -n test-network apply -f kube/org2-peer1.yaml
kubectl -n test-network apply -f kube/org2-peer2.yaml
```

### Create `mychannel`

```shell
kubectl -n test-network exec deploy/org1-peer1 -i -t -- /bin/sh

export CORE_PEER_MSPCONFIGPATH=/var/hyperledger/fabric/crypto-config/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp

peer channel \
  create \
  -c mychannel \
  -o orderer1:6050 \
  -f /var/hyperledger/fabric/channel-artifacts/mychannel.tx \
  --outputBlock /var/hyperledger/fabric/channel-artifacts/mychannel.block \
  --tls \
  --cafile /var/hyperledger/fabric/crypto-config/ordererOrganizations/example.com/orderers/orderer1.example.com/tls/ca.crt 

peer channel \
  update \
  -o orderer1:6050 \
  -c mychannel \
  -f /var/hyperledger/fabric/channel-artifacts/Org1MSPanchors.tx \
  --tls \
  --cafile /var/hyperledger/fabric/crypto-config/ordererOrganizations/example.com/orderers/orderer1.example.com/tls/ca.crt

exit
```

### Join Peers

```shell
kubectl \
  -n test-network \
  exec deploy/org1-peer1 \
  -i -t -- \
  /bin/sh -c 'CORE_PEER_MSPCONFIGPATH=/var/hyperledger/fabric/crypto-config/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp peer channel join -b /var/hyperledger/fabric/channel-artifacts/mychannel.block'
```
```shell
kubectl \
  -n test-network \
  exec deploy/org1-peer2 \
  -i -t -- \
  /bin/sh -c 'CORE_PEER_MSPCONFIGPATH=/var/hyperledger/fabric/crypto-config/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp peer channel join -b /var/hyperledger/fabric/channel-artifacts/mychannel.block'
```

```shell
kubectl \
  -n test-network \
  exec deploy/org2-peer1 \
  -i -t -- \
  /bin/sh -c 'CORE_PEER_MSPCONFIGPATH=/var/hyperledger/fabric/crypto-config/peerOrganizations/org2.example.com/users/Admin@org2.example.com/msp peer channel join -b /var/hyperledger/fabric/channel-artifacts/mychannel.block'
```

```shell
kubectl \
  -n test-network \
  exec deploy/org2-peer2 \
  -i -t -- \
  /bin/sh -c 'CORE_PEER_MSPCONFIGPATH=/var/hyperledger/fabric/crypto-config/peerOrganizations/org2.example.com/users/Admin@org2.example.com/msp peer channel join -b /var/hyperledger/fabric/channel-artifacts/mychannel.block'
```

## Chaincode 

### Install

```shell
kubectl -n test-network exec deploy/org1-peer1 -i -t -- /bin/sh
export CORE_PEER_MSPCONFIGPATH=/var/hyperledger/fabric/crypto-config/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp

peer lifecycle \
  chaincode install \
  /var/hyperledger/fabric/chaincode/asset-transfer-basic.tgz 

exit
```

### Launch External Chaincode

- [ ] Determine `CHAINCODE_ID` from install command and load as a config map / env entry in the cc deployment spec.
- [ ] Use an [insecure docker registry](bin/make-kind-with-reg.sh) to build and deploy chaincode images without Docker hub or the kind control plane.

```shell
docker build \
  -t hyperledger/asset-transfer-basic \
  ../asset-transfer-basic/chaincode-external
  
kind load docker-image hyperledger/asset-transfer-basic
```

```shell
kubectl -n test-network apply -f kube/cc-asset-transfer-basic.yaml 
```

### Approve and Commit

```shell
kubectl -n test-network exec deploy/org1-peer1 -i -t -- /bin/sh
export FABRIC_LOGGING_SPEC=INFO
export CORE_PEER_MSPCONFIGPATH=/var/hyperledger/fabric/crypto-config/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp
export CC_PACKAGE_ID=basic_1.0:d730a5ce916e120f2a2509ee33527a0df68cadac678f5eb196737ad10ba42da9

peer lifecycle \
  chaincode approveformyorg \
  -o orderer1:6050 \
  --channelID mychannel \
  --name basic \
  --version 1 \
  --package-id $CC_PACKAGE_ID \
  --sequence 1 \
  --tls \
  --cafile /var/hyperledger/fabric/crypto-config/ordererOrganizations/example.com/orderers/orderer1.example.com/msp/tlscacerts/tlsca.example.com-cert.pem 

peer lifecycle \
  chaincode commit \
  -o orderer1:6050 \
  --channelID mychannel \
  --name basic \
  --version 1 \
  --sequence 1 \
  --tls \
  --cafile /var/hyperledger/fabric/crypto-config/ordererOrganizations/example.com/orderers/orderer1.example.com/msp/tlscacerts/tlsca.example.com-cert.pem 
```

### Query

(run on org1-peer1)
```shell
peer chaincode \
  invoke \
  -o orderer1:6050 \
  -C mychannel \
  -n basic \
  -c '{"Args":["CreateAsset","1","blue","35","tom","1000"]}' \
  --tls \
  --cafile /var/hyperledger/fabric/crypto-config/ordererOrganizations/example.com/orderers/orderer1.example.com/tls/ca.crt \

sleep 2

peer chaincode \
  query \
  -C mychannel \
  -n basic \
  -c '{"Args":["ReadAsset","1"]}'

exit
```

### Reset Network

```shell 
kubectl -n test-network delete deployment --all 
kubectl -n test-network delete pod --all
kubectl -n test-network delete service --all
kubectl -n test-network delete configmap --all 
kubectl -n test-network delete secret --all 
kubectl -n test-network create -f kube/job-scrub-fabric-volume.yaml
kubectl -n test-network wait --for=condition=complete --timeout=60s job/job-scrub-fabric-volume
kubectl -n test-network delete job --all
```
[GOTO Config](#network-config)

or ... 
```shell
kind delete cluster
```
[GOTO Kube](#kube)


