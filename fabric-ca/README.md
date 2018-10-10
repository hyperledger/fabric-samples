# Hyperledger Fabric CA sample

The Hyperledger Fabric CA sample demonstrates the following:

* How to use the Hyperledger Fabric CA client and server to generate all crypto
  material rather than using cryptogen.  The cryptogen tool is not intended for
  a production environment because it generates all private keys in one location
  which must then be copied to the appropriate host or container. This sample
  demonstrates how to generate crypto material for orderers, peers,
  administrators, and end users so that private keys never leave the host or
  container in which they are generated.

* How to use Attribute-Based Access Control (ABAC). See
  fabric-samples/chaincode/abac/abac.go and note the use of the *github.com/hyperledger/fabric/core/chaincode/lib/cid* package to extract
  attributes from the invoker's identity.  Only identities with the *abac.init*
  attribute value of *true* can successfully call the *Init* function to
  instantiate the chaincode.

## Running this sample

1. To run this sample, simply run the *start.sh* script.  You may do this
multiple times in a row as needed since the *start.sh* script cleans up before
starting each time.  This sample can be run with the latest released version,
an older released version, or from locally built docker images as follows:

    a. By default, the sample is run with the latest released version of Fabric
    and Fabric CA.

    b. Older versions of Fabric and Fabric CA can be used by setting the
    `FABRIC_TAG` environment variable. For example, `export FABRIC_TAG=1.3.0`
    will run the sample with 1.3.0 version of Fabric and Fabric CA.

    c. The sample can also be run with locally built Fabric and Fabric CA
    docker images. Fabric and Fabric CA repositories must be cloned with following
    commands:

    `git clone https://github.com/hyperledger/fabric.git`
    `git clone https://github.com/hyperledger/fabric-ca.git`

    Then execute the `make docker-all` command from the fabric-ca repository. This will
    build the necessary images based on the local source code. Before executing the
    *start.sh* script, set the `FABRIC_TAG` environment variable to 'local' as follows:
    `export FABRIC_TAG=local`.

2. To stop the containers which are started by the *start.sh* script, you may run the *stop.sh* script.

## Understanding this sample

There are some variables at the top of *fabric-samples/fabric-ca/scripts/env.sh*
script which define the names and topology of this sample.  You may modify these
as described in the comments of the script in order to customize this sample.
By default, there are three organizations. The orderer organization is *org0*,
and two peer organizations are *org1* and *org2*.

The *start.sh* script first builds the *docker-compose.yml* file (by invoking the
*makeDocker.sh* script) and then starts the docker containers.
The *data* directory is a volume mount for all containers.
This volume mount is not be needed in a real scenario, but it is used by this
sample for the following reasons:
  a) so that all containers can write their logs to a common directory
     (i.e. *the *data/logs* directory) to make debugging easier;
  b) to synchronize the sequence in which containers start as described below
     (for example, an intermediate CA in an *ica* container must wait for the
      corresponding root CA in a *rca* container to write its certificate to
      the *data* directory);
  c) to access bootstrap certificates required by clients to connect over TLS.

The containers defined in the *docker-compose.yml* file are started in the
following sequence.

1. The *rca* (root CA) containers start first, one for each organization.
An *rca* container runs the fabric-ca-server for the root CA of an
organization. The root CA certificate is written to the *data* directory
and is used when an intermediate CA must connect to it over TLS.

2. The *ica* (Intermediate CA) containers start next.  An *ica* container
runs the fabric-ca-server for the intermediate CA of an organization.
Each of these containers enrolls with a corresponding root CA.
The intermediate CA certificate is also written to the *data* directory.

3. The *setup* container registers identities with the intermediate CAs,
generates the genesis block, and other artifacts needed to setup the
blockchain network.  This is performed by the
*fabric-samples/fabric-ca/scripts/setup-fabric.sh* script.  Note that the
admin identity is registered with **abac.init=true:ecert**
(see the *registerPeerIdentities* function of this script).  This causes
the admin's enrollment certificate (ECert) to have an attribute named "abac.init"
with a value of "true".  Note further that the chaincode used by this sample
requires this attribute be included in the certificate of the identity that
invokes its Init function.  See the chaincode at *fabric-samples/chaincode/abac/abac.go*).
For more information on Attribute-Based Access Control (ABAC), see
https://github.com/hyperledger/fabric/blob/master/core/chaincode/lib/cid/README.md.

4. The orderer and peer containers are started.  The naming of these containers
is straight-forward as is their log files in the *data/logs* directory.

5. The *run* container is started which runs the actual test case.  It creates
a channel, peers join the channel, chaincode is installed and instantiated,
and the chaincode is queried and invoked.  See the *main* function of the
*fabric-samples/fabric-ca/scripts/run-fabric.sh* script for more details.

<a rel="license" href="http://creativecommons.org/licenses/by/4.0/"><img alt="Creative Commons License" style="border-width:0" src="https://i.creativecommons.org/l/by/4.0/88x31.png" /></a><br />This work is licensed under a <a rel="license" href="http://creativecommons.org/licenses/by/4.0/">Creative Commons Attribution 4.0 International License</a>
