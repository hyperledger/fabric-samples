# Copyright IBM Corp. All Rights Reserved.
#
# SPDX-License-Identifier: Apache-2.0

FROM golang:alpine

WORKDIR /go/src/github.com/hyperledger/fabric-samples/asset-transfer-basic/chaincode-external
COPY . .

RUN go get -d -v ./...
RUN go install -v ./...

EXPOSE 9999
CMD ["chaincode-external"]
