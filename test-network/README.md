## Running the test network

Use the ./network.sh script to stand up a simple Fabric test network, create
channels, and deploy the fabcar chaincode on those channels.

For more information, see `./network.sh -help`
```
Usage:
  network.sh <mode> [-c <channel name>] [-ca <use CAs>] [-t <timeout>] [-d <delay>] [-f <docker-compose-file>] [-s <dbtype>] [-l <language>] [-i <imagetag>] [-v]
    <mode> - Bring up fabric network right away using ./network.sh up
      - 'up' - bring up fabric orderer and peer nodes
      - 'up createChannel' - bring up fabric network with one channel
      - 'createChannel' - create and join a channel after the network is created
      - 'deployCC' - Deploy a chainocode on the channel
      - 'down' - clear the network with docker-compose down
      - 'restart' - restart the network

    Flags:
    -c <channel name> - channel name to use (defaults to "mychannel")
    -ca <use CAs> -  create Certificate Authorities to generate the crypto material
    -r <max retry> - CLI times out after certain number of attempts (defaults to 5)
    -d <delay> - delay duration in seconds (defaults to 3)
    -f <docker-compose-file> - specify which docker-compose file use (defaults to docker-compose-cli.yaml)
    -s <dbtype> - the database backend to use: goleveldb (default) or couchdb
    -l <language> - the programming language of the chaincode to deploy: go (default), javascript, or java
    -v <version>  - chaincode version. Must be a round number, 1, 2, 3, etc
    -i <imagetag> - the tag to be used to launch the network (defaults to "latest")
    -verbose - verbose mode
  network.sh -h (print this message)

Example use

Taking all defaults:
	network.sh up

Using flags:
  network.sh up createChannel -ca -c mychannel -s couchdb -i 1.4.0
  network.sh createChannel -c channelName
  network.sh deployCC -l node
```
