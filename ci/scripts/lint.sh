#!/bin/bash
set -euo pipefail

function print() {
	GREEN='\033[0;32m'
  NC='\033[0m'
  echo
	echo -e "${GREEN}${1}${NC}"
}

go install golang.org/x/tools/cmd/goimports@latest

dirs=("$(find . -name "*-go" -o -name "*-java" -o -name "*-javascript" -o -name "*-typescript"  -not -path '*/.*')")
for dir in $dirs; do
  if [[ -d $dir ]] && [[ ! $dir =~ node_modules  ]]; then
    print "Linting $dir"
    pushd $dir
    if [[ "$dir" =~ "-go" ]]; then
      print "Running go vet"
      go vet ./...
      print "Running gofmt"
      output=$(gofmt -l -s $(go list -f '{{.Dir}}' ./...))
      if [[ "${output}" != "" ]]; then
        print "The following files contain formatting errors, please run 'gofmt -l -w <path>' to fix these issues:"
        echo "${output}"
      fi

      print "Running goimports"
      output=$(goimports -l $(go list -f '{{.Dir}}' ./...))
      if [[ "${output}" != "" ]]; then
        print "The following files contain import errors, please run 'goimports -l -w <path>' to fix these issues:"
        echo "${output}"
      fi
    elif [[ "$dir" =~ "-javascript" || "$dir" =~ "-typescript" ]]; then
      print "Installing node modules"
      npm install
      print "Running Lint"
      npm run lint
    elif [[ "$dir" =~ "-java" ]]; then
      if [[ -f "pom.xml" ]]; then
        print "Running Maven Build"
        mvn clean package
      else
        print "Running Gradle Build"
        ./gradlew build
      fi
    fi
    popd
  fi
done
