
# Kubernetes Test Network 

This directory includes a set of kubernetes deployment manifests, scripts, and configuration files suitable 
for running the Hyperledger Fabric test network in a local KIND (Kubernetes in Docker) cluster.

This is currently an experimental branch.  No attempt has been made to optimize or streamline the actual 
deployment to kubernetes - no helm charts, operators, kustomization overlays, etc. are involved at this 
early genesis.  This is merely a set of kube manifests suitable for replicating the test network on a local 
KIND cluster.


## Areas for Improvement

- [ ] Introduce `fabctl` as a bridge between objectives running locally and activities running remotely (`network.sh` equivalent.)
- [ ] Provide simple scripts or CLI driver routines (e.g. `network.sh up` equivalent)
- [ ] crypto-config --> Configure a CA
- [ ] couchdb state database
- [ ] KIND is only one path to a Kube.  Check that we are also in good shape with minikube, IBM Fyre, IKS, aws, OCP, azure, etc.
- [ ] Use kustomize, ~helm~, operator, etc. etc. to properly integrate and install. 
- [ ] The manifests directly pull 2.3.2 fabric images and have an imagePullPolicy: Always.  Find a better technique to pull :latest tag from docker hub, or `kind load docker-image ...`  
- [ ] The fabric config files (2.3.2) are also hard-wired into the /config folder.  It would be nice if this project could use the fab release archive (or better - directly from git), and override the stanzas in core.yaml (e.g. externalBuilder)
- [ ] Pick ONE technique for running binaries in k8s (kube jobs, tekton, argo, exec, controller, etc.) - See `fabctl` above
- [ ] Pick ONE technique for copying local config files into the volume mounts (currently configmap+volume mount)
- [ ] Pick ONE technique for managing `peer` CLI _connection profiles_ - see `fabctl` above
- [ ] Publish [fabric-ccs-builder](https://github.com/hyperledgendary/fabric-ccs-builder) image to docker hub 
- [ ] Publish [asset-transfer-basic](../asset-transfer-basic/chaincode-external) and external chaincode sample images to docker hub.
- [ ] The peer deployments currently mount the chaincode application bundle into a volume at launch time.  This is wrong - chaincode bundles come AFTER the peers have been deployed. 
- [ ] Pick out the CC_PACKAGE_ID from `peer chaincode install` and load into a configmap / k8s secret / env 
- [ ] Configure multiple pvc - one per network node, rather than one shared volume for all network elements.
- [ ] Configure the Fabric REST sample - needs attention in configuring connection profiles, pems, CAs, and signing keys.

## Prerequisites

- [Docker](docker.io)
- [kubectl](https://kubernetes.io/docs/tasks/tools/) 
- [KIND](https://kind.sigs.k8s.io/docs/user/quick-start/#installation)
- [fabric-ccs-builder](#fabric-ccs-builder) docker image

### Fabric CCS Builder

Smart contracts running on Kubernetes rely extensively on the [Chaincode as a Service](https://hyperledger-fabric.readthedocs.io/en/latest/cc_service.html)
deployment pattern.  This test network uses the [fabric-ccs-builder](https://github.com/jkneubuh/fabric-ccs-builder/tree/feature/docker-bundle)
image `release`, `build`, and `detect` binaries, copied into the peer pods via an init container at 
deployment time.  Before starting the test network, build the ccs image locally:

```shell
git clone https://github.com/hyperledgendary/fabric-ccs-builder.git /tmp/fabric-ccs-builder

docker build -t hyperledgendary/fabric-ccs-builder /tmp/fabric-ccs-builder
```

## Test Network (PROTO / SCRATCH)

### KIND

```shell
bin/make-kind-with-reg.sh

kind load docker-image hyperledgendary/fabric-ccs-builder
```

### Config

```shell
kubectl create -f kube/pv-fabric.yaml
kubectl create -f kube/ns-test-network.yaml
kubectl -n test-network create -f kube/pvc-fabric.yaml
kubectl -n test-network create configmap fabric-config --from-file=config/
kubectl -n test-network create configmap chaincode-config --from-file=chaincode/
kubectl -n test-network create -f kube/debug.yaml
```

### Channel Artifacts

```shell
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
- [ ] This project uses a docker registry running at `localhost:5000` to support rapid development cycles and avoid docker hub.

```shell
docker build \
  -t localhost:5000/hyperledger/asset-transfer-basic \
  ../asset-transfer-basic/chaincode-external
  
docker push localhost:5000/hyperledger/asset-transfer-basic
```

```shell
kubectl -n test-network apply -f kube/cc-asset-transfer-basic.yaml 
```

### Approve and Commit

```shell
kubectl -n test-network exec deploy/org1-peer1 -i -t -- /bin/sh
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

peer chaincode \
  query \
  -C mychannel \
  -n basic \
  -c '{"Args":["ReadAsset","1"]}'
```

### Reset Network
```shell
kubectl -n test-network exec deploy/debug -t -- rm -rf /var/hyperledger/fabric
kubectl delete namespace test-network 
kubectl delete pv fabric
```

or 
```shell
kind delete cluster
```


## REST Sample Application 

TODO: 
- fabric-rest-sample docker image, build and push to localhost:5000 registry 
- construct fabric sample deployment descriptor (cert.pem, p_k, tlsCA, connection profile, etc.)
- sample notes with pointers to crypto spec assets  
- weft to synthesize connection profile
