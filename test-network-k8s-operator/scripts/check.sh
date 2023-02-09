#!/usr/bin/env bash

SUCCESS="✅"
WARN="⚠️ "
EXIT=0

if ! command -v docker &> /tmp/cmdpath
then
    echo "${WARN} Please install Docker; suggested install commands:"
    EXIT=1
else
    echo -e "${SUCCESS} Docker found:\t$(cat /tmp/cmdpath)"
fi

KUBECTL_VERSION=v1.24.4       # $(curl -L -s https://dl.k8s.io/release/stable.txt)
if ! command -v kubectl &> /tmp/cmdpath
then
  echo "${WARN} Please install kubectl if you want to use k8s; suggested install commands:"

  if [ $(uname -s) = Darwin ]; then
    if [ $(uname -m) = arm64 ]; then
      echo "curl -LO https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/darwin/arm64/kubectl"
      echo "chmod +x ./kubectl"
      echo "sudo mv ./kubectl /usr/local/bin/kubectl"
      echo "sudo chown root: /usr/local/bin/kubectl"
    else
      echo "curl -LO https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/darwin/amd64/kubectl"
      echo "chmod +x ./kubectl"
      echo "sudo mv ./kubectl /usr/local/bin/kubectl"
      echo "sudo chown root: /usr/local/bin/kubectl"
    fi
  else
    echo "curl -LO https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl"
    echo "sudo install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl"
  fi
  EXIT=1
else
  echo -e "${SUCCESS} kubectl found:\t$(cat /tmp/cmdpath)"

  KUBECTL_CLIENT_VERSION=$(kubectl version --client --output=yaml | grep gitVersion | cut -c 15-)
  KUBECTL_CLIENT_MINOR_VERSION=$(kubectl version --client --output=yaml | grep minor | cut -c 11-12)
  if [ "${KUBECTL_CLIENT_MINOR_VERSION}" -lt "24" ]; then
    echo -e "${WARN} Found kubectl client version ${KUBECTL_CLIENT_VERSION}, which may be out of date.  Please ensure client version >= ${KUBECTL_VERSION}"
    EXIT=1
  fi
fi

# Install kind
KIND_VERSION=0.14.0
if ! command -v kind &> /tmp/cmdpath
then
  echo "${WARN} Please install kind; suggested install commands:"
  echo
  if [ $(uname -s) = Darwin ]; then
    if [ $(uname -m) = arm64 ]; then
      echo "sudo curl --fail --silent --show-error -L https://kind.sigs.k8s.io/dl/v${KIND_VERSION}/kind-darwin-arm64 -o /usr/local/bin/kind"
    else
      echo "sudo curl --fail --silent --show-error -L https://kind.sigs.k8s.io/dl/v${KIND_VERSION}/kind-darwin-amd64 -o /usr/local/bin/kind"
    fi
  else
    echo "sudo curl --fail --silent --show-error -L https://kind.sigs.k8s.io/dl/v${KIND_VERSION}/kind-linux-amd64 -o /usr/local/bin/kind"
  fi
  echo "sudo chmod 755 /usr/local/bin/kind"
  echo
  EXIT=1
else
    echo -e "${SUCCESS} kind found:\t\t$(cat /tmp/cmdpath)"
fi

# Install just
JUST_VERSION=1.2.0
if ! command -v just &> /tmp/cmdpath
then
  echo "${WARN} Please install just; suggested install commands:"
  echo "curl --proto '=https' --tlsv1.2 -sSf https://just.systems/install.sh | bash -s -- --tag ${JUST_VERSION} --to /usr/local/bin"
  EXIT=1
else
  echo -e "${SUCCESS} Just found:\t\t$(cat /tmp/cmdpath)"
fi

# Install jq
if ! command -v jq &> /tmp/cmdpath
then
  echo "${WARN} Please install jq; suggested install commands:"
  echo "sudo apt-update && sudo apt-install -y jq"
  EXIT=1
else
  echo -e "${SUCCESS} jq found:\t\t$(cat /tmp/cmdpath)"
fi

FABRIC_VERSION=2.5.0-beta
FABRIC_CA_VERSION=1.5.6-beta3
if ! command -v peer &> /tmp/cmdpath
then
  echo "${WARN} Please install the Fabric CLI binaries; suggested install commands:"
  echo "curl -sSL https://raw.githubusercontent.com/hyperledger/fabric/main/scripts/install-fabric.sh | bash -s -- binary --fabric-version $FABRIC_VERSION --ca-version $FABRIC_CA_VERSION"
  echo 'export PATH=${PWD}/bin:$PATH'
  #echo 'export FABRIC_CFG_PATH=${PWD}/config'
  EXIT=1
else
  echo -e "${SUCCESS} peer found:\t\t$(cat /tmp/cmdpath)"

  # double-check that the peer binary is compiled for the correct arch.  This can occur when installing fabric
  # binaries into a multipass VM, then running the Linux binaries from a Mac or windows Host OS via the volume share.
  peer version &> /dev/null
  rc=$?
  if [ $rc -ne 0 ]; then
    echo -e "${WARN}  Could not execute peer.  Was it compiled for the correct architecture?"
    peer version
  fi
fi

# tests if varname is defined in the env AND it's an existing directory
function must_declare() {
  local varname=$1

  if [[ ! -d ${!varname} ]]; then
    echo "${WARN} ${varname} must be set to a directory"
    EXIT=1

  else
    echo -e "${SUCCESS} ${varname}:\t${!varname}"
  fi
}

#must_declare "FABRIC_CFG_PATH"
#must_declare "WORKSHOP_PATH"

rm /tmp/cmdpath &> /dev/null

exit $EXIT
