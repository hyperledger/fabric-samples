# Fabric Gateway HSM Samples

The samples show how to create client applications that invoke transactions with HSM Identities using the
new embedded Gateway in Fabric.

The samples will only run against Fabric v2.4 and higher.

Sample client applications are available to demonstrate the features of the Fabric Gateway and associated SDKs using this network.

> **_NOTE:_**  When you use an HSM, private keys for a Fabric enrollment are stored within a dedicated hardware module, rather than in plain text on a local file system.

## Installations

### C Compilers

In order for the client application to run successfully you must ensure you have C compilers and Python 3 (Note that Python 2 may still work however Python 2 is out of support and could stop working in the future) installed otherwise the node dependency `pkcs11js` will not be built and the application will fail. The failure will have an error such as

```
Error: Cannot find module 'pkcs11js'
```

how to install the required C Compilers and Python will depend on your operating system and version.

### SoftHSM

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

### PKCS#11 enabled fabric-ca-client binary
To be able to register and enroll identities using an HSM you need a PKCS#11 enabled version of `fabric-ca-client`
To install this use the following command

```bash
go install -tags 'pkcs11' github.com/hyperledger/fabric-ca/cmd/fabric-ca-client@latest
```

## Running the sample

The Fabric test network is used to deploy and run this sample. Follow these steps in order:

1. Create the test network and a channel (from the `test-network` folder).
   ```
   ./network.sh up createChannel -ca
   ```

2. Deploy one of the smart contract implementations (from the `test-network` folder).
   ```
   # To deploy the TypeScript chaincode implementation
   ./network.sh deployCC -ccn basic -ccp ../asset-transfer-basic/chaincode-typescript/ -ccl typescript

   # To deploy the Go chaincode implementation
   ./network.sh deployCC -ccn basic -ccp ../asset-transfer-basic/chaincode-go/ -ccl go

   # To deploy the Java chaincode implementation
   ./network.sh deployCC -ccn basic -ccp ../asset-transfer-basic/chaincode-java/ -ccl java
   ```

## Initialize a token to store keys in SoftHSM

If you have not initialized a token previously (or it has been deleted) then you will need to perform this one time operation

```bash
echo directories.tokendir = /tmp > $HOME/softhsm2.conf
export SOFTHSM2_CONF=$HOME/softhsm2.conf
softhsm2-util --init-token --slot 0 --label "ForFabric" --pin 98765432 --so-pin 1234
```

This will create a SoftHSM configuration file called `softhsm2.conf` and will be stored in the home directory. This is
where the sample expects to find a SoftHSM configuration file

The Security Officer PIN, specified with the `--so-pin` flag, can be used to re-initialize the token,
and the user PIN (see below), specified with the `--pin` flag, is used by applications to access the token for
generating and retrieving keys.

## Enroll the HSM User

A user, `HSMUser`, who is HSM managed needs to be registered then enrolled for the sample.

If your PKCS11 library (libsofthsm2.so) is not located in one of the typical Linux system locations checked by this sample's scripts and applications, you will need to explicitly specify the library location using the `PKCS11_LIB` environment variable.

```bash
export PKCS11_LIB='<path to PKCS11 library location>'
```
Register a user `HSMUser` with the CA in Org1 (if not already registered) and then enroll that user which will generate a certificate on the file system for use by the sample. The private key is stored in SoftHSM.

```bash
scripts/generate-hsm-user.sh HSMUser
```

### Go SDK

For HSM support you need to ensure you include the `pkcs11` build tag.

```
cd hardware-security-module/application-go
go run -tags pkcs11 .
```

### Node SDK

```
cd hardware-security-module/application-typescript
npm install
npm start
```
## Cleanup

When you are finished running the samples, the local test-network can be brought down with the following command (from the `test-network` folder):

 ```
./network.sh down
```