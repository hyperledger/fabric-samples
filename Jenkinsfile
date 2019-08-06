#!groovy
// Copyright IBM Corp All Rights Reserved
//
// SPDX-License-Identifier: Apache-2.0
//

// Jenkinfile will get triggered on verify and merge jobs and run byfn, eyfn and fabcar
// tests.

// global shared library from ci-management repository
// https://github.com/hyperledger/ci-management/tree/master/vars (Global Shared scripts)
@Library("fabric-ci-lib") _
  pipeline {
    agent {
      // Execute tests on x86_64 build nodes
      // Set this value from Jenkins Job Configuration
      label env.NODE_ARCH
    }
      options {
        // Using the Timestamper plugin we can add timestamps to the console log
        timestamps()
        // Set build timeout for 60 mins
        timeout(time: 60, unit: 'MINUTES')
      }
      environment {
        ROOTDIR = pwd()
        // Applicable only on x86_64 nodes
        // LF team has to install the newer version in Jenkins global config
        // Send an email to helpdesk@hyperledger.org to add newer version
        nodeHome = tool 'nodejs-8.11.3'
        MARCH = sh(returnStdout: true, script: "uname -m | sed 's/x86_64/amd64/g'").trim()
        OS_NAME = sh(returnStdout: true, script: "uname -s|tr '[:upper:]' '[:lower:]'").trim()
        props = "null"
      }
      stages {
        stage('Clean Environment') {
          steps {
            script {
              // delete working directory
              deleteDir()
              // Clean build env before start the build
              fabBuildLibrary.cleanupEnv()
              // Display jenkins environment details
              fabBuildLibrary.envOutput()
            }
          }
        }
        stage('Checkout SCM') {
          steps {
            script {
              // Get changes from gerrit
              fabBuildLibrary.cloneRefSpec('fabric-samples')
              // Load properties from ci.properties file
              props = fabBuildLibrary.loadProperties()
            }
          }
        }
        // Pull build artifacts
        stage('Pull Build Artifacts') {
          steps {
            script {
                if(props["IMAGE_SOURCE"] == "build") {
                  println "BUILD ARTIFACTS"
                  // Set PATH
                  env.GOPATH = "$WORKSPACE/gopath"
                  env.GOROOT = "/opt/go/go" + props["GO_VER"] + ".linux." + "$MARCH"
                  env.PATH = "$GOPATH/bin:$GOROOT/bin:/usr/local/bin:/usr/bin:/usr/local/sbin:/usr/sbin:${nodeHome}/bin:$PATH"
                  // Clone fabric repo
                  fabBuildLibrary.cloneScm('fabric', '$GERRIT_BRANCH')
                  // Build fabric docker images and binaries
                  fabBuildLibrary.fabBuildImages('fabric', 'docker dist')
                  // Clone fabric-ca repo
                  fabBuildLibrary.cloneScm('fabric-ca', '$GERRIT_BRANCH')
                  // Build fabric docker images and binaries
                  fabBuildLibrary.fabBuildImages('fabric-ca', 'docker dist')
                  // Copy binaries to fabric-samples dir
                  sh 'cp -r $ROOTDIR/gopath/src/github.com/hyperledger/fabric/release/$OS_NAME-$MARCH/bin $ROOTDIR/$BASE_DIR/'
                  // Pull Thirdparty Docker Images from hyperledger DockerHub
                  fabBuildLibrary.pullThirdPartyImages(props["FAB_BASEIMAGE_VERSION"], props["FAB_THIRDPARTY_IMAGES_LIST"])
                } else {
                  dir("$ROOTDIR/$BASE_DIR") {
                    // Set PATH
                    env.GOPATH = "$WORKSPACE/gopath"
                    env.GOROOT = "/opt/go/go" + props["GO_VER"] + ".linux." + "$MARCH"
                    env.PATH = "$GOPATH/bin:$GOROOT/bin:/usr/local/bin:/usr/bin:/usr/local/sbin:/usr/sbin:${nodeHome}/bin:$PATH"
                    // Pull Binaries with latest version from nexus2
                    fabBuildLibrary.pullBinaries(props["FAB_BINARY_VER"], props["FAB_BINARY_REPO"])
                    // Pull Docker Images from nexus3
                    fabBuildLibrary.pullDockerImages(props["FAB_BASE_VERSION"], props["FAB_IMAGES_LIST"])
                    // Pull Thirdparty Docker Images from hyperledger DockerHub
                    fabBuildLibrary.pullThirdPartyImages(props["FAB_BASEIMAGE_VERSION"], props["FAB_THIRDPARTY_IMAGES_LIST"])
                  }
                  }
            }
          }
        }
        // Run byfn, eyfn tests (default, custom channel, couchdb, nodejs, java chaincode)
        stage('Run byfn_eyfn Tests') {
          steps {
            script {
              // making the output color coded
              wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'xterm']) {
                try {
                  dir("$ROOTDIR/$BASE_DIR/scripts/ci_scripts") {
                    // Run BYFN, EYFN tests
                    sh './ciScript.sh --byfn_eyfn_Tests'
                  }
                }
                catch (err) {
                  failure_stage = "byfn_eyfn_Tests"
                  currentBuild.result = 'FAILURE'
                  throw err
                }
              }
            }
          }
        }
        // Run fabcar tests
        stage('Run Fab Car Tests') {
          steps {
            script {
              // making the output color coded
              wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'xterm']) {
                try {
                  dir("$ROOTDIR/$BASE_DIR/scripts/ci_scripts") {
                    // Run fabcar tests
                    sh './ciScript.sh --fabcar_Tests'
                  }
                }
                catch (err) {
                  failure_stage = "fabcar_Tests"
                  currentBuild.result = 'FAILURE'
                  throw err
                }
              }
            }
          }
        }
      } // stages
      post {
        always {
          // Archiving the .log files and ignore if empty
          archiveArtifacts artifacts: '**/*.log', allowEmptyArchive: true
        }
        failure {
          script {
            if (env.JOB_TYPE == 'merge') {
              // Send rocketChat notification to channel
              // Send merge build failure email notifications to the submitter
              sendNotifications(currentBuild.result, props["CHANNEL_NAME"])
              // Delete workspace when build is done
              cleanWs notFailBuild: true
            }
          }
        }
      } // post
  } // pipeline
