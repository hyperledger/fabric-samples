#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/../.."

ci/scripts/lint-go.sh
ci/scripts/lint-javascript.sh
ci/scripts/lint-typescript.sh
ci/scripts/lint-java.sh
ci/scripts/lint-shell.sh