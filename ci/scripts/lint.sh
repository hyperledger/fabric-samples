set -euo pipefail

function print() {
	GREEN='\033[0;32m'
  NC='\033[0m'
  echo
	echo -e "${GREEN}${1}${NC}"
}

if [[ "${LANGUAGE}" == "go" ]]; then
  go get golang.org/x/tools/cmd/goimports

  cd "${DIRECTORY}/${TYPE}-${LANGUAGE}"
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
elif [[ "${LANGUAGE}" == "java" ]]; then
  cd "${DIRECTORY}/${TYPE}-${LANGUAGE}"
  print "Running Gradle Build"
  ./gradlew build
elif [[ "${LANGUAGE}" == "javascript" ]]; then
  npm install -g eslint
  cd "${DIRECTORY}/${TYPE}-${LANGUAGE}"
  print "Running ESLint"
  if [[ "${TYPE}" == "chaincode" ]]; then
    eslint *.js */**.js
  else
    eslint *.js
  fi
elif [[ "${LANGUAGE}" == "typescript" ]]; then
  npm install -g typescript tslint
  cd "${DIRECTORY}/${TYPE}-${LANGUAGE}"
  print "Running TSLint"
  tslint --project .
else
  echo "Language not supported"
  exit 1
fi
