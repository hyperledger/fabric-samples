# Test and Debug Reference

[PREVIOUS - Adding a Transaction Function](./02-Exercise-Adding-tx-function.md)

**Aim:** Stand up a Hyperledger Fabric Smart Contract so it can easily be debugged

**Objectives:**

- Introduce what Chaincode-as-a-Service is, and how it helps
- Show how to build & configure a Chaincode to run like this
- How to deploy these in a running Hyperledger Fabric network
- How then to debug this running Chaincode.

---

## Overview

It helps to think of three 'parts'

- The Fabric network, consisting of the peers, orderers, certificate authorities etc. Along with configured channels and identities.
  For our purposes here, this can be considered as a 'black box'. The 'black box' can be configured a number of different ways, but typically will be one or more docker containers. This workshop uses MicroFab to bring up the Fabric network in a single docker container.
- The Chaincode - this will be running in its own process or docker container.
- The editor - VSCode is covered here, but the approach should hold with other debuggers and editors.

The _high level process_ is

0. Stand-up Fabric
1. Develop the Smart Contract
3. Create a chaincode package using the chaincode-as-a-service approach
4. Install the chaincode to a peer and Approve/Commit the chaincode on a channel
5. Start the chaincode using the chaincode-as-a-service approach
6. Attach your debugger to the running chaincode and set a breakpoint
7. Invoke a transaction, this will then halt in the debugger to let you step over the code
8. Find the bugs and repeat **from step 5** - note that we don't need to Package/Install/Approve/Commit the chaincode again.

This is the exact process that you will have followed in the ['Getting Started'](./01-Exercise-Getting-Started.md) section

### What do you need?

You'll need to have docker available to you, along with VSCode. Also, install the VSCode extensions you prefer for debugging your preferred language. Other debuggers are available and you're free to use those if you have them available.

- For TypeScript and JavaScript VSCode has built-in support
- For Java the [JavaExtension pack](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack) is suggested

### What is Chaincode as Service?

The chaincode-as-a-service feature is a very useful and practical way to run 'Smart Contracts'. Traditionally the Fabric Peer has taken on the role of orchestrating
the complete lifecycle of the chaincode. It required access to the Docker Daemon to create images, and start containers. Java, Node.js and Go chaincode frameworks were
 explicitly known to the peer including how they should be built and started.

As a result, this makes it very hard to deploy into Kubernetes (K8S) style environments or to run in any form of debug mode. Additionally, the code is being rebuilt by
 the peer therefore there is some degree of uncertainty about what dependencies have been pulled in.

Chaincode-as-service requires you to orchestrate the build and deployment phase yourself. Whilst this is an additional step, it gives control back. The Peer still
requires a 'chaincode package' to be installed. In this case, this doesn't contain code, but the information about where the chaincode is hosted. (Hostname, Port, TLS config etc)


## Running the Smart Contracts

An important point is that the code written for the Smart Contract is the same, whether it's managed by the peer or Chaincode-as-a-Service.
What is different is how that is started and packaged. The overall process is the same, regardless of whether your smart contract is written in Java/Typescript/Go.

### TypeScript/JavaScript

Using the Typescript contract as an example, the difference is easier to see. The package.json contains 4 'start' commands

```
   "start": "fabric-chaincode-node start",
   "start:server-nontls": "fabric-chaincode-node server --chaincode-address=$CHAINCODE_SERVER_ADDRESS --chaincode-id=$CHAINCODE_ID",
   "start:server": "fabric-chaincode-node server --chaincode-address=$CHAINCODE_SERVER_ADDRESS --chaincode-id=$CHAINCODE_ID --chaincode-tls-key-file=/hyperledger/privatekey.pem --chaincode-tls-client-cacert-file=/hyperledger/rootcert.pem --chaincode-tls-cert-file=/hyperledger/cert.pem",
   "start:server-debug": "set -x && NODE_OPTIONS='--inspect=0.0.0.0:9229' fabric-chaincode-node server --chaincode-address=$CHAINCODE_SERVER_ADDRESS --chaincode-id=$CHAINCODE_ID"
```

