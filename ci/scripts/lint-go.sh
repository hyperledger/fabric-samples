#!/bin/bash
set -euo pipefail

function print() {
	GREEN='\033[0;32m'
  NC='\033[0m'
  echo
	echo -e "${GREEN}${1}${NC}"
}

dirs=("$(find . -name "*-go" -type d -not -path '*/.*')")
for dir in $dirs; do
  print "Linting $dir"
  pushd $dir

  print "Running go vet"
  go vet -tags pkcs11 ./...

  print "Running gofmt"
  output=$(gofmt -l -s $(go list -tags pkcs11 -f '{{.Dir}}' ./...))
  if [[ "${output}" != "" ]]; then
    print "The following files contain formatting errors, please run 'gofmt -l -w <path>' to fix these issues:"
    echo "${output}"
  fi

  print "Running goimports"
  output=$(goimports -l $(go list -tags pkcs11  -f '{{.Dir}}' ./...))
  if [[ "${output}" != "" ]]; then
    print "The following files contain import errors, please run 'goimports -l -w <path>' to fix these issues:"
    echo "${output}"
  fi

  popd
done