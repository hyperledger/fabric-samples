# Asset Transfer Basic typescript HSM sample

This sample takes the Asset Transfer basic example and re-works it to focus on how
you would use a Hardware Security Modules within your node client application.

## About the Sample

This typescript sample application is able to use any of the asset transfer basic
chaincode samples. It will show how to register users with a Fabric CA and enroll users which will store keys in an HSM (In this case the sample uses SoftHSM which is an HSM implementation that should be used for development and testing purposes only). It also demonstrates setting up a wallet that will store identities that can then be used to transact on the fabric network which are HSM managed.

## C Compilers

In order for the client application to run successfully you must ensure you have C compilers and Python 3 (Note that Python 2 may still work however Python 2 is out of support and could stop working in the future) installed otherwise the node dependency `pkcs11js` will not be built and the application will fail. The failure will look like

```
Loaded the network configuration located at /home/dave/temp/fabric-samples/test-network/organizations/peerOrganizations/org1.example.com/connection-org1.json
******** FAILED to run the application: Error: Cannot find module 'pkcs11js'
Require stack:
- /home/dave/temp/fabric-samples/asset-transfer-basic/application-typescript-hsm/node_modules/fabric-common/lib/impl/bccsp_pkcs11.js
- /home/dave/temp/fabric-samples/asset-transfer-basic/application-typescript-hsm/node_modules/fabric-common/lib/Utils.js
- /home/dave/temp/fabric-samples/asset-transfer-basic/application-typescript-hsm/node_modules/fabric-common/index.js
- /home/dave/temp/fabric-samples/asset-transfer-basic/application-typescript-hsm/node_modules/fabric-ca-client/lib/FabricCAServices.js
- /home/dave/temp/fabric-samples/asset-transfer-basic/application-typescript-hsm/node_modules/fabric-ca-client/index.js
- /home/dave/temp/fabric-samples/asset-transfer-basic/application-typescript-hsm/dist/app.js
```

how to install the required C Compilers and Python will depend on your operating system and version.

## Configuring and running a Hardware Security Module

This sample sets the hsmOptions for the wallet as follows

```javascript
const softHSMOptions: HsmOptions = {
    lib: await findSoftHSMPKCS11Lib(),
    pin: process.env.PKCS11_PIN || '98765432',
    label: process.env.PKCS11_LABEL || 'ForFabric',
};
```

which is specific to using SoftHSM which has been initialised with a token labelled `ForFabric`
and a user pin of `98765432`, however you can override these values to use your own HSM by either
editting the application or use these environment variables to pass in the values:

* PKCS11_LIB - path to the your specific HSM Library
* PKCS11_PIN - your HSM pin
* PKCS11_LABEL - your HSM label

Alternatively you could install SoftHSM to try out the application as described in the next sections

### Install SoftHSM

In order to run the application in the absence of a real HSM, a software
emulator of the PKCS#11 interface is required.
For more information please refer to [SoftHSM](https://www.opendnssec.org/softhsm/).

SoftHSM can either be installed using the package manager for your host system:

* Ubuntu: `sudo apt install softhsm2`
* macOS: `brew install softhsm`
* Windows: **unsupported**

Or compiled and installed from source:

1. install openssl 1.0.0+ or botan 1.10.0+
2. download the source code from <https://dist.opendnssec.org/source/softhsm-2.5.0.tar.gz>
3. `tar -xvf softhsm-2.5.0.tar.gz`
4. `cd softhsm-2.5.0`
5. `./configure --disable-gost` (would require additional libraries, turn it off unless you need 'gost' algorithm support for the Russian market)
6. `make`
7. `sudo make install`

### Specify the SoftHSM configuration file

A configuration file for SoftHSM is provided in this sample directory. This file
uses /tmp as the location for SoftHSM to store it's data which means (depending on
your operating system configuration) the data could be deleted at some point, for example
when you reboot your system. If this data is lost then you will have to delete the wallet
created. An alternative is to change this file to store SoftHSM data in a permanent location
on your file system.
To use this configuration file you need to export an environment variable to point to it
for example, if you are in the application directory then you can use the following command

```bash
export SOFTHSM2_CONF=$PWD/softhsm2.conf
```

Ensure you have this set when initializing the token as well as running the application

### Initialize a token to store keys in SoftHSM

If you have not initialized a token previously (or it has been deleted) then you will need to perform this one time operation

```bash
softhsm2-util --init-token --slot 0 --label "ForFabric" --pin 98765432 --so-pin 1234
```

The Security Officer PIN, specified with the `--so-pin` flag, can be used to re-initialize the token,
and the user PIN (see below), specified with the `--pin` flag, is used by applications to access the token for
generating and retrieving keys.

## Running the sample

Use the Fabric test network utility (`network.sh`) to start a Fabric network and deploy one
one of the sample chaincodes.

Follow these step in order.
- Start the test network with a Certificate Authority and create a channel, so assuming you are in the ensuring you are in the `application-typescript-hsm` directory

```
cd ../../test-network
./network.sh down
./network.sh up createChannel -c mychannel -ca
```

- Deploy the chaincode (smart contract)

```
# to deploy the javascript version
./network.sh deployCC -ccs 1 -ccv 1 -ccep "OR('Org1MSP.peer','Org2MSP.peer')" -ccl javascript -ccp ./../asset-transfer-basic/chaincode-javascript/ -ccn basic
```

- Run the application

```
cd ../asset-transfer-basic/application-typescript-hsm
npm install
npm run build
node dist/app.js
```

### Expected successful execution

If the sample runs successfully then it will output several messages showing the various actions and finally display the message

```
*** The sample ran successfully ***
```

