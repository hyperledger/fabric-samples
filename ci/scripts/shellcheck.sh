#!/bin/bash
set -euo pipefail

scversion="v0.8.0" # or "stable", or "latest"
wget -qO- "https://github.com/koalaman/shellcheck/releases/download/${scversion?}/shellcheck-${scversion?}.linux.x86_64.tar.xz" | tar -xJv "shellcheck-${scversion}/shellcheck"
"./shellcheck-${scversion}/shellcheck" --version

"./shellcheck-${scversion}/shellcheck" ./test-network-nano-bash/*.sh
