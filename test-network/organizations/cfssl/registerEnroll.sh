#!/usr/bin/env bash
# Copyright 2023 Aditya Joshi, All rights reserved

function peer_cert() {

    TYPE=$1 #peer user
    USER=$2
    ORG=$3

    mkdir -p "organizations/peerOrganizations/$ORG.example.com/ca"
    mkdir -p "organizations/peerOrganizations/$ORG.example.com/msp/cacerts"
    mkdir -p "organizations/peerOrganizations/$ORG.example.com/msp/tlscacerts"
    mkdir -p "organizations/peerOrganizations/$ORG.example.com/peers"
    mkdir -p "organizations/peerOrganizations/$ORG.example.com/tlsca"

    CERT_DIR=organizations/peerOrganizations/$ORG.example.com

    if [ ! -f "$CERT_DIR/ca/ca-key.pem" ]; then

        cfssl gencert -initca "${PWD}/organizations/cfssl/ca-peer.json" | cfssljson -bare "$CERT_DIR/ca/ca"

        cp "$CERT_DIR/ca/ca.pem" "$CERT_DIR/tlsca/tlsca.$ORG.example.com-cert.pem"
        cp "$CERT_DIR/ca/ca.pem" "$CERT_DIR/ca/ca.$ORG.example.com-cert.pem"

        cp "$CERT_DIR/ca/ca.pem" "$CERT_DIR/msp/cacerts/"
        cp "$CERT_DIR/ca/ca.pem" "$CERT_DIR/msp/tlscacerts/"

        echo 'NodeOUs:
    Enable: true
    ClientOUIdentifier:
        Certificate: cacerts/ca.pem
        OrganizationalUnitIdentifier: client
    PeerOUIdentifier:
        Certificate: cacerts/ca.pem
        OrganizationalUnitIdentifier: peer
    AdminOUIdentifier:
        Certificate: cacerts/ca.pem
        OrganizationalUnitIdentifier: admin
    OrdererOUIdentifier:
        Certificate: cacerts/ca.pem
        OrganizationalUnitIdentifier: orderer' >"$CERT_DIR/msp/config.yaml"

    fi

    if [[ $TYPE == "peer" ]]; then
        generate_peer_certs "$CERT_DIR" "$USER"
    fi
    if [[ $TYPE == "admin" ]]; then
        generate_user_certs "$CERT_DIR" "$USER" "$TYPE"
    fi

    find . -name "*.csr" -print0 | xargs -0 rm

}

function orderer_cert() {
    TYPE=$1 #orderer user
    USER=$2 #orderer.example.com

    mkdir -p organizations/ordererOrganizations/example.com/ca
    mkdir -p organizations/ordererOrganizations/example.com/msp/cacerts
    mkdir -p organizations/ordererOrganizations/example.com/msp/tlscacerts
    mkdir -p organizations/ordererOrganizations/example.com/orderers
    mkdir -p organizations/ordererOrganizations/example.com/tlsca

    CERT_DIR=organizations/ordererOrganizations/example.com

    if [ ! -f "$CERT_DIR/ca/ca-key.pem" ]; then

        cfssl gencert -initca "${PWD}/organizations/cfssl/ca-orderer.json" | cfssljson -bare "$CERT_DIR/ca/ca"

        cp "$CERT_DIR/ca/ca.pem" "$CERT_DIR/tlsca/tlsca.example.com-cert.pem"

        cp "$CERT_DIR/ca/ca.pem" "$CERT_DIR/msp/cacerts/"
        cp "$CERT_DIR/ca/ca.pem" "$CERT_DIR/msp/tlscacerts/tlsca.example.com-cert.pem"

        echo 'NodeOUs:
    Enable: true
    ClientOUIdentifier:
        Certificate: cacerts/ca.pem
        OrganizationalUnitIdentifier: client
    PeerOUIdentifier:
        Certificate: cacerts/ca.pem
        OrganizationalUnitIdentifier: peer
    AdminOUIdentifier:
        Certificate: cacerts/ca.pem
        OrganizationalUnitIdentifier: admin
    OrdererOUIdentifier:
        Certificate: cacerts/ca.pem
        OrganizationalUnitIdentifier: orderer' >"$CERT_DIR/msp/config.yaml"

    fi

    if [[ $TYPE == "orderer" ]]; then
        generate_orderer_certs $CERT_DIR "$USER"
    fi

    if [[ $TYPE == "admin" ]]; then
        generate_user_certs "$CERT_DIR" "$USER" "$TYPE"
    fi

    find . -name "*.csr" -print0 | xargs -0 rm

}