The first is used when the peer is completely controlling the chaincode. The second `start:server-nontls` starts in the Chaincode-as-a-service mode (without using TLS). The command
is very similar `fabric-chaincode-node server` rather than `fabric-chaincode-node start`. Two options are provided here, these are the network address the chaincode
 will listen on and its id. (aside when the Peer runs the chaincode, it does pass extra options, but they aren't seen in the package.json)

The third `start:server` adds the required TLS configuration, but is otherwise the same.
The fourth `start:server-debug` is the same as the non-TLS case, but includes the environment variable required to get Node.js to open a port to allow a debugger to connect remotely.

### Java

The changes for the Java chaincode are logically the same. The build.gradle (or use Maven if you wish) is exactly the same (like there were no changes to the
TypeScript compilation). With the v2.4.1 Java Chaincode libraries, there are no code changes to make or build changes. The '-as-a-service' mode will be used if
 the environment variable `CHAINCODE_SERVER_ADDRESS` is set.

For the non-TLS case the Java chaincode is started with `java -jar /chaincode.jar` - and will use the Chaincode-as-a-service mode _if_ the environment variable `CHAINCODE_SERVER_ADDRESS` is set.

For debug, the JVM needs to put into debug mode `java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:8000 -jar /chaincode.jar`

## How is the chaincode package different?

A key difference is that the chaincode package does not contain code. It is used as a holder of data that indicates to the peer where the chaincode is.
What host/port and what TLS configuration is needed? Chaincode packages already can hold data about the CouchDB indexes to use or the private data collections.

Within the package, the `connection.json` is an important file. At its simplest it would be

```json
{
  "address": "assettransfer_ccaas:9999",
  "dial_timeout": "10s",
  "tls_required": false
}
```

This is telling the peer the chaincode is on host `assettransfer_ccaas` port 9999. 10s timeout on connecting and tls is not needed.

The packager can be constructed by hand, it's a set of json files, collected together with `tgz`.

### Important networking warning

The chaincode package that is installed critically contains the hostname and port that the peer is expecting the chaincode to listen on. If nothing answers the
peer, it obviously will fail the transaction.

Note that it is ok not to have the chaincode running at all times, the peer won't complain until it is asked to actually connect to the chaincode. This is an important
 ability as it allows for debugging and restarting of the container.

The hostname that is supplied must be something that the peer, from its perspective, can resolve. Typically the peer will be inside a docker container, therefore
 supplying `localhost` or `127.0.0.1` will resolve to the same container the peer is running in.

Assuming that the peer is running in a docker container, the chaincode could either be run in its own docker container, on the same docker network as the peers
 container, or it could be run directly on the host system.

Depending your host OS, the 'specialhostname' that is used from within the docker container to access the host varies.
 For example, see this [stackoverflow post](https://stackoverflow.com/questions/24319662/from-inside-of-a-docker-container-how-do-i-connect-to-the-localhost-of-the-mach#:~:text=To%20access%20host%20machine%20from,using%20it%20to%20anything%20else.&text=Then%20make%20sure%20that%20you,0.0%20.)

The advantage of this is the chaincode can run locally on your host machine and is simple to connect to from a debugger.

Alternatively, you can package the chaincode into its own docker container, and run that. You can still debug into this, but need to ensure that the ports of the
container are exposed correctly for your language runtime.

## Single stepping and timeouts

- If you are going to single step in a debugger, then you are likely to hit the Fabric transaction timeout value. By default this is 30 seconds, meaning the chaincode has to complete transactions in 30 seconds or less before the peer timesout the request. In your `config/core.yaml` update `executetimeout` to be `300s`, or add `CORE_CHAINCODE_EXECUTETIMEOUT=300s` to the environment variable options of each peer, so that you can step through your contract code in a debugger for 5 minutes for each invoked transaction function.
