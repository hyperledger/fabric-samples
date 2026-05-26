#!/usr/bin/env bash

# Set up required storage classes (not present in Fyre or staging IKS/OCP)
#
# Required env vars
# CLUSTER_TYPE (iks | ocp)
set -euo pipefail
cd "$(dirname "$0")"
UTILS_DIR=$(pwd)

echo "Cloning required rook repository"
rm -rf ${UTILS_DIR}/rook
git clone https://github.com/rook/rook.git


cd rook/deploy/examples/

if [ $CLUSTER_TYPE = "iks" ]; then
    echo "Creating required IKS storage classes"
    kubectl create -f crds.yaml
    kubectl create -f common.yaml
    kubectl create -f cluster.yaml
    kubectl create -f ./csi/rbd/storageclass.yaml
    kubectl create -f ./csi/rbd/pvc.yaml
    kubectl create -f filesystem.yaml
    kubectl create -f ./csi/cephfs/storageclass.yaml
    kubectl create -f ./csi/cephfs/pvc.yaml
    kubectl create -f toolbox.yaml
elif [ $CLUSTER_TYPE = "openshift" ]; then
    echo "Creating required OCP storage classes"
    oc create -f crds.yaml
    oc create -f common.yaml
    oc create -f operator-openshift.yaml
    oc create -f cluster.yaml
    oc create -f ./csi/rbd/storageclass.yaml
    oc create -f ./csi/rbd/pvc.yaml
    oc create -f filesystem.yaml
    oc create -f ./csi/cephfs/storageclass.yaml
    oc create -f ./csi/cephfs/pvc.yaml
    oc create -f toolbox.yaml
else
    echo "Unknown or missing CLUSTER_TYPE variable"
    exit 1
fi

