#Script to set parameters as environment variables, such as path to users crypto-material
#and configuration files

export CONFIG1_FILE=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/configorg1.json
export ORG1_ADMIN=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp
export USER1_ORG1=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org1.example.com/users/User1@org1.example.com/msp
export CONFIG2_FILE=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/configorg2.json
export SHARES_FILE=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/shares.json
export USER1_ORG2=/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/org2.example.com/users/User1@org2.example.com/msp
export WORK_CHANNEL=mychannel

echo "CONFIG1_FILE="$CONFIG1_FILE
echo "ORG1_ADMIN="$ORG1_ADMIN
echo "USER1_ORG1="$USER1_ORG1
echo "CONFIG2_FILE="$CONFIG2_FILE
echo "SHARES_FILE="$SHARES_FILE
echo "USER1_ORG2="$USER1_ORG2
echo "CONFIG1_FILE="$CONFIG1_FILE
echo "WORK_CHANNEL"=$WORK_CHANNEL
