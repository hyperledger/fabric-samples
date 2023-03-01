#!/bin/bash

set -v -eou pipefail

# All tests run in the workshop root folder
cd "$(dirname "$0")"/..

export WORKSHOP_PATH="${PWD}"
export PATH="${WORKSHOP_PATH}/bin:${PATH}"
export FABRIC_CFG_PATH="${WORKSHOP_PATH}/config"

"${WORKSHOP_PATH}/check.sh"
