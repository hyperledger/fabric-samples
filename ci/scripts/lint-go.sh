#!/usr/bin/env bash
set -euo pipefail

function print() {
	GREEN='\033[0;32m'
  NC='\033[0m'
  echo
	echo -e "${GREEN}${1}${NC}"
}

dirs=("$(find . -type d \( -name vendor -o -name ".git" \) -prune -o -name "*-go" -type d -print)")
for dir in $dirs; do
  print "Linting $dir"
  pushd $dir

  golangci-lint run

  popd
done