# Kubernetes 

To get started with the Kube test network, you will need access to a Kubernetes cluster.

## TL/DR : 

```shell
$ ./network kind 
Initializing KIND cluster "kind":
✅ - Pulling docker images for Fabric 2.3.2 ...
✅ - Creating cluster "kind" ...
✅ - Launching Nginx ingress controller ...
✅ - Launching container registry "kind-registry" at localhost:5000 ...
🏁 - Cluster is ready.
```

and :
```shell
$ ./network unkind 
Deleting cluster "kind":
☠️  - Deleting KIND cluster kind ...
🏁 - Cluster is gone.
```


## Kube Context: 

For illustration purposes, this project attempts in all cases to _keep it simple_ as the
general rule.  By default, we will rely on `kind` ([Kubernetes IN Docker](https://kind.sigs.k8s.io))
as a mechanism to quickly spin up ephemeral, short-lived clusters for development and 
illustration.

To maximize portability across revisions, vendor distributions, hardware profiles, and 
network topologies, this project relies _exclusively_ on scripted interaction with the 
Kube API controller to reflect updates in a remote cluster.  While this may not be the 
ideal technique for managing production workloads, the objective of this guide is to provide 
clarity on the nuances of Fabric / Kubernetes deployments, rather than an opinionated 
perspective on state of the art techniques for cloud Dev/Ops.  Targeting 
the core Kube APIs means that there is a good chance that the systems will work "as-is" 
simply by setting the kubectl context to reference a cloud-native cluster (e.g. OCP, IKS, 
AWS, etc.)

If you don't have access to an existing cluster, or want to set up a short-lived cluster 
for development, testing, or CI, you can create a new cluster with:

```shell
$ ./network kind
```
or: 
```shell
$ kind create cluster 
```

By default, `kind` will set the current Kube context to reference the new cluster.  Any 
interaction with `kubectl` (or kube-context aware SDKs) will inherit the current context. 

```shell
$ kubectl cluster-info
Kubernetes control plane is running at https://127.0.0.1:55346
CoreDNS is running at https://127.0.0.1:55346/api/v1/namespaces/kube-system/services/kube-dns:dns/proxy

To further debug and diagnose cluster problems, use 'kubectl cluster-info dump'.
```

When you are done with the cluster, tear it down with:
```shell
$ ./network unkind 
```
or: 
```shell
$ kind delete cluster 
```

## Test Network Structure

To emulate a more realistic example of multi-party collaboration, the test network 
forms a blockchain consensus group spanning three virtual organizations.  Access to the 
blockchain is entirely constrained to Kubernetes private networks, and consuming applications 
make use of a Kube ingress controller for external visibility.

In k8s terms: 

- The blockchain is contained within a single Kubernetes `Cluster`. 
- Blockchain services (nodes, orderers, chaincode, etc.) reside within a single `Namespace`.
- Each organization maintains a distinct, independent `PersistentVolumeClaim` for TLS certificates,
  local MSP, private data, and transaction ledgers.
- Smart Contracts rely exclusively on the [Chaincode-as-a-Service](link) and [External Builder](link)
  patterns, running in the cluster as Kube `Deployments` with companion `Services`. 
- An HTTP(s) `Ingress` and companion gateway application is required for external access to the blockchain. 

When running the test network locally, the `./network kind` bootstrap will configure the system with 
an [Nginx ingress controller](link), a private [Container Registry](link), and persistent volumes / claims for 
host-local organization storage. 

Behind the scenes, `./network kind` is running: 

```shell
# Create the KIND cluster and nginx ingress controller bound to :80 and :443 
kind create cluster --name ${TEST_NETWORK_CLUSTER_NAME:-kind} --config scripts/kind-config.yaml

# Create the Kube namespace 
kubectl create namespace ${TEST_NETWORK_NAMESPACE:-test-network}

# Create host persistent volumes (tied the kind-control-plane docker image lifetime)
kubectl create -f kube/pv-fabric-org0.yaml
kubectl create -f kube/pv-fabric-org1.yaml
kubectl create -f kube/pv-fabric-org2.yaml

# Create persistent volume claims binding to the host (docker) volumes 
kubectl -n $NS create -f kube/pvc-fabric-org0.yaml 
kubectl -n $NS create -f kube/pvc-fabric-org1.yaml 
kubectl -n $NS create -f kube/pvc-fabric-org2.yaml 
```

## Container Registry

The [kube yaml descriptors](../kube) generally rely on the public Fabric images maintained at the public 
Docker and GitHub container registries.  For casual usage, the test network will bootstrap and launch CAs, 
peers, orderers, chaincode, and sample applications without any additional configuration.

While public images are made available for pre-canned samples, there will undoubtedly be cases 
where you would like to build custom chaincode, gateway client applications, or custom builds of core 
Fabric binaries without uploading your code to a public registry.  For this purpose, the Kube test 
network includes a [Local Registry](https://kind.sigs.k8s.io/docs/user/local-registry/) available for 
you to _quickly_ deploy custom images directly into the cluster without uploading your code to the 
Internet.

By default, the [kind.sh](../scripts/kind.sh) bootstrap will configure and link up a local container 
registry running at `localhost:5000/`.  Images pushed to this registry will be immediately available 
to Pods deployed to the local cluster.  

For dev/test/CI based flows using an external registry, the traditional Kubernetes practice of 
[Adding ImagePullSecrets to a service account](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/#add-imagepullsecrets-to-a-service-account) 
still applies.

In some environments, KIND may encounter issues loading the Fabric docker images from the public container 
registries.  In addition, for Fabric development it can be advantageous to work with Docker images built 
locally, bypassing the public images entirely.  For these scenarios, images may also be [directly loaded](https://kind.sigs.k8s.io/docs/user/quick-start/#loading-an-image-into-your-cluster)
into the KIND image plane, bypassing the container registry.

The `./network` script supports these additional modes via: 

1.  For network-constrained environments, pull all images to the local docker cache and load to KIND: 
```shell
export TEST_NETWORK_STAGE_DOCKER_IMAGES=true 

./network kind 
./network up  
```

2.  For alternate registries (e.g. local or Fabric CI/CD builds): 
```shell
./network kind 

export TEST_NETWORK_FABRIC_CONTAINER_REGISTRY=hyperledger-fabric.jfrog.io
export TEST_NETWORK_FABRIC_VERSION=amd64-latest 
export TEST_NETWORK_FABRIC_CA_VERSION=amd64-latest

./network up  
```

3.  For working with Fabric images built locally: 
```shell
./network kind 

make docker    # in hyperledger/fabric 

export TEST_NETWORK_FABRIC_VERSION=2.4.0

./network load-images 
./network up 
```

## Cloud Vendors 

While the test network primarily targets KIND clusters, the singular reliance on the Kube API plane 
means that it should also work without modification on any modern cloud-based or bare metal 
Kubernetes distribution.  While supporting the entire ecosystem of cloud vendors is not in scope 
for this sample project, we'd love to hear feedback, success stories, or bugs related to applying the 
test network to additional platforms.

In general, at a high-level the steps required to port the test network to ANY kube vendor are: 

- Configure an HTTP `Ingress` for access to any gateway, REST, or companion blockchain applications.
- Register `PersistentVolumeClaims` for each of the organizations in the test network. 
- Create a `Namespace` for each instance of the test network.
- Upload your chaincode, gateway clients, and application logic to an external Container Registry. 
- Run with a `ServiceAccount` and role bindings suitable for creating `Pods`, `Deployments`, and `Services`. 

Example configurations for common cloud vendors: 

### IKS 
### OCP 
### AWS 
### Azure 

## Next : [Fabric Certificate Authorities](CA.md)

