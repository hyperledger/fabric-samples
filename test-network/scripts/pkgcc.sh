#!/bin/bash

#
# SPDX-License-Identifier: Apache-2.0
#

function usage() {
    echo "Usage: pkgcc.sh -l <label> -a <address> [-m <META-INF directory>]"
}

function error_exit {
    echo "${1:-"Unknown Error"}" 1>&2
    exit 1
}

while getopts "hl:a:m:" opt; do
    case "$opt" in
        h)
            usage
            exit 0
            ;;
        l)
            label=${OPTARG}
            ;;
        m)
            metainf=${OPTARG}
            ;;
        a)
            address=${OPTARG}
            ;;
        *)
            usage
            exit 1
            ;;
    esac
done
shift $((OPTIND-1))

type=ccaas

if [ -z "$label" ] || [ -z "$address" ]; then
    usage
    exit 1
fi

metadir=$(basename "$metainf")
if [ -n "$metainf" ]; then
    if [ "META-INF" != "$metadir" ]; then
        error_exit "Invalid chaincode META-INF directory $metadir: directory name must be 'META-INF'"
    elif [ ! -d "$metainf" ]; then
        error_exit "Cannot find directory $metadir"
    fi
fi

prefix=$(basename "$0")
tempdir=$(mktemp -d -t "$prefix.XXXXXXXX") || error_exit "Error creating temporary directory"

file=${tempdir}/connection.json

cat > "${file}" <<CONN_EOF
{
  "address": "${address}",
  "dial_timeout": "10s",
  "tls_required": false
}
CONN_EOF

if [ -n "$DEBUG" ]; then
    echo "label = $label"
    echo "type = $type"
    echo "file = $file"
    echo "tempdir = $tempdir"
    echo "metainf = $metainf"
fi


mkdir -p "$tempdir/src"
if [ -d "$file" ]; then
    cp -a "$file/"* "$tempdir/src/"
elif [ -f "$file" ]; then
    cp -a "$file" "$tempdir/src/"
fi

if [ -n "$metainf" ]; then
    cp -a "$metainf" "$tempdir/src/"
fi

mkdir -p "$tempdir/pkg"
cat << METADATA-EOF > "$tempdir/pkg/metadata.json"
{
    "type": "$type",
    "label": "$label"
}
METADATA-EOF

if [ "$type" = "ccaas" ]; then
    tar -C "$tempdir/src" -czf "$tempdir/pkg/code.tar.gz" .
else
    tar -C "$tempdir" -czf "$tempdir/pkg/code.tar.gz" src 
fi

tar -C "$tempdir/pkg" -czf "$label.tgz" metadata.json code.tar.gz

rm -Rf "$tempdir"

packageid="${label}:$(shasum -a 256 audit-trail.tgz | cut -d ' ' -f1)"
echo ${packageid}
