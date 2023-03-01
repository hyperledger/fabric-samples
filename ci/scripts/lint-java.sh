#!/bin/bash
set -euo pipefail

function print() {
	GREEN='\033[0;32m'
  NC='\033[0m'
  echo
	echo -e "${GREEN}${1}${NC}"
}

# remove the java asset-private-data until the publishing issues have been resolved
dirs=("$(find . -name "*-java" -type d -not -path '*/.*')")
for dir in $dirs; do
  print "Linting $dir"
  pushd $dir

  if [[ -f "pom.xml" ]]; then
    print "Running Maven Build"
    mvn clean package
  else
    print "Running Gradle Build"
    ./gradlew build
  fi
  popd
done