## Running the test network

Use the `./network.sh` script to stand up a simple Fabric test network. The
network has two peer peer organizations with one peer each and a single node
raft ordering service. You can also use the script to create channels, and deploy
the fabcar chaincode on those channels. The test network is being introduced in
Fabric v2.0 as the long term replacement for the `first-network` sample.

Before you can deploy the test network, you need follow the instructions to
[Install the Samples, Binaries and Docker Images](https://hyperledger-fabric.readthedocs.io/en/latest/install.html) in the Hyperledger Fabric documentation. You may experience problems if you run the
sample using a local build.

For more information, see `./network.sh -help`
```
Usage:
  network.sh <Mode> [Flags]
    <Mode>
      - 'up' - bring up fabric orderer and peer nodes. No channel is created
      - 'up createChannel' - bring up fabric network with one channel
      - 'createChannel' - create and join a channel after the network is created
      - 'deployCC' - deploy the fabcar chaincode on the channel
      - 'down' - clear the network with docker-compose down
      - 'restart' - restart the network

    Flags:
    -ca <use CAs> -  create Certificate Authorities to generate the crypto material
    -c <channel name> - channel name to use (defaults to "mychannel")
    -s <dbtype> - the database backend to use: goleveldb (default) or couchdb
    -r <max retry> - CLI times out after certain number of attempts (defaults to 5)
    -d <delay> - delay duration in seconds (defaults to 3)
    -l <language> - the programming language of the chaincode to deploy: go (default), javascript, or java
    -v <version>  - chaincode version. Must be a round number, 1, 2, 3, etc
    -i <imagetag> - the tag to be used to launch the network (defaults to "latest")
    -verbose - verbose mode
  network.sh -h (print this message)

 Possible Mode and flags
  network.sh up -ca -c -r -d -s -i -verbose
  network.sh up createChannel -ca -c -r -d -s -i -verbose
  network.sh createChannel -c -r -d -verbose
  network.sh deployCC -l -v -r -d -verbose

 Taking all defaults:
	network.sh up

 Examples:
  network.sh up createChannel -ca -c mychannel -s couchdb -i 1.4.0
  network.sh createChannel -c channelName
  network.sh deployCC -l node
```
