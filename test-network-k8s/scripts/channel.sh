#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

function create_channel_org_MSP() {
  local org=$1
  local org_type=$2
  local ecert_ca=${org}-ecert-ca 
  
  echo 'set -x
 
  mkdir -p /var/hyperledger/fabric/organizations/'${org_type}'Organizations/'${org}'.example.com/msp/cacerts
  cp \
    $FABRIC_CA_CLIENT_HOME/'${ecert_ca}'/rcaadmin/msp/cacerts/'${ecert_ca}'.pem \
    /var/hyperledger/fabric/organizations/'${org_type}'Organizations/'${org}'.example.com/msp/cacerts
  
  mkdir -p /var/hyperledger/fabric/organizations/'${org_type}'Organizations/'${org}'.example.com/msp/tlscacerts
  cp \
    $FABRIC_CA_CLIENT_HOME/tls-ca/tlsadmin/msp/cacerts/'${org}'-tls-ca.pem \
    /var/hyperledger/fabric/organizations/'${org_type}'Organizations/'${org}'.example.com/msp/tlscacerts
  
  echo "NodeOUs:
    Enable: true
    ClientOUIdentifier:
      Certificate: cacerts/'${ecert_ca}'.pem
      OrganizationalUnitIdentifier: client
    PeerOUIdentifier:
      Certificate: cacerts/'${ecert_ca}'.pem
      OrganizationalUnitIdentifier: peer
    AdminOUIdentifier:
      Certificate: cacerts/'${ecert_ca}'.pem
      OrganizationalUnitIdentifier: admin
    OrdererOUIdentifier:
      Certificate: cacerts/'${ecert_ca}'.pem
      OrganizationalUnitIdentifier: orderer "> /var/hyperledger/fabric/organizations/'${org_type}'Organizations/'${org}'.example.com/msp/config.yaml
      
  ' | exec kubectl -n $NS exec deploy/${ecert_ca} -i -- /bin/sh
}

function create_channel_MSP() {
  push_fn "Creating channel MSP"

  create_channel_org_MSP org0 orderer 
  create_channel_org_MSP org1 peer
  create_channel_org_MSP org2 peer

  pop_fn
}

function aggregate_channel_MSP() {
  push_fn "Aggregating channel MSP"

  rm -rf ./build/msp/
  mkdir -p ./build/msp
  
  kubectl -n $NS exec deploy/org0-ecert-ca -- tar zcvf - -C /var/hyperledger/fabric organizations/ordererOrganizations/org0.example.com/msp > build/msp/msp-org0.example.com.tgz
  kubectl -n $NS exec deploy/org1-ecert-ca -- tar zcvf - -C /var/hyperledger/fabric organizations/peerOrganizations/org1.example.com/msp > build/msp/msp-org1.example.com.tgz
  kubectl -n $NS exec deploy/org2-ecert-ca -- tar zcvf - -C /var/hyperledger/fabric organizations/peerOrganizations/org2.example.com/msp > build/msp/msp-org2.example.com.tgz

  kubectl -n $NS delete configmap msp-config || true
  kubectl -n $NS create configmap msp-config --from-file=build/msp/

  pop_fn
}

function launch_admin_CLIs() {
  push_fn "Launching admin CLIs"

  launch kube/org0/org0-admin-cli.yaml
  launch kube/org1/org1-admin-cli.yaml
  launch kube/org2/org2-admin-cli.yaml

  kubectl -n $NS rollout status deploy/org0-admin-cli
  kubectl -n $NS rollout status deploy/org1-admin-cli
  kubectl -n $NS rollout status deploy/org2-admin-cli

  pop_fn
}

