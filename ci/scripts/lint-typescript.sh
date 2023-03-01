#!/bin/bash
set -euo pipefail

function print() {
	GREEN='\033[0;32m'
  NC='\033[0m'
  echo
	echo -e "${GREEN}${1}${NC}"
}

dirs=("$(find . -name "*-typescript" -type d -not -path '*/.*')")
for dir in $dirs; do
  print "Linting $dir"
  pushd $dir

  print "Installing node modules"
  npm install
  print "Running Lint"
  npm run lint

  popd
done