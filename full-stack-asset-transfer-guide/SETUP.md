# Essential Setup

Remember to clone this repository!

```shell
git clone https://github.com/hyperledgendary/full-stack-asset-transfer-guide.git workshop
cd workshop
export WORKSHOP_PATH=$(pwd)
```

> to check the tools you already have  `./check.sh`

## Option 1: Use local environment

Do you want to configure your local environment with the workshop dependencies?

- To develop an application and/or contract (first two parts of workshop) follow the *DEV* setup below

- To deploy a chaincode to kubernetes in a production manner (third part of workshop) follow the *PROD* setup below

## Option 2: Use a Multipass Ubuntu image

If you do not want to install dependencies on your local environment, you can use a Multipass Ubuntu image instead.

Tip - You may need to stop any VPN client for the Multipass networking to work.

- [Install multipass](https://multipass.run/install)

- Launch the virtual machine and automatically install the workshop dependencies:

```shell
multipass launch \
  --name        fabric-dev \
  --disk        80G \
  --cpus        8 \
  --mem         8G \
  --cloud-init  infrastructure/multipass-cloud-config.yaml
```

- Mount the local workshop to your multipass vm:

```shell
multipass mount $PWD fabric-dev:/home/ubuntu/full-stack-asset-transfer-guide
```

- Open a shell on the virtual machine:

```shell
multipass shell fabric-dev
```

Tip - The vm creation log can be seen at /var/log/cloud-init-output.log if you need to troubleshoot anything.

- You are now inside the virtual machine. cd to the workshop directory:

```shell
cd full-stack-asset-transfer-guide
```

- Install Fabric peer CLI and set environment variables
```shell
curl -sSLO https://raw.githubusercontent.com/hyperledger/fabric/main/scripts/install-fabric.sh && chmod +x install-fabric.sh
./install-fabric.sh binary
export WORKSHOP_PATH=$(pwd)
export PATH=${WORKSHOP_PATH}/bin:$PATH
export FABRIC_CFG_PATH=${WORKSHOP_PATH}/config
```

Note - You'll probably want three terminal windows for running the workshop, go ahead and open the shells now:

```shell
multipass shell fabric-dev
```

- Eventual cleanup - To remove the multipass image when you are done with it after the workshop:
```shell
multipass delete fabric-dev
multipass purge
multipass list
```

## DEV - Required Tools

You will need a set of tools to assist with chaincode and application development.
We'll assume you are developing in Node for this workshop, but you could also develop in Java or Go by installing the respective compilers.

- [docker engine](https://docs.docker.com/engine/install/)

- [just](https://github.com/casey/just#installation) to run all the commands here directly

- [nvm](https://github.com/nvm-sh/nvm#installing-and-updating) to install node and npm
```shell
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.1/install.sh | bash
```

- [node v16 and npm](https://github.com/nvm-sh/nvm#usage) to run node chaincode and applications
```shell
nvm install 16
```

- [typescript](https://www.typescriptlang.org/download) to compile typescript chaincode and applications to node
```shell
npm install -g typescript
```

- [weft ](https://www.npmjs.com/package/@hyperledger-labs/weft) Hyperledger-Labs cli to work with identities and chaincode packages
```shell
npm install -g @hyperledger-labs/weft
```

- [jq](https://stedolan.github.io/jq/) jq JSON command-line processor
```shell
sudo apt-get update && sudo apt-get install -y jq
```

- Fabric peer CLI
```shell
curl -sSL https://raw.githubusercontent.com/hyperledger/fabric/main/scripts/install-fabric.sh | bash -s -- binary
export WORKSHOP_PATH=$(pwd)
export PATH=${WORKSHOP_PATH}/bin:$PATH
export FABRIC_CFG_PATH=${WORKSHOP_PATH}/config
```

## PROD - Required Tools for Kubernetes Deployment

- [kubectl](https://kubernetes.io/docs/tasks/tools/)
- [jq](https://stedolan.github.io/jq/)
- [just](https://github.com/casey/just#installation) to run all the comamnds here directly
- [kind](https://kind.sigs.k8s.io/) if you want to create a cluster locally, see below for other options
- [k9s](https://k9scli.io) (recommended, but not essential)

### Beta Ansible Playbooks

The v2.0.0-beta Ansible Collection for Hyperledger Fabric is required for Kubernetes deployment. This isn't yet being published to DockerHub but is being published to Github Packages.

For reference check the latest version of [ofs-ansible](https://github.com/IBM-Blockchain/ansible-collection/pkgs/container/ofs-ansibe)

The Ansible scripts in the workshop are set to use the latest image here by default.
