# Hyperledger Fabric Kubernetes Test Network

Create a 
Hyperledger Fabric [test-network](https://github.com/hyperledger/fabric-samples/tree/main/test-network) 
on [KIND](https://kind.sigs.k8s.io) 
with [fabric-operator](https://github.com/hyperledger-labs/fabric-operator).  

Objective:  provide _crystal clarity_ to Fabric's _MSP_ and certificate structures, 
focusing on the inductive construction of a multi-organization network.

![Dark Side of the Moon](https://upload.wikimedia.org/wikipedia/en/3/3b/Dark_Side_of_the_Moon.png)
###### The Dark Side of the Moon - Pink Floyd ([From Wikipedia, the free encyclopedia](https://en.wikipedia.org/wiki/File:Dark_Side_of_the_Moon.png) )


## The Venue:

To run this sample locally, clone the git repo and follow the dependency checklist:
```shell
./scripts/check.sh
```

This scenario is _slow_ but _predictable_.  The focus in this example is not efficiency, but to 
demonstrate the construction of a multi-org network, highlighting a production-realistic scenario
of running a Fabric network spanning multiple Kubernetes clusters, namespaces, or cloud-vendors.

In typical examples of constructing a fabric test network, the use of `cryptogen` is highlighted as 
an efficient and convenient mechanism to avoid complexities of CA bootstrap, node enrollments, and 
the exchange of consortium MSP certificates as part of the channel configuration.

By contrast, this scenario sets up a multi-org Fabric network, illustrating a _correct_ ordering of 
CA initialization, node / admin enrollments, MSP certificate exchange, and channel construction 
without the assumption of a central file system or volume mount.  With minor modifications, this 
example can be extended to use `rsync` or an SSH protocol to exchange channel MSP for a network 
spanning multiple, independent Kubernetes clusters.  For convenience, this example allocates a 
dedicated k8s namespace for each organization, running on a shared virtual KIND cluster.

For best results, start a new terminal for each organization in the consortium.  (Imagine that each
shell is running commands on behalf of the org's Fabric administrator.)


## The Stage:

```shell
git clone https://github.com/hyperledger/fabric-samples.git
cd test-network-k8s-operator
```

Create a KIND kubernetes cluster, *.localho.st ingress, and local container registry: 
```shell
just kind 
```


## Act I: Launch CAs, peers, and orderers

Start the nodes in the network: 
```shell
just start org0
just start org1
just start org2
```

Enroll admin, rcaadmin, and gateway users at the org CAs: 
```shell
just enroll org0
just enroll org1
just enroll org2
```

```shell
just check-network
```

## Act II: Build a Consortium

```shell
just export-msp org0
just export-msp org1
just export-msp org2
```

```shell
just create-genesis-block

just inspect-genesis-block
```

```shell
just join org0
just join org1
just join org2
```


## Act III: Chaincode and Gateway Application 

Install [asset-transfer](https://github.com/hyperledger/fabric-samples/tree/main/full-stack-asset-transfer-guide/contracts/asset-transfer-typescript)
version [0.1.4](https://github.com/hyperledgendary/full-stack-asset-transfer-guide/releases/tag/v0.1.4) with the
Kubernetes [chaincode builder](https://github.com/hyperledger-labs/fabric-builder-k8s):

```shell
just install-cc org1
just install-cc org2
```

### Ad Hoc peer CLI: 

org1: 
```shell
export ORG=org1
export MSP_ID=Org1MSP 

export $(just show-context $MSP_ID $ORG peer1)

peer chaincode query \
  -n asset-transfer \
  -C mychannel \
  -c '{"Args":["org.hyperledger.fabric:GetMetadata"]}'  
```

org2: 
```shell
export ORG=org2
export MSP_ID=Org2MSP 

export $(just show-context $MSP_ID $ORG peer1) 

peer chaincode query \
  -n asset-transfer \
  -C mychannel \
  -c '{"Args":["org.hyperledger.fabric:GetMetadata"]}' 
```


### Gateway Client

When the org1 and org2 CAs are created, they include a bootstrap [registration](organizations/org1/org1-ca.yaml#L50-L52) 
and [enrollment](organizations/org1/enroll.sh#L48) of a client identity for use in gateway application development.

If the `just show-context` commands (above) have been loaded into the terminal, the peer, orderer, and
CA certificate paths have been loaded into the environment.

In an org admin shell, load the gateway client environment for [trader-typescript](https://github.com/hyperledger/fabric-samples/tree/main/full-stack-asset-transfer-guide/applications/trader-typescript): 
```shell
# local MSP enrollment folder for the org client user
export USER_MSP_DIR=$PWD/organizations/$ORG/enrollments/${ORG}user/msp

# Path to private key file 
export PRIVATE_KEY=$USER_MSP_DIR/keystore/key.pem

# Path to user certificate file
export CERTIFICATE=$USER_MSP_DIR/signcerts/cert.pem

# Path to CA certificate
export TLS_CERT=$CORE_PEER_TLS_ROOTCERT_FILE

# Connect client applications to the load-balancing gateway peer alias:
export ENDPOINT=${ORG}-peer-gateway.${ORG}.localho.st:443
```

- Compile the trader-typescript application:
```shell
pushd ../full-stack-asset-transfer-guide/applications/trader-typescript

npm install
``` 

```shell
# Create a yellow banana token
npm start create banana bananaman yellow

npm start getAllAssets

# Transfer the banana among users / orgs 
npm start transfer banana appleman Org1MSP

npm start getAllAssets

# Transfer the banana among users / orgs 
npm start transfer banana bananaman Org2MSP

# Error! Which org owns the banana? 
npm start transfer banana bananaman Org1MSP
```


## Teardown

```shell
# Tear down the network 
just destroy
```
or
```shell
# Tear down the kubernetes cluster
just unkind
```
