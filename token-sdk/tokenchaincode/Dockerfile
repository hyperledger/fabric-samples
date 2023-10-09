FROM golang:1.20 as builder
RUN git clone https://github.com/hyperledger-labs/fabric-token-sdk.git
WORKDIR fabric-token-sdk 

# Change the hash to checkout a different commit / version. It should be the same as in app/go.mod.
RUN git checkout v0.3.0 && go mod download
RUN CGO_ENABLED=1 go build -buildvcs=false -o /tcc token/services/network/fabric/tcc/main/main.go && chmod +x /tcc

# Final image
FROM golang:1.20
COPY --from=builder /tcc .
EXPOSE 9999

# zkatdlog is the output of the tokengen command. It contains the certificates 
# of the issuer and auditor and the CA that issues owner account credentials,
# As well as cryptographic curves needed by the chaincode to verify proofs.
# It is generated once to initialize the network, when the 'init' function is
# invoked on the chaincode.
ENV PUBLIC_PARAMS_FILE_PATH=/zkatdlog_pp.json
ADD zkatdlog_pp.json /zkatdlog_pp.json

CMD [ "./tcc"]
