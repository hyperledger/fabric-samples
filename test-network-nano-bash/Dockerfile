FROM alpine:3.14 AS build

WORKDIR /build

ENV HLF_VERSION=2.4.6
ENV YQ_VERSION=v4.22.1
ENV YQ_BINARY=yq_linux_amd64

RUN apk --no-cache add curl dumb-init

RUN curl -L --retry 5 --retry-delay 3 "https://github.com/mikefarah/yq/releases/download/${YQ_VERSION}/${YQ_BINARY}.tar.gz" | tar xz && mv ${YQ_BINARY} /usr/bin/yq
RUN curl -L --retry 5 --retry-delay 3 "https://github.com/hyperledger/fabric/releases/download/v${HLF_VERSION}/hyperledger-fabric-linux-amd64-${HLF_VERSION}.tar.gz" | tar xz

RUN yq -i 'del(.vm.endpoint) | del(.chaincode.externalBuilders) | .chaincode.externalBuilders[0].name = "ccaas_builder" | .chaincode.externalBuilders[0].path = "/home/nanofab/ccbuilders/ccaas" | .chaincode.externalBuilders[0].propagateEnvironment[0] = "CHAINCODE_AS_A_SERVICE_BUILDER_CONFIG"' config/core.yaml

COPY chaincode-external/ ./chaincode-external/

RUN yq -i '.address = "host.docker.internal:9999"' chaincode-external/connection.json
RUN cd chaincode-external && tar cfz code.tar.gz connection.json && tar cfz external-chaincode.tar.gz code.tar.gz metadata.json

FROM alpine:3.14

ENV FABRIC_CFG_PATH /etc/hyperledger/fabric

RUN apk --no-cache add libc6-compat

COPY --from=build /usr/bin/dumb-init /usr/bin/dumb-init
COPY --chown=root:root --from=build /build/bin/* /usr/local/bin/
COPY --chown=root:root --from=build /build/config/* ${FABRIC_CFG_PATH}/

RUN addgroup -g 500 nanofab && adduser -u 500 -D -h /home/nanofab -G nanofab nanofab
USER nanofab
WORKDIR /home/nanofab

RUN mkdir -p ./ccbuilders/ccaas ./network/logs && ln -s /usr/local/bin ./bin && ln -s ${FABRIC_CFG_PATH} ./config
COPY --chown=nanofab:nanofab --from=build /build/builders/ccaas/ /home/nanofab/ccbuilders/ccaas/
COPY --chown=nanofab:nanofab --from=build /build/chaincode-external/ /home/nanofab/chaincode-external/
COPY --chown=nanofab:nanofab *.sh *.yaml ./network/

WORKDIR /home/nanofab/network
ENTRYPOINT ["/usr/bin/dumb-init", "--"]
CMD ["/home/nanofab/network/network.sh","start"]
