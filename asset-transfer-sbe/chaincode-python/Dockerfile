#
# SPDX-License-Identifier: Apache-2.0
#
# Docker image for the Python asset-transfer-sbe chaincode.
# The image is used to run the chaincode as a service (CCAAS) on the
# fabric-test network, one container per peer.
#
# Build from this directory:
#   docker build -f Dockerfile -t sbe_ccaas_image:latest --build-arg CC_SERVER_PORT=9999 .

FROM python:3.11-slim

ARG CC_SERVER_PORT=9999

LABEL maintainer="Hyperledger"
LABEL description="asset-transfer-sbe chaincode (Python) - Hyperledger Fabric chaincode as a service"

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py ./

ENV PYTHONUNBUFFERED=1

EXPOSE ${CC_SERVER_PORT}

CMD ["python", "-u", "main.py"]
