# Channels 

Once the test network peers and orderers have been started, and the network identities have been registered 
and enrolled with the ECert CA, we can construct a channel linking the participants of the test network 
blockchain. 

## TL/DR : 

```
$ export TEST_NETWORK_CHANNEL_NAME="mychannel" 

$ ./network channel create 
Creating channel "mychannel":
✅ - Registering org Admin users ...
✅ - Enrolling org Admin users ...
✅ - Creating channel MSP ...
✅ - Creating channel genesis block ...
✅ - Joining orderers to channel mychannel ...
✅ - Joining org1 peers to channel mychannel ...
✅ - Joining org2 peers to channel mychannel ...
🏁 - Channel is ready.
```

## Process Overview 

In order to construct a Fabric channel, the following steps must be performed: 

1. Admin users must be registered and enrolled with the CAs

2. TLS and enrollment certificates must be aggregated and distributed to all participants in the network.   
   
3. The channel genesis block is constructed from `configtx.yaml`, specifying the location of channel MSP.
   
4. Network orderers are joined to the channel using the channel participation API.

5. Network peers are joined to the channel.


## Aggregating the Channel MSP 
```shell
✅ - Registering org Admin users ...
✅ - Enrolling org Admin users ...
✅ - Creating channel MSP ...
```

One of the responsibilities of a Hyperledger Fabric _Consortium Organizer_ is to distribute the public MSP and 
TLS certificates to organizations participating in a blockchain.  In the Docker composed based test network, or 
systems bootstrapped with the `cryptogen` command, all of the public certificates will be available on a common 
file system or volume share.  In our Kubernetes test network, each organization maintains the cryptographic 
assets on a distinct persistent volume, invisible to other the other participants in the network.

To distribute the TLS and MSP _public_ certificates, the test network emulates the responsibilities of the 
consortium organizer by constructing a [Channel MSP](https://hyperledger-fabric.readthedocs.io/en/latest/membership/membership.html#channel-msps) 
structure, extracting the relevant certificate files into a local folder before constructing the channel 
genesis block.  The `configtx.yaml` specifies the channel root folder as the consortium org's (org0) `MSPDir`
attribute. 

- Org admin users are registered with the `fabric-ca-client`, storing the enrollment MSP structures in the local 
`${PWD}/build/enrollments/${org}` folder.

- Channel MSP certificates are extracted from the ECert CA and cert-manager TLS signing authorities, storing the files
locally in the `${PWD}/channel-msp` folder.


## Create the Channel 
```shell
✅ - Creating channel "mychannel" ...
```

As the _consortium leader_ org0, we create the channel's genesis block and use the orderer admin REST 
services to register the channel genesis block configuration on the ordering nodes: 

```shell
configtxgen -profile TwoOrgsApplicationGenesis -channelID '${CHANNEL_NAME}' -outputBlock genesis_block.pb

osnadmin channel join --orderer-address org0-orderer1-admin.localho.st --channelID '${CHANNEL_NAME}' --config-block genesis_block.pb
osnadmin channel join --orderer-address org0-orderer2-admin.localho.st --channelID '${CHANNEL_NAME}' --config-block genesis_block.pb
osnadmin channel join --orderer-address org0-orderer3-admin.localho.st --channelID '${CHANNEL_NAME}' --config-block genesis_block.pb
```


## Join Peers

```shell
✅ - Joining org1 peers to channel "mychannel" ...
✅ - Joining org2 peers to channel "mychannel" ...
```

After the channel configurations have been registered with the network orderers, we will join the peers to the channel 
by retrieving the genesis block from the orderers and then joining the channel:

```shell
  # Fetch the genesis block from an orderer
  peer channel \
    fetch oldest \
    genesis_block.pb \
    -c '${CHANNEL_NAME}' \
    -o org0-orderer1.localho.st \
    --tls --cafile /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/msp/tlscacerts/org0-tls-ca.pem

  # Join peer1 to the channel.
  CORE_PEER_ADDRESS='${org}'-peer1:7051 \
  peer channel \
    join \
    -b genesis_block.pb \
    -o org0-orderer1.localho.st \
    --tls --cafile /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/msp/tlscacerts/org0-tls-ca.pem

  # Join peer2 to the channel.
  CORE_PEER_ADDRESS='${org}'-peer2:7051 \
  peer channel \
    join \
    -b genesis_block.pb \
    -o org0-orderer1.localho.st \
    --tls --cafile /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/msp/tlscacerts/org0-tls-ca.pem
```


## Set Anchor Peers (Optional)
```shell
$ ./network anchor peer2 
✅ - Updating anchor peers to "peer2" ... 
```

In the test network, the configtx.yaml sets the organization [Anchor Peers](https://hyperledger-fabric.readthedocs.io/en/latest/glossary.html?highlight=anchor#anchor-peer)
to "peer1" in the genesis block.  As such, no additional configuration is necessary for neighboring 
organizations to discover additional peers in the network.

However, the process of setting the anchor peers on a channel requires a more complicated scripting process, so we 
have included in the test network a mechanism to illustrate how anchor peers may be set on a network after a 
channel has been constructed.

Up to this point in the network configuration, the shell scripts orchestrating the remote volumes, peers, and 
admin commands have all been executed by piping a sequence of commands into an existing pod directly 
into the input of a `kubectl` command.  For small command sets this is adequate, but for the more complicated 
process of registering a channel anchor peer, we have elected to use a different approach to launch the peer 
update scripts on the kubernetes cluster.

When updating anchor peers, the `./network` script will: 

1.  Transfer the shell scripts from `/scripts/*.sh` into the remote organization's persistent volume.
2.  Issue a `kubectl exec -c "script-name.sh {args}"` on the org's admin CLI pod.

For non-trivial Fabric administrative tasks, this approach of uploading a script into the cluster and then 
executing in an admin pod works well. 


## Next Steps 

### [Working with Chaincode](CHAINCODE.md)