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
✅ - Launching TLS CAs ...
✅ - Enrolling bootstrap TLS CA users ...

✅ - Registering and enrolling ECert CA bootstrap users ...
✅ - Launching ECert CAs ...
✅ - Enrolling bootstrap ECert CA users ...
...
🏁 - Network is ready.
```


## [Planning for a CA](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/ca-deploy-topology.html#planning-for-a-ca)

Setting up a CA framework is one of the more daunting aspects of a Fabric installation.  There is an incredible amount 
of flexibility possible with the Fabric CA architecture, so to keep things straightforward we have opted to aim for a 
simplified, but realistic CA deployment illustrating the key touch points with Kubernetes:

- Each organization maintains distinct, [independent volumes](../kube/pv-fabric-org0.yaml) for the storage of MSP and 
  TLS certificates.  This forces the consortium organizer to plan for the distribution of _public_ certificates to 
  member organizations, while maintaining an independent, secret storage location for _private_ signing keys.  
  

- Each organization maintains two distinct, separate CA instances : one dedicated to [TLS](../kube/org0/org0-tls-ca.yaml)
  Certificate Signing Requests, and a second process dedicated to [ECert](../kube/org0/org0-ecert-ca.yaml) Enrollments 
  and identity MSPs.
  

- Certificate organization and [Folder Structure](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/use_CA.html#folder-structure-for-your-org-and-node-admin-identities)
  strictly adheres to the best practices and guidelines recommended by the CA Deployment Guide. 


- The `cryptogen` anti-pattern is **strictly forbidden**.  All TLS and MSP enrollments are constructed using the CA 
  registration and enrollment REST services, coordinated by calls to `fabric-ca-client` running directly on the 
  CA pods.  When working with certificates, the fabric CA client ONLY has visibility to the organization's local volume 
  storage. 


- TLS CA configuration and certificates are maintained in each org's persistent volume at `/var/hyperledger/fabric-tls-ca-server`


- ECert CA configuration and certificates are maintained in each org's persistent volume at `/var/hyperledger/fabric-ca-server`


- fabric-ca-client configuration and certificates are maintained in each org's persistent volume at `/var/hyperledger/fabric-ca-client`


- ECert and MSP data structures are maintained in each org's persistent volume at `/var/hyperledger/fabric/organizations`



### Future Enhancements: 

- **_Bring your own Certificates_** :  It would be nice to boostrap the network using a single, top-level signing authority, 
  rather than generating self-signed certificates when the system is bootstrapped.  Ideally this will be realized by 
  introducing an [Intermediate CA](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/ca-deploy-topology.html#when-would-i-want-an-intermediate-ca)
  and/or alternate signing chains backed by formal (e.g. letsencrypt, Thawte, Verisign, etc.) certificate authorities. 


- **_Dual Headed CAs_** : In practice, juggling two distinct deployments between TLS and ECert servers adds little 
  functional value.  It would be nice to simplify the configuration, deployment, and bootstrapping scripts such that 
  each org manages a single, dual-headed CA capable of responding to both TLS as well as ECert enrollmnent rerquests.
  

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

1. [Deploy the TLS CAs](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#deploy-the-tls-ca) 
    1. [Configure the TLS CA Servers](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#modify-the-tls-ca-server-configuration)
    1. [Launch the TLS CA Servers](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#start-the-tls-ca-server)      
    1. [Enroll the TLS CA Bootstrap Admin Users](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#enroll-bootstrap-user-with-tls-ca)
    
1. [Deploy the Organization CA](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#deploy-an-organization-ca)
    1. [Register and enroll the org CA bootstrap identity with the TLS CA](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#register-and-enroll-the-organization-ca-bootstrap-identity-with-the-tls-ca)
    1. [Configure the ECert CA Servers](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#modify-the-ca-server-configuration)
    1. [Launch the ECert CA Servers](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#start-the-ca-server)
    1. [Enroll the ECert CA Bootstrap / Admin User](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#enroll-the-ca-admin)


## [Deploy the TLS CAs](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#deploy-the-tls-ca)

### [Configure the TLS CA Servers](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#modify-the-tls-ca-server-configuration) 

While the CA guide suggests running the `fabric-ca-server` binary to generate a default configuration file, for the 
test network we've skipped this step and have added a [config/fabric-tls-ca-server-config.yaml](../config/org0/fabric-tls-ca-server-config.yaml) 
to the top level of this project.  

Changes have been made to reflect: 

- `port: 443` binds all traffic to the default HTTPS port
- `tls.enabled: true` enables TLS for registration and enrollment requests
- `ca.name: <service-name>` matches the Kubernetes `Service` host alias 
- `csr.hosts:` includes host aliases for accessing the CA with Kube DNS 


Prior to launching the CA, for each org we create a configmap including the TLS CA server yaml: 

```shell
kubectl -n test-network create configmap org0-config --from-file=config/org0
kubectl -n test-network create configmap org1-config --from-file=config/org1
kubectl -n test-network create configmap org2-config --from-file=config/org2
```


### [Launch the TLS CA Servers](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#start-the-tls-ca-server)

```shell
✅ - Launching TLS CAs ...
```

For each org we create a Kube Deployment and Service, ensuring that the org config 
map and persistent volume maps to the correct location on disk.   

```shell
kubectl -n test-network apply -f kube/org0/org0-tls-ca.yaml
kubectl -n test-network apply -f kube/org1/org1-tls-ca.yaml
kubectl -n test-network apply -f kube/org2/org2-tls-ca.yaml
```

As a side-effect of bootstrapping the TLS CA, each storage volume will include a self-signed certificate 
pair to serve as the **Root TLS Certificate**.  Pay special attention to this path, as it will be used extensively 
to verify the TLS host name of all services within the organization: 
```shell 
${FABRIC_CA_CLIENT_HOME}/tls-root-cert/tls-ca-cert.pem 
```


### [Enroll the TLS CA Bootstrap Admin Users](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#enroll-bootstrap-user-with-tls-ca)
```shell
✅ - Enrolling bootstrap TLS CA users ...
```

After the TLS server is running, we need to enroll the bootstrap admin user with the CA.  This admin user will 
then be employed to fulfill a Certificate Signing request for the ECert CA servers, allowing for full host 
verification when connecting to the ECert CAs via https.  

To enroll the bootstrap TLS CA users, each org runs within the TLS CA pod: 
```shell
  fabric-ca-client enroll \
    --url https://'$auth'@'${tlsca}' \
    --tls.certfiles $FABRIC_CA_CLIENT_HOME/tls-root-cert/tls-ca-cert.pem \
    --csr.hosts '${tlsca}' \
    --mspdir $FABRIC_CA_CLIENT_HOME/tls-ca/tlsadmin/msp
