# Notes on deployment with OpenShift

There are minor variations in deploying to each different K8S environment; these notes are for open shift specifically

## Login

Typically for setup the local k8s context you should login to OpenShift onthe command line; this usually uses token based authentication rather than a password.

## Storage Classes

If you don't have any storage classes created by default...

```
CLUSTER_TYPE=ocp ./infrastructure/setup_storage_classes.sh
```

This uses rook to create storage classes
Set the setup_storage_classes

```
WORKSHOP_STORAGE_CLASS=rook-cephfs
```

## Image Registry

Using the built in image registry is possible.  

Follow the instructions at https://docs.openshift.com/container-platform/4.8/registry/accessing-the-registry.html to create a user and permissions that allow for pushing and pulling from the registry

Expose the registry externally with instructions at https://docs.openshift.com/container-platform/4.8/registry/securing-exposing-registry.html

## Image names

Note the name used externally is different from the internal name of the image. The name in the chaincode package MUST be the internal name

As an example for a bare-metal OpenShift cluster these where the internal and external names of the same image

```
WORKSHOP_EXTERNAL_REPO=default-route-openshift-image-registry.apps.report.cp.fyre.ibm.com:443
WORKSHOP_INTERNAL_REPO=image-registry.openshift-image-registry.svc:5000
```