One the first run of the sample, a CA admin id from Org1 will be enrolled from the CA. The certificate for this admin will be stored in the application wallet but the private key will have been stored in the HSM, it will not be in the application wallet. A User in Org1 will be registered in the the Org1 CA and then enrolled. Again only it's certificate will be stored in the application wallet.
All signing of transactions done by the application (driven by the node sdk) will actually be done by the HSM rather than within the node sdk as the private key will never be directly available outside of the HSM.

This sample can be run multiple times even while the same network remains up. As the certificates are already in the application wallet it will not have to enroll a CA admin or register and enroll a user.

### Possible issues you could encounter running the sample

If you see this error:

```
******** FAILED to run the application: Pkcs11Error: CKR_GENERAL_ERROR
```
Make sure you have exported SOFTHSM2_CONF correctly and it points to a valid SoftHSM configuration file that references
a directory containing a initialised SoftHSM token


If you see this error:

```
2020-08-07T20:23:17.590Z - error: [DiscoveryService]: send[mychannel] - Channel:mychannel received discovery error:access denied
******** FAILED to run the application: Error: DiscoveryService: mychannel error: access denied
```

Then the current certificates in your wallet are not valid for the network you are trying to interact with. This would happen if you had
shutdown the fabric network using `network.sh down` and created a new network because this causes all the root certificates to be recreated
To address this problem, you can delete the `wallet` directory in the `dist` folder (`fabric-samples/asset-transfer-basic/application-typescript-hsm/dist/wallet`) of this sample to create new certificates. Because new keypairs are generated these will be stored in SoftHSM and the existing old ones will not be referenced

If you see this error

```
Failed to register user : Error: fabric-ca request register failed with errors [[ { code: 20, message: 'Authentication failure' } ]]
******** FAILED to run the application: Error: Identity not found in wallet: appUser
```

Then the most likely cause is you have deleted your wallet. You would need to either
- stop and create a new fabric network using the `network.sh down` and then following the above instructions to start a new fabric network (but you should not re-initialize the softhsm token)
- or change the application such that it registers a new user instead of `appUser`. This is because be default a registered user can only be enrolled once (using the userid and it's secret)

If you see this error

```
******** FAILED to run the application: Error: _pkcs11OpenSession[336]: PKCS11 label ForFabric cannot be found in the slot list
```

Then the SoftHSM token directory has been deleted (could be due to the /tmp file being cleaned up if you use the sample softhsm2.conf file provided).
You will either need to
- delete your existing wallet, bring down the network as described in `Clean up` section and recreate the network including re-initializing the softhsm token
- or you could just re-initialise the softhsm token but you will need to change the application so that it registers a new user instead of `appUser`

If you see this error (note the number following `SKI` will not be the same)

```
******** FAILED to run the application: Error: _pkcs11SkiToHandle[572]: no key with SKI 27f3557183cd5f26384ab69968ba74944c94c0e24f681c4fadd6502886891da0 found
```

Then the certificates in your wallet do not have corresponding keys in SoftHSM. You can should bring down the network and delete your current wallet (as described in `Clean up` section) and create the network again


If you see either of these errors when the application ends

```
free(): double free detected in tcache 2
Aborted
```

or

```
node[61480]: ../src/node_http2.cc:521:void node::http2::Http2Session::CheckAllocatedSize(size_t) const: Assertion `(current_nghttp2_memory_) >= (previous_size)' failed.
 1: 0xa1a640 node::Abort() [node]
 2: 0xa1a6be  [node]
 3: 0xa55e2a node::mem::NgLibMemoryManager<node::http2::Http2Session, nghttp2_mem>::ReallocImpl(void*, unsigned long, void*) [node]
 4: 0xa55ed3 node::mem::NgLibMemoryManager<node::http2::Http2Session, nghttp2_mem>::FreeImpl(void*, void*) [node]
 5: 0x18b0388 nghttp2_session_close_stream [node]
 6: 0x18b76ea nghttp2_session_mem_recv [node]
 7: 0xa54937 node::http2::Http2Session::ConsumeHTTP2Data() [node]
 8: 0xa54d5e node::http2::Http2Session::OnStreamRead(long, uv_buf_t const&) [node]
 9: 0xb6a651 node::TLSWrap::ClearOut() [node]
10: 0xb6bcdb node::TLSWrap::OnStreamRead(long, uv_buf_t const&) [node]
11: 0xaf54b1  [node]
12: 0x137fed9  [node]
13: 0x1380500  [node]
14: 0x1386de5  [node]
15: 0x137458f uv_run [node]
16: 0xa5d7a6 node::NodeMainInstance::Run() [node]
17: 0x9eab6c node::Start(int, char**) [node]
18: 0x7fd7612180b3 __libc_start_main [/lib/x86_64-linux-gnu/libc.so.6]
19: 0x981fe5  [node]
Aborted (core dumped)
```

then this is due to a bug in `node`. May sure you are using the latest supported version of node, however at the time of writing (node 14.17.1 & node 12.22.1) a fix had not been released by node.js

### Enabling Node SDK logging

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

```bash
cd ../../test-network
./network.sh down
```
Be sure to delete the wallet directory create by the application.
The stored identities will no longer be valid when restarting the network.
For example if you are in the application-typescript-hsm directory

```bash
rm -fr dist/wallet
```

## Suggestions
When typescript node applications log problems or terminate with a stack trace you normally would have to look at the compiled .js code to find the code that was executed in the stack trace. It would be easier if you could find the corresponding lines in the typescript source file. One solution to this problem can be found here https://github.com/evanw/node-source-map-support which will convert stack trace output to corresponding typescript file lines using the generated source maps.