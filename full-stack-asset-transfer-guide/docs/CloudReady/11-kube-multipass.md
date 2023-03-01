# Deploy a Kubernetes Cluster

[PREV: Setup](00-setup.md) <==> [NEXT: Deploy a Fabric Network](20-fabric.md)

---

## Ready?

```shell

just check-setup

```

**WINDOWS note**, please create the multipass VM with the command below from an elevated command prompt. Then proceed from logged into this VM with the [KIND instructions](./10-kube.md)

## Provision a Multipass Virtual Machine

```shell

multipass launch \
  --name        fabric-dev \
  --disk        80G \
  --cpus        8 \
  --mem         8G \
  --cloud-init  infrastructure/multipass-cloud-config.yaml

# todo: scp not volume mounts
multipass mount $PWD fabric-dev:/home/ubuntu/full-stack-asset-transfer-guide

export WORKSHOP_IP=$(multipass info fabric-dev --format json | jq -r .info.\"fabric-dev\"."ipv4[0]")
export WORKSHOP_INGRESS_DOMAIN=$(echo $WORKSHOP_IP | tr -s '.' '-').nip.io

echo "Multipass VM created with IP: $WORKSHOP_IP"
echo "WORKSHOP_DOMAIN=$WORKSHOP_INGRESS_DOMAIN"

```


## Start a KIND Cluster

- Open a shell on the virtual machine:
```shell

# todo ssh authorized_keys -> ubuntu@${WORKSHOP_IP} not multipass shell
multipass shell fabric-dev

```

```shell

cd ~/full-stack-asset-transfer-guide

# Bind a docker container registry to the VM's external IP  
export CONTAINER_REGISTRY_ADDRESS=0.0.0.0
export CONTAINER_REGISTRY_PORT=5000

# Expose the Kube API controller on the VM's public interface
export KIND_API_SERVER_ADDRESS=$(hostname -I | cut -d ' ' -f 1)
export KIND_API_SERVER_PORT=8888

```

```shell

# Create a Kubernetes cluster in Docker, configure an Nginx ingress, and docker container registry
just kind

# KIND will set the current kube client context in ~/.kube/config
kubectl cluster-info

# Copy the kube config to the host OS volume share:
# todo: scp not volume share
cp ~/.kube/config ~/full-stack-asset-transfer-guide/config/multipass-kube-config.yaml

```

- Exit the multipass VM

- From the host OS:
```shell

# Connect the local kube client to the k8s API server running on the VM:
cp config/multipass-kube-config.yaml ~/.kube/config

# Display kube client connection to k8s running on the VM:
kubectl cluster-info

# Observe the target Kubernetes workspace:  
k9s -n test-network

```


## Troubleshooting:

- Run KIND on your [local system](10-kueb.md)
- Run KIND on an [EC2 instance](12-kube-ec2-vm.md) at AWS
- ssh to a workshop EC2 instance (see the login information on the back of your Conga Trading Card)


# Take it Further:

- Run k8s directly on your laptop with [KIND](todo.md)  (`export WORKSHOP_DOMAIN=localho.st`)
- Provision an EC2 instance on your AWS account with a [#cloud-config](../../infrastructure/ec2-cloud-config.yaml)
- Connect your kube client to a cloud k8s provider


---
[PREV: Setup](00-setup.md) <==> [NEXT: Deploy a Fabric Network](20-fabric.md)
