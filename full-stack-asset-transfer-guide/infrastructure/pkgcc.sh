#!/bin/sh -l


# From the github action 
# https://github.com/hyperledgendary/package-k8s-chaincode-action/blob/main/pkgk8scc.sh
#
# SPDX-License-Identifier: Apache-2.0
#

usage() {
    echo "Usage: pkgk8scc.sh -l <label> -n <name> -d <digest> [-m <META-INF directory>]"
    echo
    echo "  Creates a k8s chaincode package"
    echo
    echo "    Flags:"
    echo "    -l <label> - chaincode label"
    echo "    -n <name> - docker image name"
    echo "    -d <digest> - docker image digest"
    echo "    -m <META-INF directory> - state database index definitions for CouchDB"
    echo "    -h - Print this message"
}

error_exit() {
    echo "${1:-"Unknown Error"}" 1>&2
    exit 1
}

while getopts "hl:n:d:m:" opt; do
    case "$opt" in
        h)
            usage
            exit 0
            ;;
        l)
            label=${OPTARG}
            ;;
        n)
            name=${OPTARG}
            ;;
        d)
            digest=${OPTARG}
            ;;
        m)
            metainf=${OPTARG}
            ;;
        *)
            usage
            exit 1
            ;;
    esac
done
shift $((OPTIND-1))

if [ -z "$label" ] || [ -z "$name" ] || [ -z "$digest" ]; then
    usage
    exit 1
fi

if [ -n "$metainf" ]; then
    metadir=$(basename "$metainf")
    if [ "META-INF" != "$metadir" ]; then
        error_exit "Invalid chaincode META-INF directory $metainf: directory name must be 'META-INF'"
    elif [ ! -d "$metainf" ]; then
        error_exit "Cannot find directory $metainf"
    fi
fi

prefix=$(basename "$0")
tempdir=$(mktemp -d -t "$prefix.XXXXXXXX") || error_exit "Error creating temporary directory"

if [ -n "$DEBUG" ]; then
    echo "label = $label"
    echo "name = $name"
    echo "digest = $digest"
    echo "metainf = $metainf"
    echo "tempdir = $tempdir"
fi

mkdir -p "$tempdir/src"
cat << IMAGEJSON-EOF > "$tempdir/src/image.json"
{
  "name": "$name",
  "digest": "$digest"
}
IMAGEJSON-EOF

if [ -n "$metainf" ]; then
    cp -a "$metainf" "$tempdir/src/"
fi

mkdir -p "$tempdir/pkg"
cat << METADATAJSON-EOF > "$tempdir/pkg/metadata.json"
{
    "type": "k8s",
    "label": "$label"
}
METADATAJSON-EOF

tar -C "$tempdir/src" -czf "$tempdir/pkg/code.tar.gz" .

tar -C "$tempdir/pkg" -czf "$label.tgz" metadata.json code.tar.gz

rm -Rf "$tempdir"