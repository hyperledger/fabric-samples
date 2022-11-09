# Dploying Chaincode with Ansible

[PREV: Deploy a Fabric Network](22-fabric-ansible-collection.md) <==> [NEXT: Go Bananas](40-bananas.md)

---


## Ready?

```shell

just check-network

```
## Build the Chaincode Docker Image

We need to build the chaincode image, and push it to the local image registry. Here this uses docker to build the image

```shell
# Build the chaincode image
docker build -t localho.st:5000/asset-transfer contracts/asset-transfer-typescript

# Push the image to the insecure container registry
docker push localho.st:5000/asset-transfer

```

## Prepare a k8s Chaincode Package

A chaincode package is requried to inform the peer of which docker image should be used. (strictly it is the K8s-builder that knows which image to use; the peer uses the k8s-builder to do the work creating the container. therefore the peer that Ansible has created is specifically configured to work in k8s)

```shell
IMAGE_DIGEST=$(docker inspect --format='{{index .RepoDigests 0}}' localho.st:5000/asset-transfer | cut -d'@' -f2)
infrastructure/pkgcc.sh -l asset-transfer -n localhost:5000/asset-transfer -d $IMAGE_DIGEST
```

Check the tgz package created 

```shell
ls -l asset-transfer.tgz
-rw-r--r-- 1 matthew matthew 483 Sep  5 11:05 asset-transfer.tgz
```
You can see the file is quite small. If you like you can unpack this with tar

```shell
tar -zxf asset-transfer.tgz && tar -zxf code.tar.gz
```

Copy the `asset-transfer.tgz` to the `_cfg` directory.

```shell
cp asset-transfer.tgz ${WORKSPACE_PATH}/_cfg
```

## Deploy the Chaincode

Firstly we need to create a Ansible variables files that will give the Ansible modules the information on what we want to deploy.

An example file has been provided, check the contents to see what is needed.
```shell

cat contracts/asset-transfer-typescript/asset-transfer-chaincode-vars.yml

smart_contract_name: "asset-transfer"
smart_contract_version: "1.0.0"
smart_contract_sequence: 1
smart_contract_package: "asset-transfer.tgz"
# smart_contract_constructor: "initLedger"
smart_contract_endorsement_policy: ""
smart_contract_collections_file: ""
```

There are three (small) playbooks that need to run to deploy the chaincode. 

- Install and Approve the chaincode on the peers. One each for [org1](../../infrastructure/production_chaincode_playbooks/19-install-and-approve-chaincode.yml) and [org2](../../infrastructure/production_chaincode_playbooks/20-install-and-approve-chaincode.yml)
- Commit the chaincode definition to the channel needs [one playbook](../../infrastructure/production_chaincode_playbooks/21-commit-chaincode.yml)

Run all of these playbooks now; the example configuration file above will be used

```shell
just ansible-deploy-chaincode
```

This will have created the chaincode containers.

## Create a user to access the chaincode

We can run a simple client application here to check the chaincode is deployed and accessible to each. There are two Ansible tasks that are helpful here. 

- `registered_identity` will register an identity to use for the application
- `enrolled_identity` will enroll an already registered identity, returning the certifcate and private keys needed
- `connection_profile` that will put together all the information needed for connecting an application

```shell
just ansible-ready-application
```

This will create two files in `_cfg`; first look at the identity (we've shortened the certificates here)

```shell
 cat _cfg/asset-transfer_appid.json
{
    "name": "asset-transfer.admin",
    "cert": "LS0..............",
    "ca": "LS0tLS................",
    "hsm": false,
    "private_key": "LS0tLS1C..........."
}

```

Now look at the `Org1_gateway.json` file; use jq to just look at the address needed for the gateway to connect to

```shell
cat _cfg/Org1_gateway.json | jq .peers
{
  "Org1 Peer": {
    "url": "grpcs://fabricinfra-org1peer-peer.localho.st:443",
    "tlsCACerts": {
      "pem": "-----BEGIN CERTIFICATE-----...\n-----END CERTIFICATE-----\n"
    }
  }
}
```

## Setup the application

We can use this information directly in an application; we will use the Gateway SDK to create a simple client that will invoke the ib-built Smart Contract function to return it's metadata. This is available in all smart contracts, so it is a useful and simple way to check everything is deployed. The application is called 'ping-chaincode' for this reason

- Change to the `applications/ping-chaincode` directory
- Check the `app.env` file, it should look similar to this

```
CHANNEL_NAME=mychannel
CHAINCODE_NAME=asset-transfer
CONN_PROFILE_FILE=/home/matthew/github.com/hyperledgendary/full-stack-asset-transfer-guide/_cfg/Org1_gateway.json
ID_FILE=asset-transfer_appid.json
ID_DIR=/home/matthew/github.com/hyperledgendary/full-stack-asset-transfer-guide/_cfg/
TLS_ENABLED=true
```

- Build the application (it's typescript)

```shell
npm install
npm run build
```

- Run the application, and look at the output.

```shell
npm start


> asset-transfer-basic@1.0.0 start
> node dist/app.js

Created GRPC Connection
Loaded Identity

--> Evaluate Transaction: Get Contract Metdata from :  org.hyperledger.fabric:GetMetadata
*** Result:
$schema: >-
  https://hyperledger.github.io/fabric-chaincode-node/main/api/contract-schema.json
contracts:
  AssetTransferContract:
    name: AssetTransferContract
    contractInstance:
      name: AssetTransferContract
      default: true
    transactions:
      - tag:
          - SUBMIT
          - submitTx
        parameters:
          - name: assetJson
            description: ''
            schema:
              type: string
        name: CreateAsset
....
```