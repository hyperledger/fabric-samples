#!/bin/bash -e
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#

# exit on first error

Parse_Arguments() {
  while [ $# -gt 0 ]; do
    case $1 in
      --byfn_eyfn_Tests)
        byfn_eyfn_Tests
        ;;
      --fabcar_Tests)
        fabcar_Tests
        ;;
    esac
    shift
  done
}

# run byfn,eyfn tests
byfn_eyfn_Tests() {

  echo
  echo "  ____   __   __  _____   _   _           _____  __   __  _____   _   _  "
  echo " | __ )  \ \ / / |  ___| | \ | |         | ____| \ \ / / |  ___| | \ | | "
  echo " |  _ \   \ V /  | |_    |  \| |  _____  |  _|    \ V /  | |_    |  \| | "
  echo " | |_) |   | |   |  _|   | |\  | |_____| | |___    | |   |  _|   | |\  | "
  echo " |____/    |_|   |_|     |_| \_|         |_____|   |_|   |_|     |_| \_| "

  ./byfn_eyfn.sh
}
# run fabcar tests
fabcar_Tests() {

  echo " #############################"
  echo "npm version ------> $(npm -v)"
  echo "node version ------> $(node -v)"
  echo " #############################"

  echo
  echo " _____      _      ____     ____      _      ____    "
  echo " |  ___|    / \    | __ )   / ___|    / \    |  _ \  "
  echo " | |_      / _ \   |  _ \  | |       / _ \   | |_) | "
  echo " |  _|    / ___ \  | |_) | | |___   / ___ \  |  _ <  "
  echo " |_|     /_/   \_\ |____/   \____| /_/   \_\ |_| \_\ "

  ./fabcar.sh
}

Parse_Arguments $@
