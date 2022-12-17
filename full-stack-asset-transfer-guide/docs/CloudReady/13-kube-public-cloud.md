# Deploy a Kubernetes Cluster 

[PREV: Setup](00-setup.md) <==> [NEXT: Deploy a Fabric Network](20-fabric.md)

---

## Ready?

```shell

just check-setup

```

## Provision a cloud Kubernetes Instance

- Provision an IKS or EKS Kubernetes service at IBM or Amazon Cloud. 

   - Configure a 3x 4 CPU / 16 GRAM worker pool. 
   - single region is OK. 


- Configure your `kubectl` CLI for access to the remote cluster.

- Test CLI access to the cluster:
```shell

kubectl cluster-info

```

- Open a new shell and observe the target namespace: 
```shell

k9s -n test-network

```


## Configuration Options

### IBM Cloud / IKS 
```shell

export WORKSHOP_NAMESPACE="test-network"
export WORKSHOP_CLUSTER_RUNTIME="k3s"
export WORKSHOP_COREDNS_DOMAIN_OVERRIDE="false"
export WORKSHOP_STAGE_DOCKER_IMAGES="false"
export WORKSHOP_STORAGE_CLASS="ibmc-file-gold"

```


### Amazon Web Services / EKS 
```shell

export WORKSHOP_NAMESPACE="test-network"
export WORKSHOP_CLUSTER_RUNTIME="k3s"
export WORKSHOP_COREDNS_DOMAIN_OVERRIDE="false"
export WORKSHOP_STAGE_DOCKER_IMAGES="false"
export WORKSHOP_STORAGE_CLASS="gp2"

```
###Digital ocean / DOKS 
```shell

export WORKSHOP_NAMESPACE="test-network"
export WORKSHOP_CLUSTER_RUNTIME="k3s"
export WORKSHOP_COREDNS_DOMAIN_OVERRIDE="false"
export WORKSHOP_STAGE_DOCKER_IMAGES="false"
export WORKSHOP_STORAGE_CLASS="do-block-storage"

```

## Install Nginx Ingress 

- Install the Nginx controller to the cluster
```shell

just nginx

```


## Cluster Ingress DNS Domain

### IKS 
```shell

export INGRESS_IPADDR=$(kubectl -n ingress-nginx get svc/ingress-nginx-controller -o json | jq -r '.status.loadBalancer.ingress[0].ip')
export WORKSHOP_INGRESS_DOMAIN=$(echo $INGRESS_IPADDR | tr -s '.' '-').nip.io

```

### EKS 
```shell

export INGRESS_HOSTNAME=$(kubectl -n ingress-nginx get svc/ingress-nginx-controller -o json  | jq -r '.status.loadBalancer.ingress[0].hostname')
export INGRESS_IPADDR=$(dig $INGRESS_HOSTNAME +short)
export WORKSHOP_INGRESS_DOMAIN=$(echo $INGRESS_IPADDR | tr -s '.' '-').nip.io

```
### Digital ocean 
```shell

export INGRESS_HOSTNAME=$(kubectl -n ingress-nginx get svc/ingress-nginx-controller -o json  | jq -r '.status.loadBalancer.ingress[0].ip')
export WORKSHOP_INGRESS_DOMAIN=$(echo $INGRESS_HOSTNAME | tr -s '.' '-').nip.io

```




# Take it Further

During the workshop, one of the steps involves building a chaincode image, tagging the 
image, and publishing to an insecure docker registry running at localhost:5000.  For cloud
based clusters, the remote instance will not have access to the local insecure registry.

To upload custom chaincode, configure your local docker client with access to an IBM 
cloud / public container registry.  In addition, make sure that the Fabric target namespace
has read access to the repository, allowing the pods created in the cluster with access to 
your code.

To run the workshop without building and uploading custom code, you can install a chaincode
package using the reference asset-transfer smart contract.  This reference sample has been
made available for public read access, and does not require `imagePullSecrets` for the
chaincode pods to be started in the cluster.

To install the reference smart contract, in the "Install Chaincode" section of the workshop,
skip the "build image" sections and [install the contract from a CI pipeline](https://github.com/jkneubuh/full-stack-asset-transfer-guide/blob/feature/iks-notes/docs/CloudReady/30-chaincode.md#install-chaincode-from-a-ci-pipeline).


---
[PREV: Setup](00-setup.md) <==> [NEXT: Deploy a Fabric Network](20-fabric.md)
