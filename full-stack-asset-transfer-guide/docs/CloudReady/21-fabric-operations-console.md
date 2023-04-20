# Deploy a Fabric Network

[PREV: Deploy a Kube](10-kube.md) <==> [NEXT: Install Chaincode](30-chaincode.md)

---

[Fabric Operations Console](https://github.com/hyperledger-labs/fabric-operations-console) provides an
interactive GUI layer and set of service SDKs suitable for the programmatic administration of a
Fabric network.

![Fabric Operations Console](https://github.com/hyperledger-labs/fabric-operations-console/blob/main/docs/images/architecture_hl.png)


## Ready?

```shell

just check-kube

```

## Operations Console

- Install the fabric-operator [Kubernetes Custom Resources](https://kubernetes.io/docs/concepts/extend-kubernetes/api-extension/custom-resources/)
```shell

kubectl apply -k https://github.com/hyperledger-labs/fabric-operator.git/config/crd

```

- Install the Fabric operator and console in the target namespace: 
```shell

just console

```

- Connect to the console GUI at the hyperlink printed to the terminal.

- Use the Console GUI or [Ansible Collection](22-fabric-ansible-collection.md) to
  [Build a Network](https://cloud.ibm.com/docs/blockchain?topic=blockchain-ibp-console-build-network)


---

[PREV: Deploy a Kube](10-kube.md) <==> [NEXT: Install Chaincode](30-chaincode.md)
