# Asset Transfer REST API Sample

This is a simple REST server written in golang with endpoints for chaincode invoke and query.

  
## Usage

- Setup fabric test network and deploy the asset transfer chaincode by [following this instructions](https://hyperledger-fabric.readthedocs.io/en/release-2.4/test_network.html).

- cd into rest-api-go directory
- Download required dependencies using `go mod download`
- Run `go run main.go` to run the REST server

## Sending Requests

Invoke endpoint accepts POST requests with chaincode function and arguments. Query endpoint accepts get requests with chaincode function and arguments.

Sample chaincode invoke for the "createAsset" function. Response will contain transaction ID for a successful invoke.

``` sh
curl --request POST \
  --url http://localhost:3000/invoke \
  --header 'content-type: application/x-www-form-urlencoded' \
  --data = \
  --data channelid=mychannel \
  --data chaincodeid=basic \
  --data function=createAsset \
  --data args=Asset123 \
  --data args=yellow \
  --data args=54 \
  --data args=Tom \
  --data args=13005
```
Sample chaincode query for getting asset details.

``` sh
curl --request GET \
  --url 'http://localhost:3000/query?channelid=mychannel&chaincodeid=basic&function=ReadAsset&args=Asset123' 
  ```
