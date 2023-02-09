#!/usr/bin/env bash
set -euo pipefail
. scripts/utils.sh


# Create the namespace, ignoring an error if it previously was created.
cat << EOF | kubectl apply -f -
apiVersion: v1
kind: Namespace
metadata:
  name: ${NAMESPACE}
EOF

print "Launching ${NAMESPACE} fabric-operator"

# Substitute just/env variables into the kustomization before applying to k8s
kubectl kustomize kind/operator | envsubst | kubectl -n ${NAMESPACE} apply -f -

kubectl -n ${NAMESPACE} rollout status deploy fabric-operator