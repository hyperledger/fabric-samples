# High Availability

The peers have been configured so they implemented a essential failover/high-availability configuration.

Two important notes:

1. The word 'gateway' in the k8s definitions is being used in a generic way. It is not tied to the concept of the 'Fabric Gateway' component. However using the 'Fabric-Gateway' with the updated SDKs, make connecting to Fabric even easier. There is a single connection, that can easily be handled with core k8s abilities. Attempting the approach described below with the older SDKs is not recommended.
2. Long Lived gRPC connections. Remember that the connections between components in Fabric are long-lived gRPC connections. From a client application's perspective that means the connection will be load-balanced when initially connected, but unless the connection breaks, it will not be 're-load-balanced'. It's important to keep this in mind.

## Peer Gateway Services

Each peer has defined their own K8S service, with the selector specifically choosing only one peer pod. 
In this test-network, there are two peers per organization. Using a service with a different selector that
picks both peer pods, allows a degree of load balancing.

```yaml
---
apiVersion: v1
kind: Service
metadata:
  name: org2-peer-gateway-svc
spec:
  ports:
    - name: gossip
      port: 7051
      protocol: TCP
  selector:
    org: org2
```

The selector is `org: org2` that is defined in the specification of the Peer's Deployment. 

```yaml
  template:
    metadata:
      labels:
        app: org2-peer1
        org: org2
```

## Kube Proxy Configuration
The proxy configuration is set to be `ipvs`. This gives a lot more scope for different load balancing algorithms.
"Round Robin" is the default configuration (as used in this test network). For more information check this [deep dive](https://kubernetes.io/blog/2018/07/09/ipvs-based-in-cluster-load-balancing-deep-dive) on the Kubernetes blog.

For this KIND cluster, this is configured by updating the cluster configuration, add the following yaml.

```yaml
networking:
  kubeProxyMode: "ipvs"
```

## Application and TLS Configuration

It is important that applications connect to the `org2-peer-gateway-svc` or `org1-peer-gateway-svc` rather that specific peer services. That way the service can load balance.  However if TLS used, errors will occur as the host name that is connected to is different to that used by the application. 

The solution is to add the additional servicename to the hosts field in the SAN section of the TLS certificate. As an example here is the command that is used to create the TLS certificate for org1-peer1.  Note the 

```bash
fabric-ca-client enroll --url https://org1-peer1:peerpw@org1-ca --csr.hosts org1-peer1,org1-peer-gateway-svc --mspdir /var/hyperledger/fabric/organizations/peerOrganizations/org1.example.com/peers/org1-peer1.org1.example.com/msp
```

## Summary

The FabricGateway and updated SDKs, improve the connection from a client application to Fabric, by needing only a single connection to one peer. By using a K8S service fronting two or more peer pods, a degree of load-balancing can be achieved. Remember that this will only be load balanced when the connection is first created. If a single peer becomes heavily loaded, K8S will not move any existing connection. 

To achieve this you would need to have a monitoring system that can trigger applications to disconnect and reconnect. 

If the connection drops, the application can reconnect and will get to a working peer.



