#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#
queryStatus() {
  echo "===================== Querying approval status for ${CORE_PEER_LOCALMSPID} ===================== "
  local rc=1
  local starttime=$(date +%s)

  # continue to poll
  # we either get a successful response, or reach TIMEOUT
  while
    test "$(($(date +%s) - starttime))" -lt "$TIMEOUT" -a $rc -ne 0
  do
    echo "Attempting to Query approval status for ${CORE_PEER_LOCALMSPID} ...$(($(date +%s) - starttime)) secs"
    set -x
    peer lifecycle chaincode queryapprovalstatus -o irs-orderer:7050 --channelID irs --signature-policy "AND(OR('partya.peer','partyb.peer','partyc.peer'), 'auditor.peer')" --name irscc --version 1 --init-required --sequence 1 >&log.txt
    res=$?
    set +x
    test $res -eq 0 || continue
    let rc=0
    for var in "$@"
    do
        grep "$var" log.txt &>/dev/null || let rc=1
    done
  done
  echo
  cat log.txt
  if test $rc -eq 0; then
    echo "===================== Query approval status successful for ${CORE_PEER_LOCALMSPID} ===================== "
  else
    echo "!!!!!!!!!!!!!!! Query approval status result for ${CORE_PEER_LOCALMSPID} !!!!!!!!!!!!!!!!"
    echo "================== ERROR !!! FAILED to execute End-2-End Scenario =================="
    echo
    exit 1
  fi
}
