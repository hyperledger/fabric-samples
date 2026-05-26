# Working with Applications

## TL/DR: 

```shell
$ ./network rest-easy 
Launching fabric-rest-sample application:
✅ - Ensuring fabric-rest-sample image ...
✅ - Constructing fabric-rest-sample connection profiles ...
✅ - Starting fabric-rest-sample ...

The fabric-rest-sample has started. See https://github.com/hyperledger/fabric-samples/tree/main/asset-transfer-basic/rest-api-typescript for additional usage.
To access the endpoint:

export SAMPLE_APIKEY=97834158-3224-4CE7-95F9-A148C886653E
curl -s --header "X-Api-Key: ${SAMPLE_APIKEY}" http://localhost/api/assets

🏁 - Fabric REST sample is ready.
```

```shell
$ export SAMPLE_APIKEY=97834158-3224-4CE7-95F9-A148C886653E

$ ./network chaincode invoke asset-transfer-basic '{"Args":["CreateAsset","1","blue","35","tom","1000"]}' 

$ curl -s --header "X-Api-Key: ${SAMPLE_APIKEY}" http://fabric-rest-sample.localho.st/api/assets | jq 
[
  {
    "Key": "1",
    "Record": {
      "ID": "1",
      "color": "blue",
      "size": 35,
      "owner": "tom",
      "appraisedValue": 1000
    }
  }
]

$ open https://github.com/hyperledger/fabric-samples/tree/main/asset-transfer-basic/rest-api-typescript 
```

## Guide for Gateway Client Applications 

TODO: this section is a work-in-progress.  

### EXTERNAL Gateway Client (localhost)

For certain development scenarios, it is advantageous to run a Gateway Client externally, using a bridge 
or port forward to access services running behind the veil of Kubernetes networking.  For instance, during active 
development we can run a Gateway Client under the microscope of an IDE / debugger, on a local system, connected
to a remote network as if it were running resident within the Kube.  As the system is developed (bugs addressed, etc.),
the application author can transition the updated routines into Docker containers, verify locally, and push 
into the container registry for validation within the Kubernetes network.

Here is a brief overview of the steps necessary to run EXTERNAL gateway applications: 

1.  Open a TCP port forward from the local host to a targeted peer: 
```shell
kubectl -n test-network port-forward svc/org1-peer1 7051:7051 
```

2.  Add "mock DNS" records to /etc/hosts for TLS host validation: 
```shell
127.0.0.1 org1-peer1 
```

3.  Configure the gateway client to connect to `org1-peer1:7051`, or the kube TCP port forward.


4.  Launch the gateway client application locally, e.g. in a docker container or attached to an IDE.    


5.  Update this guide with feedback, recipes, and stories of successful client development on Kube/KIND.  


### INTERNAL Gateway Client (In Kube)

#### TODO: Deploy

```shell
./network application ACTION 
```


#### Local Container Registry

Docker images built locally can be uploaded to the `localhost:5000` container registry for
immediate access within the Kube/KIND cluster.  In addition to providing fast turn-around to/from containers 
running in Kube, the use of a private container registry allows us to quickly iterate on code without uploading 
images to the Internet.  Even when using _private_ container registries, the use of a local server saves valuable 
time when loading images into the kind control plane.

e.g.: 
```shell
docker build -t localhost:5000/my-gateway-app . 
docker push localhost:5000/my-gateway-app 
```

Provided that the `imagePullPolicy` for the client deployment is not set to `IfNotPresent`, killing the current pod 
running the gateway client will force a refresh with the latest image layer available at the local registry. 


#### Aggregating MSP and Certificates 

#### Deploying to the Namespace 