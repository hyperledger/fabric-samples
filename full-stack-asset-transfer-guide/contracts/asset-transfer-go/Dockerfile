#
# SPDX-License-Identifier: Apache-2.0
#
#Stage 1 – Builder image
FROM golang:1.23-alpine AS builder

RUN apk add --no-cache git

WORKDIR /build


COPY go.mod go.sum ./
RUN go mod download

COPY src/ ./src/
RUN CGO_ENABLED=0 go build -v -o /build/chaincode ./src/...


# Stage 2 – Chaincode-as-a-Service (CaaS) image
FROM alpine:3.20 AS ccaas

ARG TARGETARCH
ARG CC_SERVER_PORT=9999

# tini gives us proper PID-1 signal handling
ENV TINI_VERSION=v0.19.0
ADD https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini-static-${TARGETARCH} /tini
RUN chmod +x /tini

RUN addgroup -S chaincode && adduser -S chaincode -G chaincode
WORKDIR /home/chaincode

COPY --from=builder /build/chaincode ./chaincode
COPY docker/docker-entrypoint.sh ./docker-entrypoint.sh
RUN chmod +x ./docker-entrypoint.sh

ENV PORT=${CC_SERVER_PORT}
EXPOSE ${CC_SERVER_PORT}

USER chaincode
ENTRYPOINT ["/tini", "--", "./docker-entrypoint.sh"]



# Stage 3 – k8s builder image
FROM alpine:3.20 AS k8s

RUN addgroup -S chaincode && adduser -S chaincode -G chaincode
WORKDIR /home/chaincode

COPY --from=builder /build/chaincode ./chaincode
COPY docker/docker-entrypoint.sh ./docker-entrypoint.sh
RUN chmod +x ./docker-entrypoint.sh

USER chaincode
CMD ["./docker-entrypoint.sh"]
