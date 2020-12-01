# Asset Transfer Basic javascript Sample

The asset transfer basic sample demonstrates submitting transactions
and queries.


## About the Sample

This javascript sample application is able to use any of the asset transfer basic
chaincode samples. It will show how to enroll an admin identity. The admin will then
register and enroll a user identity to work with the Fabric network.
The application will demostrate the basic operations of create, update, delete,
and query on an asset.


## Running the sample

Use the Fabric test network utility (`network.sh`) to start a Fabric network and deploy one
one of the sample chaincodes.

Follow these step in order.
- Start the test network with a Certificate Authority and create a channel
```
cd test-network
./network.sh up createChannel -c mychannel -ca
```

- Deploy the chaincode (smart contract)
```
# to deploy the javascript version
./network.sh deployCC -ccs 1 -ccv 1 -ccep "OR('Org1MSP.peer','Org2MSP.peer')" -ccl javascript -ccp ./../asset-transfer-basic/chaincode-javascript/ -ccn basic
```

- Run the application
```
cd asset-transfer-basic/application-javascript
npm install
node app.js
```

If you see an error:
```
    2020-08-07T20:23:17.590Z - error: [DiscoveryService]: send[mychannel] - Channel:mychannel received discovery error:access denied
    ******** FAILED to run the application: Error: DiscoveryService: mychannel error: access denied
```
   or
```
   Failed to register user : Error: fabric-ca request register failed with errors [[ { code: 20, message: 'Authentication failure' } ]]
   ******** FAILED to run the application: Error: Identity not found in wallet: appUser
```
Delete the `/fabric-samples/asset-transfer-basic/application-javascript/wallet` directory
and retry this application.

The certificate authority must have been restarted and the saved certificates for the
admin and application user are not valid. Deleting the wallet store will force these to be reset
with the new certificate authority.

To see the SDK workings, try setting the logging to show on the console before running
```
export HFC_LOGGING='{"debug":"console"}'
```
or log to a file
```
export HFC_LOGGING='{"debug":"./debug.log"}'
```

## Clean up
When you are finished, you can bring down the test network.
The command will remove all the nodes of the test network, and delete any
ledger data that you created:

```
./network.sh down
```
Be sure to delete the wallet directory create by the application.
The stored identities will no longer be valid when restarting the network.

## Configuring and running a Hardware Security Module

The javascript sample application for asset transfer basic includes
support for a HSM. Below are the steps required to run a Hardware Security Module (HSM)
simulator locally.

**_Note:_**
	When using the HSM simulator you will only be able to run the application once
	without restarting the Fabric network and removing the wallet store.
	Rerunning the application will restart the HSM simulator. The user's identity
	information that has been saved to the wallet store will not exist in the
	restarted HSM simulator.

### Install SoftHSM

In order to run the javascript application in the absence of a real HSM, a software
emulator of the PKCS#11 interface is required.
For more information please refer to [SoftHSM](https://www.opendnssec.org/softhsm/).

SoftHSM can either be installed using the package manager for your host system:

* Ubuntu: `apt-get install softhsm2`
* macOS: `brew install softhsm`
* Windows: **unsupported**

Or compiled and installed from source:

1. install openssl 1.0.0+ or botan 1.10.0+
2. download the source code from <https://dist.opendnssec.org/source/softhsm-2.2.0.tar.gz>
3. `tar -xvf softhsm-2.2.0.tar.gz`
4. `cd softhsm-2.2.0`
5. `./configure --disable-gost` (would require additional libraries, turn it off unless you need 'gost' algorithm support for the Russian market)
6. `make`
7. `sudo make install`

### Specify the SoftHSM configuration file

The configuration file required for the simulator has been created and is
located in the test network directory. Set the following environment
variable to point to a config file before running the javascript application.
When the javascript application sees the following environment variable, it will
attempt to connect to the HSM simulator.

```bash
export SOFTHSM2_CONF="../test-network/hsm/softhsm2.conf"
```

### Create a token to store keys in the HSM

```bash
softhsm2-util --init-token --slot 0 --label "ForFabric" --pin 98765432 --so-pin 1234
```

The Security Officer PIN, specified with the `--so-pin` flag, can be used to re-initialize the token,
and the user PIN (see below), specified with the `--pin` flag, is used by applications to access the token for
generating and retrieving keys.

### Configuration

By default the javascript sample application will run with SoftHSM using slot `0` and user PIN `98765432`.
If your configuration is different, edit the application or use these environment variables to pass in the values:

* PKCS11_LIB - path to the SoftHSM2 library; if not specified, the application will search a list of common install locations
* PKCS11_PIN
* PKCS11_SLOT