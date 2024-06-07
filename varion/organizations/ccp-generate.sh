#!/bin/bash

function one_line_pem {
    echo "`awk 'NF {sub(/\\n/, ""); printf "%s\\\\\\\n",$0;}' $1`"
}

function json_ccp {
    local PP=$(one_line_pem $4)
    local CP=$(one_line_pem $5)
    sed -e "s/\${ORG}/$1/" \
        -e "s/\${P0PORT}/$2/" \
        -e "s/\${CAPORT}/$3/" \
        -e "s#\${PEERPEM}#$PP#" \
        -e "s#\${CAPEM}#$CP#" \
        organizations/ccp-template.json
}

function yaml_ccp {
    local PP=$(one_line_pem $4)
    local CP=$(one_line_pem $5)
    sed -e "s/\${ORG}/$1/" \
        -e "s/\${P0PORT}/$2/" \
        -e "s/\${CAPORT}/$3/" \
        -e "s#\${PEERPEM}#$PP#" \
        -e "s#\${CAPEM}#$CP#" \
        organizations/ccp-template.yaml | sed -e $'s/\\\\n/\\\n          /g'
}

ORG=farmer
P0PORT=7051
CAPORT=7054
PEERPEM=organizations/peerOrganizations/farmer.varion.com/tlsca/tlsca.farmer.varion.com-cert.pem
CAPEM=organizations/peerOrganizations/farmer.varion.com/ca/ca.farmer.varion.com-cert.pem

echo "$(json_ccp $ORG $P0PORT $CAPORT $PEERPEM $CAPEM)" > organizations/peerOrganizations/farmer.varion.com/connection-farmer.json
echo "$(yaml_ccp $ORG $P0PORT $CAPORT $PEERPEM $CAPEM)" > organizations/peerOrganizations/farmer.varion.com/connection-farmer.yaml

ORG=pulper
P0PORT=9051
CAPORT=8054
PEERPEM=organizations/peerOrganizations/pulper.varion.com/tlsca/tlsca.pulper.varion.com-cert.pem
CAPEM=organizations/peerOrganizations/pulper.varion.com/ca/ca.pulper.varion.com-cert.pem

echo "$(json_ccp $ORG $P0PORT $CAPORT $PEERPEM $CAPEM)" > organizations/peerOrganizations/pulper.varion.com/connection-pulper.json
echo "$(yaml_ccp $ORG $P0PORT $CAPORT $PEERPEM $CAPEM)" > organizations/peerOrganizations/pulper.varion.com/connection-pulper.yaml

ORG=huller
P0PORT=9051
CAPORT=8054
PEERPEM=organizations/peerOrganizations/huller.varion.com/tlsca/tlsca.huller.varion.com-cert.pem
CAPEM=organizations/peerOrganizations/huller.varion.com/ca/ca.huller.varion.com-cert.pem

echo "$(json_ccp $ORG $P0PORT $CAPORT $PEERPEM $CAPEM)" > organizations/peerOrganizations/huller.varion.com/connection-huller.json
echo "$(yaml_ccp $ORG $P0PORT $CAPORT $PEERPEM $CAPEM)" > organizations/peerOrganizations/huller.varion.com/connection-huller.yaml

ORG=export
P0PORT=9051
CAPORT=8054
PEERPEM=organizations/peerOrganizations/export.varion.com/tlsca/tlsca.export.varion.com-cert.pem
CAPEM=organizations/peerOrganizations/export.varion.com/ca/ca.export.varion.com-cert.pem

echo "$(json_ccp $ORG $P0PORT $CAPORT $PEERPEM $CAPEM)" > organizations/peerOrganizations/export.varion.com/connection-export.json
echo "$(yaml_ccp $ORG $P0PORT $CAPORT $PEERPEM $CAPEM)" > organizations/peerOrganizations/export.varion.com/connection-export.yaml
