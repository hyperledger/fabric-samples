// Copyright IBM Corp All Rights Reserved
//
// SPDX-License-Identifier: Apache-2.0
//

// Pipeline script for fabric-samples

node ('hyp-x') { // trigger build on x86_64 node
    def ROOTDIR = pwd() // workspace dir (/w/workspace/<job_name>
    env.PROJECT_DIR = "gopath/src/github.com/hyperledger"
    def failure_stage = "none"
    // delete working directory
    deleteDir()
      stage("Fetch Patchset") { // fetch gerrit refspec on latest commit
          try {
              dir("${ROOTDIR}"){
              sh '''
                 [ -e gopath/src/github.com/hyperledger/fabric-samples ] || mkdir -p $PROJECT_DIR
                 cd $PROJECT_DIR
                 git clone git://cloud.hyperledger.org/mirror/fabric-samples && cd fabric-samples
                 git fetch origin "$GERRIT_REFSPEC" && git checkout FETCH_HEAD
              '''
              }
          }
          catch (err) {
                 failure_stage = "Fetch patchset"
                 throw err
           }
      }
// clean environment and get env data
      stage("Clean Environment - Get Env Info") {
           try {
                 dir("${ROOTDIR}/$PROJECT_DIR/fabric-samples/scripts/Jenkins_Scripts") {
                 sh './CI_Script.sh --clean_Environment --env_Info'
                 }
               }
           catch (err) {
                 failure_stage = "Clean Environment - Get Env Info"
                 throw err
           }
      }


    // Pull Fabric Images
      stage("Pull third_party images") {
           try {
                 dir("${ROOTDIR}/$PROJECT_DIR/fabric-samples/scripts/Jenkins_Scripts") {
                 sh './CI_Script.sh --pull_Thirdparty_Images'
                 }
               }
           catch (err) {
                 failure_stage = "Pull third_party docker images"
                 throw err
           }
      }

// Pull Fabric Images
      stage("Pull fabric images") {
           try {
                 dir("${ROOTDIR}/$PROJECT_DIR/fabric-samples/scripts/Jenkins_Scripts") {
                 sh './CI_Script.sh --pull_Fabric_Images'
                 }
               }
           catch (err) {
                 failure_stage = "Pull fabric docker images"
                 throw err
           }
      }

 // Pull Fabric-ca
      stage("Pull fabric-ca images") {
           try {
                 dir("${ROOTDIR}/$PROJECT_DIR/fabric-samples/scripts/Jenkins_Scripts") {
                 sh './CI_Script.sh --pull_Fabric_CA_Image'
                 }
               }
           catch (err) {
                 failure_stage = "Pull fabric-ca docker image"
                 throw err
           }
      }
// Run byfn, eyfn tests (default, custom channel, couchdb, nodejs chaincode, fabric-ca samples)
      stage("Run byfn_eyfn Tests") {
           try {
                 dir("${ROOTDIR}/$PROJECT_DIR/fabric-samples/scripts/Jenkins_Scripts") {
                 sh './CI_Script.sh --byfn_eyfn_Tests'
                 }
               }
           catch (err) {
                 failure_stage = "byfn_eyfn_Tests"
                 throw err
           }
      }
}
