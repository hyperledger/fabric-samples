local build

npm install
npm start


k8s deployment
Step-1 Prodction build
ng build
Step-2 Build docker image & tag
docker build -t localhost:5000/frontend .
Step-3 Push image to local registary
docker push localhost:5000/frontend
Step-4 deploy to k8s 
kubectl apply -f deployment.yaml  -n test-network

Step-5 Navigate to frontend using below url
https://frontend.localho.st/