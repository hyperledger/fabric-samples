#!/usr/bin/env bash
set -euo pipefail

shellcheck --version

cd ./test-network-nano-bash && shellcheck *.sh
