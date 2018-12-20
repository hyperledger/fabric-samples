// Copyright IBM Corp All Rights Reserved
//
// SPDX-License-Identifier: Apache-2.0
//

// Pipeline script for fabric-samples

node ('hyp-x') { // trigger build on x86_64 node
  timestamps {
   try {
    def ROOTDIR = pwd() // workspace dir (/w/workspace/<job_name>
    def nodeHome = tool 'nodejs-8.11.3'
    env.ARCH = "amd64"
    env.VERSION = sh(returnStdout: true, script: 'curl -O https://raw.githubusercontent.com/hyperledger/fabric/master/Makefile && cat Makefile | grep "BASE_VERSION =" | cut -d "=" -f2').trim()
    env.VERSION = "$VERSION" // BASE_VERSION from fabric Makefile
    env.BASE_IMAGE_VER = sh(returnStdout: true, script: 'cat Makefile | grep "BASEIMAGE_RELEASE =" | cut -d "=" -f2').trim() // BASEIMAGE Version from fabric Makefile
    env.IMAGE_TAG = "${ARCH}-${VERSION}-stable" // fabric latest stable version from nexus
    env.PROJECT_VERSION = "${VERSION}-stable"
    env.BASE_IMAGE_TAG = "${ARCH}-${BASE_IMAGE_VER}" //fabric baseimage version
    env.PROJECT_DIR = "gopath/src/github.com/hyperledger"
    env.GOPATH = "$WORKSPACE/gopath"
    env.PATH = "$GOPATH/bin:/usr/local/bin:/usr/bin:/usr/local/sbin:/usr/sbin:${nodeHome}/bin:$PATH"

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
                 currentBuild.result = 'FAILURE'
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
                 currentBuild.result = 'FAILURE'
                 throw err
           }
         }

    // Pull Third_party Images
      stage("Pull third_party images") {
         // making the output color coded
         wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'xterm']) {
           try {
                 dir("${ROOTDIR}/$PROJECT_DIR/fabric-samples/scripts/Jenkins_Scripts") {
                 sh './CI_Script.sh --pull_Thirdparty_Images'
                 }
               }
           catch (err) {
                 failure_stage = "Pull third_party docker images"
                 currentBuild.result = 'FAILURE'
                 throw err
           }
         }
      }

      // Pull Fabric, fabric-ca Images
      stage("Pull Docker images") {
         // making the output color coded
         wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'xterm']) {
           try {
                 dir("${ROOTDIR}/$PROJECT_DIR/fabric-samples/scripts/Jenkins_Scripts") {
                 sh './CI_Script.sh --pull_Docker_Images'
                 }
               }
           catch (err) {
                 failure_stage = "Pull fabric, fabric-ca docker images"
                 currentBuild.result = 'FAILURE'
                 throw err
           }
         }
      }

      // Run byfn, eyfn tests (default, custom channel, couchdb, nodejs chaincode)
      stage("Run byfn_eyfn Tests") {
         // making the output color coded
         wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'xterm']) {
           try {
                 dir("${ROOTDIR}/$PROJECT_DIR/fabric-samples/scripts/Jenkins_Scripts") {
                 sh './CI_Script.sh --byfn_eyfn_Tests'
                 }
               }
           catch (err) {
                 failure_stage = "byfn_eyfn_Tests"
                 currentBuild.result = 'FAILURE'
                 throw err
           }
         }
      }

      // Run fabcar tests
      stage("Run FabCar Tests") {
         // making the output color coded
         wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'xterm']) {
           try {
                 dir("${ROOTDIR}/$PROJECT_DIR/fabric-samples/scripts/Jenkins_Scripts") {
                 sh './CI_Script.sh --fabcar_Tests'
                 }
               }
           catch (err) {
                 failure_stage = "fabcar_Tests"
                 currentBuild.result = 'FAILURE'
                 throw err
           }
         }
      }
      } finally {
           // Archive the artifacts
           archiveArtifacts allowEmptyArchive: true, artifacts: '**/*.log'
           // Sends notification to Rocket.Chat jenkins-robot channel
           if (env.JOB_NAME == "fabric-samples-merge-byfn") {
              if (currentBuild.result == 'FAILURE') { // Other values: SUCCESS, UNSTABLE
               rocketSend message: "Build Notification - STATUS: *${currentBuild.result}* - BRANCH: *${env.GERRIT_BRANCH}* - PROJECT: *${env.PROJECT}* - (<${env.BUILD_URL}|Open>)"
              }
           }
        }
// End Try block
  }
// End Node block
}
