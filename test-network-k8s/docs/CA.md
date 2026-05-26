# Certificate Authorities

This guide serves as a companion to the [Fabric CA Deployment Guide](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/ca-deploy.html), 
the definitive reference for planning, configuring, and managing CAs within a production Hyperledger Fabric installation.

For individual fabric nodes to communicate securely over a network, all interactions are performed over secure sockets
with (at a minimum) server side TLS certificate verification.  In addition, for the individual participants of a Fabric
network to interact with the blockchain, the participant identities and activities are verified against an Enrollment 
Certificate or 'ECert' authority.

In this document we'll outline the key aspects of bootstrapping test network TLS and ECert CAs, registration and 
enrollment of node identities, and address some effective strategies for storage and organization of channel and 
node local MSP data structures.


### TL/DR : 
```shell
$ ./network up
 
Launching network "test-network":
...
✅ - Initializing TLS certificate Issuers ...
✅ - Launching Fabric CAs ...
✅ - Enrolling bootstrap ECert CA users ...
...
🏁 - Network is ready.
```

## [Planning for a CA](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/ca-deploy-topology.html#planning-for-a-ca)

Setting up a CA framework is one of the more daunting aspects of a Fabric installation.  There is an incredible amount 
of flexibility possible with the Fabric CA architecture, so to keep things straightforward we have opted to aim for a 
simplified, but realistic CA deployment illustrating key touch points with Kubernetes:

- Each organization maintains distinct, [independent volumes](../kube/pv-fabric-org0.yaml) for the storage of MSP and 
  node certificates.  This forces the consortium organizer to plan for the distribution of _public_ certificates to 
  member organizations, while maintaining an independent, secret storage location for _private_ signing keys.  
  

