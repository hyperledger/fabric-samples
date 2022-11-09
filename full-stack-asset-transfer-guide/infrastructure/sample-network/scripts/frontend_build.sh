function launch_frontend() {
pushd $WORKSHOP_PATH/applications/frontend
log "Install dependencies"
npm install
log "Generate dist"
ng build
log "Build docker"
docker build -t localhost:5000/frontend .
#build docker image and push to local registary
log "Push docker image to registry"
docker push localhost:5000/frontend
popd
# docker push localhost:5000/rest-api
# #deploy rest api image to k8s
kubectl -n $WORKSHOP_NAMESPACE apply -f scripts/frontend_deployment.yaml
}