function generate_user_certs() {

    CERT_DIR=$1
    USER=$2
    TYPE=$3

    mkdir -p $CERT_DIR/users/$USER/tls

    for DIR in cacerts keystore signcerts tlscacerts; do
        mkdir -p $CERT_DIR/users/$USER/msp/$DIR
    done

    sed -e "s/{USER}/$USER/g" <"$PWD/organizations/cfssl/${TYPE}-csr-template.json" >$PWD/organizations/cfssl/${TYPE}-${USER}-csr.json

    cfssl gencert \
        -ca=$CERT_DIR/ca/ca.pem \
        -ca-key=$CERT_DIR/ca/ca-key.pem \
        -config=$PWD/organizations/cfssl/cert-signing-config.json \
        -cn="$USER" \
        -hostname="$USER,localhost,127.0.0.1" \
        -profile="sign" \
        $PWD/organizations/cfssl/${TYPE}-${USER}-csr.json | cfssljson -bare $CERT_DIR/users/$USER/msp/signcerts/cert

    mv $CERT_DIR/users/$USER/msp/signcerts/cert-key.pem $CERT_DIR/users/$USER/msp/keystore/cert-key.pem
    cp $CERT_DIR/ca/ca.pem $CERT_DIR/users/$USER/msp/cacerts
    cp $CERT_DIR/ca/ca.pem $CERT_DIR/users/$USER/msp/tlscacerts

    echo 'NodeOUs:
    Enable: true
    ClientOUIdentifier:
      Certificate: cacerts/ca.pem
      OrganizationalUnitIdentifier: client
    PeerOUIdentifier:
      Certificate: cacerts/ca.pem
      OrganizationalUnitIdentifier: peer
    AdminOUIdentifier:
      Certificate: cacerts/ca.pem
      OrganizationalUnitIdentifier: admin
    OrdererOUIdentifier:
      Certificate: cacerts/ca.pem
      OrganizationalUnitIdentifier: orderer' >$CERT_DIR/users/$USER/msp/config.yaml

    cfssl gencert \
        -ca=$CERT_DIR/ca/ca.pem \
        -ca-key=$CERT_DIR/ca/ca-key.pem \
        -config=$PWD/organizations/cfssl/cert-signing-config.json \
        -cn="$USER" \
        -hostname="$USER,localhost,127.0.0.1" \
        -profile="tls" \
        $PWD/organizations/cfssl/${TYPE}-${USER}-csr.json | cfssljson -bare $CERT_DIR/users/$USER/tls/client

    cp $CERT_DIR/ca/ca.pem $CERT_DIR/users/$USER/tls/ca.crt
    mv $CERT_DIR/users/$USER/tls/client-key.pem $CERT_DIR/users/$USER/tls/client.key
    mv $CERT_DIR/users/$USER/tls/client.pem $CERT_DIR/users/$USER/tls/client.crt

    rm $PWD/organizations/cfssl/${TYPE}-${USER}-csr.json

}

function generate_peer_certs() {
    CERT_DIR=$1
    USER=$2

    for DIR in cacerts keystore signcerts tlscacerts; do
        mkdir -p "$CERT_DIR/peers/$USER/msp/$DIR"
    done

    mkdir -p "$CERT_DIR/peers/$USER/tls"
    sed -e "s/{USER}/$USER/g" <"$PWD/organizations/cfssl/peer-csr-template.json" >"$PWD/organizations/cfssl/peer-${USER}.json"

    cfssl gencert \
        -ca="$CERT_DIR/ca/ca.pem" \
        -ca-key="$CERT_DIR/ca/ca-key.pem" \
        -config="$PWD/organizations/cfssl/cert-signing-config.json" \
        -cn="$USER" \
        -hostname="$USER,localhost,127.0.0.1" \
        -profile="sign" \
        "$PWD/organizations/cfssl/peer-${USER}.json" | cfssljson -bare "$CERT_DIR/peers/${USER}/msp/signcerts/cert"

    mv "$CERT_DIR/peers/$USER/msp/signcerts/cert-key.pem" "$CERT_DIR/peers/$USER/msp/keystore"

    cp "$CERT_DIR/ca/ca.pem" "$CERT_DIR/peers/$USER/msp/cacerts"
    cp "$CERT_DIR/ca/ca.pem" "$CERT_DIR/peers/$USER/msp/tlscacerts"

    echo 'NodeOUs:
    Enable: true
    ClientOUIdentifier:
        Certificate: cacerts/ca.pem
        OrganizationalUnitIdentifier: client
    PeerOUIdentifier:
        Certificate: cacerts/ca.pem
        OrganizationalUnitIdentifier: peer
    AdminOUIdentifier:
        Certificate: cacerts/ca.pem
        OrganizationalUnitIdentifier: admin
    OrdererOUIdentifier:
        Certificate: cacerts/ca.pem
        OrganizationalUnitIdentifier: orderer' >"$CERT_DIR/peers/$USER/msp/config.yaml"

    cfssl gencert \
        -ca="$CERT_DIR/ca/ca.pem" \
        -ca-key="$CERT_DIR/ca/ca-key.pem" \
        -config="$PWD/organizations/cfssl/cert-signing-config.json" \
        -cn="$USER" \
        -hostname="$USER,localhost,127.0.0.1" \
        -profile="tls" \
        "$PWD/organizations/cfssl/peer-${USER}.json" | cfssljson -bare "$CERT_DIR/peers/$USER/tls/server"

    cp "$CERT_DIR/ca/ca.pem" "$CERT_DIR/peers/$USER/tls/ca.crt"
    mv "$CERT_DIR/peers/$USER/tls/server.pem" "$CERT_DIR/peers/$USER/tls/server.crt"
    mv "$CERT_DIR/peers/$USER/tls/server-key.pem" "$CERT_DIR/peers/$USER/tls/server.key"

    rm "$PWD/organizations/cfssl/peer-${USER}.json"
}