- This guide simplifies the storage and organization of Fabric certificates into two distinct flows.  For securing 
  inter-node communication with TLS, [cert-manager](https://cert-manager.io) is responsible for the lifecycle of issuing, 
  renewing, and revoking SSL certificates and keys as native Kubernetes `Certificate` resources.  Complementing the 
  SSL certificate lifecycle is a set of fabric-CAs responsible for fulfilling Fabric [ECert](../kube/org0/org0-ca.yaml) 
  Enrollments and identities.


- MSP Certificate organization and [Folder Structure](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/use_CA.html#folder-structure-for-your-org-and-node-admin-identities)
  strictly adheres to the best practices and guidelines recommended by the CA Deployment Guide.  


- The `cryptogen` anti-pattern is **strictly forbidden**.  All MSP enrollments are constructed using the CA 
  registration and enrollment REST services, coordinated by calls to `fabric-ca-client`.  At runtime, the ca-client 
  ONLY has visibility to the organization's shared volume mount.


- TLS Certificates are stored and organized within the cluster as a series of `Certificate` resources with associated 
  Kube `Secret` and volume mounts.  Service pods mount the node TLS key pair and CA certificate at `/var/hyperledger/fabric/config/tls`. 
  Each organization in the network maintains an independent [CA `Issuer`](https://cert-manager.io/docs/configuration/ca/) 
  endorsed by a system-wide, self-signed root CA. 


- Each organization in the network maintains an independent fabric CA instance, with configuration and certificates 
  stored in each org's persistent volume at `/var/hyperledger/fabric-ca-server`. 


- fabric-ca-client configuration and certificates are maintained in each org's persistent volume at `/var/hyperledger/fabric-ca-client`


- ECert and MSP enrollment structures are maintained in each org's persistent volume at `/var/hyperledger/fabric/organizations`



### Future Enhancements: 

- **_Bring your own Certificates_** :  It would be nice to bootstrap the network using a single, top-level signing authority, 
  rather than generating self-signed certificates when the system is bootstrapped.  Ideally this will be realized by 
  introducing an [Intermediate CA](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/ca-deploy-topology.html#when-would-i-want-an-intermediate-ca)
  and/or alternate signing chains backed by formal (e.g. letsencrypt, Thawte, Verisign, etc.) certificate authorities. 


- **_Time-Bomb Certificates_** : By default the certificates issued by the test network are valid for 1 (one) year.  For 
  lightweight or adhoc testing, this is fine.  But when applied to production deployments, certificate expiry is a 
  real operational challenge.  For instance, it is possible to soft-lock a Fabric network when all system certificates
  expire _en-masse_ - it's impossible to re-establish a consensus and renew the certificates!
  

- **_Mutual TLS_** : Server-side TLS is a minimum, but the addition of client-side TLS certificates will help fully 
  secure all TCP channels within the Fabric network.  


- **_Bugs_** : `./network up` currently goes through the process of bootstrapping a fabric network from scratch, but 
  does not handle "multiple runs" or the complete course of errors that can occur in the wild.  For instance, If the 
  routine is run multiple times in succession, it will overwrite the network's certificate chains and soft-lock the 
  network.  


## [Process Overview](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#)

The [sequence of activities](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#what-order-should-i-deploy-the-cas) 
necessary to bring up a CA infrastructure is well documented by the CA Deployment Guide:  

1. [Deploy TLS CA Issuers](#deploy-tls-ca-issuers) 
    
1. [Deploy the Organization CA](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#deploy-an-organization-ca)
    1. [Configure the ECert CA Servers](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#modify-the-ca-server-configuration)
    1. [Launch the ECert CA Servers](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#start-the-ca-server)
    1. [Enroll the ECert CA Bootstrap / Admin User](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#enroll-the-ca-admin)


## Deploy TLS CA Issuers 

```
✅ - Initializing TLS certificate Issuers ...
...
```

The Kubernetes Test Network relies on [cert-manager](https://cert-manager.io) to issue, renew, and revoke TLS 
certificates for network endpoints.  Before launching peers, orderers, and chaincode pods, each node must 
have a corresponding [`Certificate`](https://cert-manager.io/docs/usage/certificate/) generated by a cert manager [CA 
`Issuer`](https://cert-manager.io/docs/configuration/ca/), stored in Kubernetes and exposed as a kube `Secret` at 
runtime.

In the test network, the root TLS certificate is automatically generated by requesting a self-signed ECDSA key pair.  
In turn, the root key is used to create a series of CA `Issuers`, one per member organization participating in the 
blockchain: 

```
# Use the self-signing issuer to generate three Issuers, one for each org: 
kubectl -n test-network apply -f kube/org0/org0-tls-cert-issuer.yaml
kubectl -n test-network apply -f kube/org1/org1-tls-cert-issuer.yaml
kubectl -n test-network apply -f kube/org2/org2-tls-cert-issuer.yaml
```

Each organization's CA `Issuer` will be used to construct a TLS `Certificate` for each node in the network.  At 
runtime, the deployment pods will mount the certificate contents (`tls.key`, `tls.pem`, and `ca.pem`) as a kube 
secrets mounted at `/var/hyperledger/fabric/config/tls`.


## [Deploy the Organization CA](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#deploy-an-organization-ca)

The organization (ECert) CA is used to issue MSP certificates for nodes, channels, and identities in the fabric network. 
Before we can set up the peers, orderers, and channels, we will need to bootstrap an ECert CA administrator 
for each org in the network.


### [Configure the ECert CA Servers](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#modify-the-ca-server-configuration)

When launching the ECert CA pods, both the org volume shares and org config maps are made available via volume shares. 
The [fabric-ecert-ca-server.yaml](../config/org0/fabric-ca-server-config.yaml) includes overrides for:

- `port: 443` binds all traffic to the default HTTPS port
- `tls.enabled: true` enables TLS for registration and enrollment requests
- `ca.name: <service-name>` matches the Kubernetes `Service` host alias
- `csr.hosts:` includes host aliases for accessing the CA with Kube DNS


### [Launch the ECert CA Servers](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#start-the-ca-server)
```shell
✅ - Launching ECert CAs ...
```

```shell
kubectl -n test-network apply -f kube/org0/org0-ca.yaml
kubectl -n test-network apply -f kube/org1/org1-ca.yaml
kubectl -n test-network apply -f kube/org2/org2-ca.yaml
```
- [x] Note: The `rcaadmin` enrollment's `cert.pem` and `key.pem` locations are specified in the ecert CA's k8s deployment as environment variables.


### [Enroll the ECert CA Bootstrap / Admin User](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#enroll-the-ca-admin)
```shell
✅ - Enrolling bootstrap ECert CA users ...
```

Finally, after the services are active, we can connect to each organization's ECert CA using TLS and 
activate the `rcaadmin` (Root Certificate Authority) admin user.  This user will be employed to generate the 
local MSP certificate structure for all of the nodes in our test network.

```shell
  fabric-ca-client enroll \
    --url https://'${auth}'@'${ecert_ca}' \
    --tls.certfiles /var/hyperledger/fabric/config/tls/ca.pem \
    --mspdir $FABRIC_CA_CLIENT_HOME/'${ecert_ca}'/rcaadmin/msp
```


## Next Steps : 

After the CAs have been deployed, each org in the Kube namespace includes: 

- One TLS CA `Issuer` and issuer `Certificate`
- One ECert CA `Service`, forwarding internal traffic from https://orgN-ecert-ca to the ECert CA
- One ECert CA `Deployment`  
- One ECert CA `Pod`  
- One ECert CA admin bootstrap user `rcaadmin` enrollment and MSP root certificate.


### [Launch the Test Network...](TEST_NETWORK.md)
