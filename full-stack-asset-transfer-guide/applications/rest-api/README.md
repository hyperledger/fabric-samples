Make sure all the certificates are generated as per the documentation in the below link
https://github.com/hyperledgendary/full-stack-asset-transfer-guide/blob/main/docs/CloudReady/40-bananas.md


#Local development

npm install
npm run prod

Import the postman collections and test the apis


#Kubernetes development

Step-1  Build docker image & Tag

docker build -t localhost:5000/rest-api .

Step-2  Push docker image to local registary

docker push localhost:5000/rest-api

Step-3 Create secrets for the certicates

 kubectl create secret generic client-secret --from-file=keyPath=/home/ramdisk/my-full-stack/infrastructure/sample-network/temp/enrollments/org1/users/org1user/msp/keystore/key.pem --from-file=certPath=/home/ramdisk/my-full-stack/infrastructure/sample-network/temp/enrollments/org1/users/org1user/msp/signcerts/cert.pem --from-file=tlsCertPath=/home/ramdisk/my-full-stack/infrastructure/sample-network/temp/channel-msp/peerOrganizations/org1/msp/tlscacerts/tlsca-signcert.pem -n test-network

please replace the path of /home/ramdisk/my-full-stack/infrastructure/sample-network/temp with your system path

Step-4 Deploy the pods to k8s

kubectl apply -f deployment.yaml  -n test-network

Step-5 Testing API's

Import the apis into postman and test the apis

create & list apis are tested.reminaing apis need be implemented