function generate_orderer_certs() {

    CERT_DIR=$1
    USER=$2

    for DIR in cacerts keystore signcerts tlscacerts; do
        mkdir -p "organizations/ordererOrganizations/example.com/orderers/$USER/msp/$DIR"
    done

    mkdir -p "organizations/ordererOrganizations/example.com/orderers/$USER/tls"

    sed -e "s/{USER}/$USER/g" <"$PWD/organizations/cfssl/orderer-csr-template.json" >"$PWD/organizations/cfssl/orderer-${USER}.json"

    cfssl gencert \
        -ca="$CERT_DIR/ca/ca.pem" \
        -ca-key="$CERT_DIR/ca/ca-key.pem" \
        -config="$PWD/organizations/cfssl/cert-signing-config.json" \
        -cn="$USER" \
        -hostname="$USER,localhost,127.0.0.1" \
        -profile="sign" \
        "$PWD/organizations/cfssl/orderer-${USER}.json" | cfssljson -bare "$CERT_DIR/orderers/$USER/msp/signcerts/cert"

    mv "$CERT_DIR/orderers/$USER/msp/signcerts/cert-key.pem" "$CERT_DIR/orderers/$USER/msp/keystore"

    cp "$CERT_DIR/ca/ca.pem" "$CERT_DIR/orderers/$USER/msp/cacerts"
    cp "$CERT_DIR/ca/ca.pem" "$CERT_DIR/orderers/$USER/msp/tlscacerts/tlsca.example.com-cert.pem"

    echo 'NodeOUs:
    Enable: true
    ClientOUIdentifier:
        Certificate: cacerts/ca.pem
        OrganizationalUnitIdentifier: client
    PeerOUIdentifier:
        Certificate: cacerts/ca.pem
        OrganizationalUnitIdentifier: peer
    AdminOUIdentifier:
        Certificate: cacerts/ca.pem
        OrganizationalUnitIdentifier: admin
    OrdererOUIdentifier:
        Certificate: cacerts/ca.pem
        OrganizationalUnitIdentifier: orderer' >"$CERT_DIR/orderers/$USER/msp/config.yaml"

    cfssl gencert \
        -ca="$CERT_DIR/ca/ca.pem" \
        -ca-key="$CERT_DIR/ca/ca-key.pem" \
        -config="$PWD/organizations/cfssl/cert-signing-config.json" \
        -cn="$USER" \
        -hostname="$USER,localhost,127.0.0.1" \
        -profile="tls" \
        "$PWD/organizations/cfssl/orderer-${USER}.json" | cfssljson -bare "$CERT_DIR/orderers/$USER/tls/server"

    cp "$CERT_DIR/ca/ca.pem" "$CERT_DIR/orderers/$USER/tls/ca.crt"
    mv "$CERT_DIR/orderers/$USER/tls/server.pem" "$CERT_DIR/orderers/$USER/tls/server.crt"
    mv "$CERT_DIR/orderers/$USER/tls/server-key.pem" "$CERT_DIR/orderers/$USER/tls/server.key"
    rm "$PWD/organizations/cfssl/orderer-${USER}.json"
}
