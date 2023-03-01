# Gateway Client Application

[前一步: 安装智能合约](30-chaincode-zh.md) <==> [下一步: 关闭环境](90-teardown.md)

---
这是本次workshop的最后一个练习，我们将会使用Gateway Client来将我们开发的应用[应用](../ApplicationDev)部署在云原生的Fabric网络上。

为了查询账本和提交交易到`asset-transfer`智能合约，客户端应用必须通过制定CA签发的数字身份来启动。一旦用户身份被注册，我们将会用身份来创建，交易虚拟“token”资产。

![Gateway Client Application](../../docs/images/CloudReady/40-gateway-client-app.png)


## 执行

```shell

just check-chaincode

```


## 注册新用户

```shell

# User organization MSP ID
export MSP_ID=Org1MSP        
export ORG=org1
export USERNAME=org1user
export PASSWORD=org1userpw

```

```shell

ADMIN_MSP_DIR=$WORKSHOP_CRYPTO/enrollments/${ORG}/users/rcaadmin/msp
USER_MSP_DIR=$WORKSHOP_CRYPTO/enrollments/${ORG}/users/${USERNAME}/msp
PEER_MSP_DIR=$WORKSHOP_CRYPTO/channel-msp/peerOrganizations/${ORG}/msp

fabric-ca-client  register \
  --id.name       $USERNAME \
  --id.secret     $PASSWORD \
  --id.type       client \
  --url           https://$WORKSHOP_NAMESPACE-$ORG-ca-ca.$WORKSHOP_INGRESS_DOMAIN \
  --tls.certfiles $WORKSHOP_CRYPTO/cas/$ORG-ca/tls-cert.pem \
  --mspdir        $WORKSHOP_CRYPTO/enrollments/$ORG/users/rcaadmin/msp

fabric-ca-client enroll \
  --url           https://$USERNAME:$PASSWORD@$WORKSHOP_NAMESPACE-$ORG-ca-ca.$WORKSHOP_INGRESS_DOMAIN \
  --tls.certfiles $WORKSHOP_CRYPTO/cas/$ORG-ca/tls-cert.pem \
  --mspdir        $WORKSHOP_CRYPTO/enrollments/$ORG/users/$USERNAME/msp

mv $USER_MSP_DIR/keystore/*_sk $USER_MSP_DIR/keystore/key.pem

```

## 启动应用

- Set the gateway client to connect to the org1-peer1 as the newly enrolled `${USERNAME}`:
```shell

# Path to private key file
export PRIVATE_KEY=${USER_MSP_DIR}/keystore/key.pem

# Path to user certificate file
export CERTIFICATE=${USER_MSP_DIR}/signcerts/cert.pem

# Path to CA certificate
export TLS_CERT=${PEER_MSP_DIR}/tlscacerts/tlsca-signcert.pem

# Gateway peer SSL host name override
export HOST_ALIAS=${WORKSHOP_NAMESPACE}-${ORG}-peer1-peer.${WORKSHOP_INGRESS_DOMAIN}

# Gateway endpoint
export ENDPOINT=$HOST_ALIAS:443

```

```shell

pushd applications/trader-typescript

npm install

```

```shell

# Create a yellow banana token owned by appleman@org1 
npm start create banana bananaman yellow

npm start getAllAssets

# Transfer the banana among users / orgs 
npm start transfer banana appleman Org1MSP

npm start getAllAssets

# Transfer the banana among users / orgs 
npm start transfer banana bananaman Org2MSP

# Error! Which org owns the banana? 
npm start transfer banana bananaman Org1MSP

popd

```

# 进一步探索

## Gateway 负载均衡

在之前的例子中，我们的Gateway客户端时通过直接连接的方式连接到peer节点上。我们可以进一步通过负载均衡的方式来进行拓展，通过将Gateway连接到虚拟机的Ingress和k8s Service上。当以这种方式连接时，网关客户端连接通过网关在网络中的组织对等端之间进行负载平衡对等方进一步向对等方发送交易请求，同时保持平衡的账本高度。

![Fabric Gateway deployment](../images/ApplicationDev/fabric-gateway-deployment.png)

相关设置 [Service and Ingress](../../infrastructure/sample-network/config/gateway/org1-peer-gateway.yaml):


- Create a virtual host name / Ingress endpoint for the org peers: 
```shell
pushd applications/trader-typescript

kubectl kustomize \
  ../../infrastructure/sample-network/config/gateway \
  | envsubst \
  | kubectl -n ${WORKSHOP_NAMESPACE} apply -f -  

```

- Run the gateway client application, using the load-balanced Gateway service.  When the gateway client 
connects to the network, the gRPCs connections will be distributed across peers in the org:
```shell

unset HOST_ALIAS
export ENDPOINT=${WORKSHOP_NAMESPACE}-org1-peer-gateway.${WORKSHOP_INGRESS_DOMAIN}:443

npm start getAllAssets

popd
```

Note that in order to support ingress and host access with the new virtual domain, the peer 
CRDs have been instructed to [designate an additional SAN alias](../../infrastructure/sample-network/config/peers/org1-peer1.yaml#L69)
/ host name when provisioning the node TLS certificate with the CA.


---

[前一步: 安装智能合约](30-chaincode-zh.md) <==> [下一步: 关闭环境](90-teardown.md)