function create_genesis_block() {
  push_fn "Creating channel \"${CHANNEL_NAME}\""

  echo 'set -x
  configtxgen -profile TwoOrgsApplicationGenesis -channelID '${CHANNEL_NAME}' -outputBlock genesis_block.pb
  # configtxgen -inspectBlock genesis_block.pb
  
  osnadmin channel join --orderer-address org0-orderer1:9443 --channelID '${CHANNEL_NAME}' --config-block genesis_block.pb
  osnadmin channel join --orderer-address org0-orderer2:9443 --channelID '${CHANNEL_NAME}' --config-block genesis_block.pb
  osnadmin channel join --orderer-address org0-orderer3:9443 --channelID '${CHANNEL_NAME}' --config-block genesis_block.pb
  
  ' | exec kubectl -n $NS exec deploy/org0-admin-cli -i -- /bin/bash
  
  # todo: readiness / liveiness equivalent for channel ?    Needs a little bit to settle before peers can join. 
  sleep 10

  pop_fn
}

function join_org_peers() {
  local org=$1
  push_fn "Joining ${org} peers to channel \"${CHANNEL_NAME}\""

  echo 'set -x
  # Fetch the genesis block from an orderer
  peer channel \
    fetch oldest \
    genesis_block.pb \
    -c '${CHANNEL_NAME}' \
    -o org0-orderer1:6050 \
    --tls --cafile /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/msp/tlscacerts/org0-tls-ca.pem

  # Join peer1 to the channel.
  CORE_PEER_ADDRESS='${org}'-peer1:7051 \
  peer channel \
    join \
    -b genesis_block.pb \
    -o org0-orderer1:6050 \
    --tls --cafile /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/msp/tlscacerts/org0-tls-ca.pem

  # Join peer2 to the channel.
  CORE_PEER_ADDRESS='${org}'-peer2:7051 \
  peer channel \
    join \
    -b genesis_block.pb \
    -o org0-orderer1:6050 \
    --tls --cafile /var/hyperledger/fabric/organizations/ordererOrganizations/org0.example.com/msp/tlscacerts/org0-tls-ca.pem

  ' | exec kubectl -n $NS exec deploy/${org}-admin-cli -i -- /bin/bash

  pop_fn
}

function join_peers() {
  join_org_peers org1
  join_org_peers org2
}

# Copy the scripts/anchor_peers.sh to a remote volume
function push_anchor_peer_script() {
  local org=$1

  tar cf - scripts/ | kubectl -n $NS exec -i -c main deploy/${org}-admin-cli -- tar xf - -C /var/hyperledger/fabric
}

verify_result() {
  if [ $1 -ne 0 ]; then
    echo $2
    exit $1
  fi
}

# Launch the anchor peer update script on a remote org admin CLI
function invoke_anchor_peer_update() {
  local org_num=$1
  local peer_name=$2

  kubectl exec \
    -n $NS \
    -c main \
    deploy/org${org_num}-admin-cli \
    -i \
    /bin/bash -c "/var/hyperledger/fabric/scripts/set_anchor_peer.sh ${org_num} ${CHANNEL_NAME} ${peer_name}"

  verify_result $? "Error updating anchor peer for org ${org_num}"
}

#
# To update the anchor peers we will need to execute a script on each of the peer admin CLI containers.  These
# commands can be individually piped into kubectl exec ... but it will be simpler if we transfer the anchor
# peer update script over to the org volume and then trigger it from kubectl.
#
function update_anchor_peers() {
  local peer_name=$1
  push_fn "Updating anchor peers to ${peer_name}"

  push_anchor_peer_script org1
  push_anchor_peer_script org2

  invoke_anchor_peer_update 1 ${peer_name}
  invoke_anchor_peer_update 2 ${peer_name}

  pop_fn
}

function channel_up() {

  create_channel_MSP
  aggregate_channel_MSP
  launch_admin_CLIs

  create_genesis_block
  join_peers

  # peer1 was set as the anchor peer in configtx.yaml.  Setting this again will force an
  # error to be returned from the channel up.  We might want to render the warning in
  # this case to indicate that the call was made but had a nonzero exit. 
  # update_anchor_peers peer1
}