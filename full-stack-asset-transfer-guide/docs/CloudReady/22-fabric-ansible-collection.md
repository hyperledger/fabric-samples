# Deploy a Fabric Network

[PREV: Deploy a Kube](10-kube.md) <==> [NEXT: Install Chaincode](30-chaincode.md)

---

In addition to a graphical interface, [Fabric Operations Console](https://github.com/hyperledger-labs/fabric-operations-console) provides a set of RESTful service SDKs which can be utilized to realize a network in a declarative fashion using the Fabric [Blockchain Ansible Collection](https://github.com/IBM-Blockchain/ansible-collection).

With ansible, a Fabric network of CAs, Peers, Orderers, Channels, Chaincode, and Identities are
realized by applying a series of playbooks to realize the target configuration.

## Ready?

```shell

just check-kube

```

## Build a Fabric Network

The first step is to create the configuration that Ansible will use, then run the Ansible Playbooks

### Define the namespace and storage class that will be used for console

```shell
export WORKSHOP_NAMESPACE="fabricinfra"
# for *IBM Cloud K8S and Openshift* use this storage class
export WORKSHOP_STORAGE_CLASS="ibmc-file-gold"
```


### Configure Ingress controller to the cluster
*IBMCloud IKS Clusters and Kind* 

```shell
just nginx
```

Check the Ingress controllers domain

For IKS:
```shell
export INGRESS_IPADDR=$(kubectl -n ingress-nginx get svc/ingress-nginx-controller -o json | jq -r '.status.loadBalancer.ingress[0].ip')
export WORKSHOP_INGRESS_DOMAIN=$(echo $INGRESS_IPADDR | tr -s '.' '-').nip.io
```

For Kind:
```shell
export WORKSHOP_INGRESS_DOMAIN=localho.st
```

*IBM Cloud Openshift*

The ingress subdomain can be obtained from the Cluster's dashboard, for example

```shell
export WORKSHOP_INGRESS_DOMAIN=theclusterid.eu-gb.containers.appdomain.cloud
```

### Generate Ansible Playbook configuration

```shell
# check the output to ensure the correct domain, storage class and namespace
just ansible-review-config
```

Please check the local `_cfg/operator-console-vars.yaml` file. Ensure that the ingress domain, storage class and namespace are correct.  By default the all the `WORKSHOP_xxx` varirables are used to see the Ansible configuration, but it's worth double checking the files

For example:
```shell
# this MUST be set to either k8s or openshift
target: openshift
# Console name/domain
console_domain: 203-0-113-42.nip.io
console_storage_class: ibmc-file-gold
```

**For Openshift, please ensure that the `type: openshift` is set**

```
target: openshift
```

- Set Kubectl context

A Kubectl context is also requried - the default behaviour is use the current context.


Alternatively your K8S provider may give you a different command to get the K8S cxontext.
For IKS use this command instead
```shell
ibmcloud ks cluster config --cluster <clusterid> --output yaml > _cfg/k8s_context.yaml
```

The `k8s_context.yaml` will be detected by the shell scripts and that will be used


- Run the [00-complete](../../infrastructure/fabric_network_playbooks/00-complete.yml) play:
```shell

# if you are using IKS/KIND 
# do not do this for OpenShift
just ansible-ingress


# Start the operator and Fabric Operations Console
just ansible-operator
just ansible-console

# Construct a network and channel with ansible playbooks
just ansible-network

```
The console will be available at the Nginx ingress domain alias:
`https://fabricinfra-hlf-console-console.<WORKSHOP_INGRESS_DOMAIN>`


In a browser, connect to this URL (accepting the self-signed certificate), log in as `admin` password `password` and view the network structure in the Operations Console user interface. (you will be prompted to change the password!)

## Generate configuration files

To connect applications details of the Gateway Endpoints with TLS certificates and the identies to use are required.
The Ansible scripts will have written several files to the `_cfg` directory, run `ls -1` to see the files and refer to the table below for what file is

| Filename                     | Contents                                                              |
|------------------------------|-----------------------------------------------------------------------|
| 'Ordering Org Admin.json'    | Ordering Organizations Admin identity                                 |
| 'Ordering Org CA Admin.json' | Ordering Organization's Certificate Authority's Admin Identity        |
| 'Org1 Admin.json'            | Organization 1's Admin identity                                       |
| 'Org1 CA Admin.json'         | Organization 1's Certificate Authority's Admin Identity               |
| 'Org2 Admin.json'            | Organization 2's Admin identity                                       |
| 'Org2 CA Admin.json'         | Organization 2's Certificate Authority's Admin Identity               |
| auth-vars.yml                | Configuration for Ansible to connect to the Fabric Operations Console |
| fabric-common-vars.yml       | Ansible Configuartion - for common and shared values                  |
| fabric-ordering-org-vars.yml | - for the ordering organization                                       |
| fabric-org1-vars.yml         | - for organization 1                                                  |
| fabric-org2-vars.yml         | - for ogranization 2                                                  |
| operator-console-vars.yml    | - for creating the operator an dconsole                               |


---

[PREV: Deploy a Kube](10-kube.md) <==> [NEXT: Install Chaincode](31-fabric-ansible-chaincode.md)
