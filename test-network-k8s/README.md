# Kubernetes Test Network 

This project re-establishes the Hyperledger [test-network](../test-network) as a _cloud native_ application.

### Objectives:

- Provide a simple, _one click_ activity for running the Fabric test network.
- Provide a reference guide for deploying _production-style_ networks on Kubernetes.
- Provide a _cloud ready_ platform for developing chaincode, Gateway, and blockchain apps.
- Provide a Kube supplement to the Fabric [CA Operations and Deployment](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/ca-deploy.html) guides.
- Support a transition to [Chaincode as a Service](https://hyperledger-fabric.readthedocs.io/en/latest/cc_service.html).
- Support a transition from the Internal, Docker daemon to [External Chaincode](https://hyperledger-fabric.readthedocs.io/en/latest/cc_launcher.html) builders.
- Run on any Kube.

_Fabric, Ahoy!_ 


## Prerequisites 

- [Docker](https://www.docker.com)
- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- [kind](https://kind.sigs.k8s.io/docs/user/quick-start/#installation)
- [jq](https://stedolan.github.io/jq/)


## Quickstart

Create a local Kubernetes cluster:
```shell
./network kind
```

Launch the network, create a channel, and deploy the [basic-asset-transfer](../asset-transfer-basic) smart contract: 
```shell
./network up
./network channel create

./network chaincode deploy asset-transfer-basic basic_1.0 $PWD/../asset-transfer-basic/chaincode-java
```

Invoke and query chaincode:
```shell
./network chaincode invoke asset-transfer-basic '{"Args":["CreateAsset","1","blue","35","tom","1000"]}' 
./network chaincode query  asset-transfer-basic '{"Args":["ReadAsset","1"]}'
```

Access the blockchain with a [REST API](https://github.com/hyperledger/fabric-samples/tree/main/asset-transfer-basic/rest-api-typescript): 
```shell
./network rest-easy
```

Tear down the test network: 
```shell
./network down 
```

Tear down the cluster: 
```shell
./network unkind
```


## [Detailed Guides](docs/README.md)

- [Working with Kubernetes](docs/KUBERNETES.md)
- [Certificate Authorities](docs/CA.md)
- [Launching the Test Network](docs/TEST_NETWORK.md)
- [Working with Channels](docs/CHANNELS.md)
- [Working with Chaincode](docs/CHAINCODE.md)
- [Working with Applications](docs/APPLICATIONS.md)


## Areas for Improvement / TODOs

- [ ] Refine the recipe and guidelines for use with `k3s` / `nerdctl` (rancherdesktop.io) as an alternative to Docker / KIND.
- [ ] Test the recipe with OCP, AWS, gcp, Azure, etc. (These should ONLY differ w.r.t. pvc and ingress)
- [ ] Address any of the 20+ todo: notes in network.sh
- [ ] Implement mutual TLS across peers, orderers, and clients. 
- [ ] Caliper?  
