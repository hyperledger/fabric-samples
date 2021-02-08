set -euo pipefail

function print() {
	GREEN='\033[0;32m'
  NC='\033[0m'
  echo
	echo -e "${GREEN}${1}${NC}"
}

dirs=("$(find . -name "*-go" -o -name "*-java" -o -name "*-javascript" -o -name "*-typescript")")
for dir in $dirs; do
  if [[ -d $dir ]]; then
    print "Linting $dir"
    pushd $dir
    if [[ "$dir" =~ "-go" ]]; then
      go get golang.org/x/tools/cmd/goimports
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
    elif [[ "$dir" =~ "-javascript" ]]; then
      print "Running ESLint"
      if [[ "$dir" =~ "chaincode" ]]; then
        eslint *.js */**.js
      else
        eslint *.js
      fi
    elif [[ "$dir" =~ "-java" ]]; then
      if [[ -f "pom.xml" ]]; then
        print "Running Maven Build"
        mvn clean package
      else
        print "Running Gradle Build"
        ./gradlew build
      fi
    elif [[ "$dir" =~ "-typescript" ]]; then
      print "Running TSLint"
      tslint --project .
    fi
    popd
  fi
done