```

The --mspdir output of this command is a set of certificates for use with the ECert CA.  This enrollment MSP 
will be used to register and enroll the ECert bootstrap user. 


## [Deploy the Organization CA](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#deploy-an-organization-ca)

The organization (ECert) CA is used to issue MSP certificates for nodes, channels, and identities in the fabric network. 
Before we can set up the peers, orderers, and channels, we will need to bootstrap an ECert CA administrator 
for each org in the network.


### [Register and enroll the organization CA bootstrap identity with the TLS CA](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#register-and-enroll-the-organization-ca-bootstrap-identity-with-the-tls-ca)
```shell
✅ - Registering and enrolling ECert CA bootstrap users ...
```

The TLS CA can be used to fulfill a Certificate Signing Request on behalf of each organization's ECert CA.   

```shell
  fabric-ca-client register \
    --id.name rcaadmin \
    --id.secret rcaadminpw \
    --url https://'${tlsca}' \
    --tls.certfiles $FABRIC_CA_CLIENT_HOME/tls-root-cert/tls-ca-cert.pem \
    --mspdir $FABRIC_CA_CLIENT_HOME/tls-ca/tlsadmin/msp

  fabric-ca-client enroll \
    --url https://'${tlsauth}'@'${tlsca}' \
    --tls.certfiles $FABRIC_CA_CLIENT_HOME/tls-root-cert/tls-ca-cert.pem \
    --csr.hosts '${ecertca}' \
    --mspdir $FABRIC_CA_CLIENT_HOME/tls-ca/rcaadmin/msp
```

**Important**: The output from this enrollment includes the ECert CA's public certificate and private signing keys.
When the ECert CA pod is launched, the server configuration references the `tls.certfile` and `tls.keyfile` attributes 
by specifying `FABRIC_CA_SERVER_TLS_CERTFILE` and `FABRIC_CA_SERVER_TLS_KEYFILE` environment in the pod's environment.


### [Configure the ECert CA Servers](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#modify-the-ca-server-configuration)

When launching the ECert CA pods, both the org volume shares and org config maps are made available via volume shares. 
The [fabric-ecert-ca-server.yaml](../config/org0/fabric-ecert-ca-server-config.yaml) includes overrides for:

- `port: 443` binds all traffic to the default HTTPS port
- `tls.enabled: true` enables TLS for registration and enrollment requests
- `ca.name: <service-name>` matches the Kubernetes `Service` host alias
- `csr.hosts:` includes host aliases for accessing the CA with Kube DNS

In addition, pay special attention to the location of the `FABRIC_CA_SERVER_TLS_CERTFILE` and `FABRIC_CA_SERVER_TLS_KEYFILE` 
environment variables in the [ECert deployment descriptor](../kube/org0/org0-ecert-ca.yaml).  These variables 
reference the TLS certificate authority and signing keys as generated by the admin bootstrap enrollment.  


### [Launch the ECert CA Servers](https://hyperledger-fabric-ca.readthedocs.io/en/latest/deployguide/cadeploy.html#start-the-ca-server)
```shell
✅ - Launching ECert CAs ...
```

```shell
kubectl -n test-network apply -f kube/org0/org0-ecert-ca.yaml
kubectl -n test-network apply -f kube/org1/org1-ecert-ca.yaml
kubectl -n test-network apply -f kube/org2/org2-ecert-ca.yaml
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
    --tls.certfiles $FABRIC_CA_CLIENT_HOME/tls-root-cert/tls-ca-cert.pem \
    --mspdir $FABRIC_CA_CLIENT_HOME/'${ecert_ca}'/rcaadmin/msp
```


## Next Steps : 

After the CAs have been deployed, each org in the Kube namespace includes: 

- One TLS CA `Service`, forwarding internal traffic from https://orgN-tls-ca to the TLS CA
- One TLS CA `Deployment`  
- One TLS CA `Pod` 
- One ECert CA `Service`, forwarding internal traffic from https://orgN-ecert-ca to the ECert CA
- One ECert CA `Deployment`  
- One ECert CA `Pod`  
- One TLS CA admin bootstrap user `tlsadmin` enrollment and TLS root certificate.
- One ECert CA admin bootstrap user `rcaadmin` enrollment and MSP root certificate.


### [Launch the Test Network...](TEST_NETWORK.md)
