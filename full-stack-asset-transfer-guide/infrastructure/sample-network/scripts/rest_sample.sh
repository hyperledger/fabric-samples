function launch_rest_sample() {
export MSP_ID=Org1MSP        
export ORG=org1
export USERNAME=org2user
export PASSWORD=org1user

ADMIN_MSP_DIR=$WORKSHOP_CRYPTO/enrollments/${ORG}/users/rcaadmin/msp
USER_MSP_DIR=$WORKSHOP_CRYPTO/enrollments/${ORG}/users/${USERNAME}/msp
PEER_MSP_DIR=$WORKSHOP_CRYPTO/channel-msp/peerOrganizations/${ORG}/msp

ENROLLMENT_DIR=${TEMP_DIR}/enrollments
CHANNEL_MSP_DIR=${TEMP_DIR}/channel-msp
CONFIG_DIR=${TEMP_DIR}/fabric-rest-sample-config 

  local peer_pem=$CHANNEL_MSP_DIR/peerOrganizations/org1/msp/tlscacerts/tlsca-signcert.pem
  local ca_pem=$ENROLLMENT_DIR/org1/users/$USERNAME/msp/signcerts/cert.pem
  local keyPath=$ENROLLMENT_DIR/org1/users/$USERNAME/msp/keystore/key.pem
log "Register and enroll a new user at the org CA"
log "registering $USERNAME"
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


#configure secrets 
kubectl -n $WORKSHOP_NAMESPACE delete secret my-secret || true
kubectl create secret generic my-secret --from-file=keyPath=$keyPath  --from-file=certPath=$ca_pem --from-file=tlsCertPath=$peer_pem -n $WORKSHOP_NAMESPACE
#build docker image and push to local registary
log "building restapi docker image"
docker build -t localhost:5000/rest-api $WORKSHOP_PATH/applications/rest-api/
log "pushing restapi docker image to localregistry"
docker push localhost:5000/rest-api
#deploy rest api image to k8s
log "deploying rest api to k8s"
kubectl -n $WORKSHOP_NAMESPACE apply -f scripts/rest_deployment.yaml
}