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


## Quickstart

Create a local Kubernetes cluster:
```shell
./network kind
```

Launch the network, create a channel, and deploy the [basic-asset-transfer](../asset-transfer-basic) smart contract: 
```shell
./network up
./network channel create
./network chaincode deploy
```

Invoke and query chaincode:
```shell
./network chaincode invoke '{"Args":["CreateAsset","1","blue","35","tom","1000"]}' 
./network chaincode query '{"Args":["ReadAsset","1"]}'
```

Access the blockchain with a [REST API](https://github.com/hyperledgendary/fabric-rest-sample/tree/main/asset-transfer-basic/rest-api-typescript#rest-api): 
```
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

- [`./network`](docs/NETWORK.md)
- [Working with Kubernetes](docs/KUBERNETES.md)
- [Certificate Authorities](docs/CA.md)
- [Launching the Test Network](docs/TEST_NETWORK.md)
- [Working with Channels](docs/CHANNELS.md)
- [Working with Chaincode](docs/CHAINCODE.md)
- [Working with Applications](docs/APPLICATIONS.md)


## Areas for Improvement / TODOs

- [ ] Test the recipe with OCP, AWS, gcp, Azure, etc. (These should ONLY differ w.r.t. pvc and ingress)
- [ ] Implement @celder mechanism for bootstrapping dual-headed CAs w/o poisoning the root CA on expiry.
- [ ] Address any of the 20+ todo: notes in network.sh
- [ ] Implement mutual TLS across peers, orderers, and clients. 
- [ ] Caliper?  
