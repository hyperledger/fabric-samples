#!/bin/sh

set -euo pipefail

METADIR=$2
# check if the "type" field is set to "external"
# crude way without jq which is not in the default fabric peer image
TYPE=$(tr -d '\n' < "$METADIR/metadata.json" | awk -F':' '{ for (i = 1; i < NF; i++){ if ($i~/type/) { print $(i+1); break }}}'| cut -d\" -f2)

if [ "$TYPE" = "external" ]; then
    exit 0
fi

exit 1
