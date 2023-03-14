# Hardware Security Module Samples

The samples show how to create client applications that invoke transactions using identity credentials stored in a Hardware Security Module (HSM). When using an HSM, private keys for a Fabric enrollment are stored within a dedicated hardware module. The private keys are not accessible outside of the HSM, and messages are sent to the HSM to be signed.

The samples use the Fabric Gateway client API and will only run against Fabric v2.4 and higher.

## Install prerequisites

### C compilers

In order for the client application to run successfully you must ensure you have C compilers and Python 3 (Note that Python 2 may still work however Python 2 is out of support and could stop working in the future) installed otherwise the node dependency `pkcs11js` will not be built and the application will fail. The failure will have an error such as

```
Error: Cannot find module 'pkcs11js'
```

how to install the required C Compilers and Python will depend on your operating system and version.

### SoftHSM

In order to run the application in the absence of a real HSM, a software emulator of the PKCS#11 interface ([SoftHSM v2](https://www.opendnssec.org/softhsm/)) is required. This can either be:

- installed using the package manager for your host system:
  - Ubuntu: `sudo apt install softhsm2`
  - macOS: `brew install softhsm`
  - Windows: **unsupported**
- or compiled and installed from source, following the [SoftHSM2 install instructions](https://wiki.opendnssec.org/display/SoftHSMDOCS/SoftHSM+Documentation+v2)
  - It is recommended to use the `--disable-gost` option unless you need **gost** algorithm support for the Russian market, since it requires additional libraries.
  
### PKCS#11 enabled fabric-ca-client binary
To be able to register and enroll identities using an HSM you need a PKCS#11 enabled version of `fabric-ca-client`
To install this use the following command

```bash
go install -tags 'pkcs11' github.com/hyperledger/fabric-ca/cmd/fabric-ca-client@latest
```

## Create Fabric network and deploy the smart contract

The Fabric test network is used to deploy and run this sample. Follow these steps in order:

1. Create the test network and a channel (from the `test-network` folder).
   ```bash
   ./network.sh up createChannel -ca
   ```

2. Deploy one of the smart contract implementations (from the `test-network` folder).
   ```bash
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
mkdir -p "${TMPDIR:-/tmp}/softhsm"
echo "directories.tokendir = ${TMPDIR:-/tmp}/softhsm" > "${HOME}/softhsm2.conf"
SOFTHSM2_CONF="${HOME}/softhsm2.conf" softhsm2-util --init-token --slot 0 --label "ForFabric" --pin 98765432 --so-pin 1234
```

This will create a SoftHSM configuration file called `softhsm2.conf` and will be stored in the home directory. This is
where the sample expects to find a SoftHSM configuration file

The Security Officer PIN, specified with the `--so-pin` flag, can be used to re-initialize the token,
and the user PIN (see below), specified with the `--pin` flag, is used by applications to access the token for
generating and retrieving keys.

## Enroll the HSM User

A user, `HSMUser`, who is HSM managed needs to be registered then enrolled for the sample.

If your PKCS11 library (libsofthsm2.so) is not located in one of the typical system locations checked by this sample's scripts and applications, you will need to explicitly specify the library location using the `PKCS11_LIB` environment variable.

```bash
export PKCS11_LIB='<path to PKCS11 library location>'
```
Register a user `HSMUser` with the CA in Org1 (if not already registered) and then enroll that user which will generate a certificate on the file system for use by the sample. The private key is stored in SoftHSM.

From the `hardware-security-module` folder, run the command:

```bash
SOFTHSM2_CONF="${HOME}/softhsm2.conf" ./scripts/generate-hsm-user.sh HSMUser
```

## Run the sample application

### Go

For HSM support you need to ensure you include the `pkcs11` build tag. From the `hardware-security-module/application-go` folder, run the command:

```
SOFTHSM2_CONF="${HOME}/softhsm2.conf" go run -tags pkcs11 .
```

### Node

From the `hardware-security-module/application-typescript` folder, run the commands:

```
npm install
SOFTHSM2_CONF="${HOME}/softhsm2.conf" npm start
```

## Cleanup

When you are finished running the samples, the local test-network can be brought down with the following command (from the `test-network` folder):

 ```
./network.sh down
```

Created public credentials can be removed from the filesystem by deleting the `hardware-security-module/crypto-material` folder.

SoftHSM tokens and private credentials stored within them can be removed by deleting the `${TMPDIR:-/tmp}/softhsm` folder.
