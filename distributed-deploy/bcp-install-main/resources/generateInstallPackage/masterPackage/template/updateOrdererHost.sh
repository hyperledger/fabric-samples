#! /bin/bash
#
# Copyright CGB Corp. All Rights Reserved.
#
# SPDX-License-Identifier: Apache-2.0
#

NEWORDERERHOST="$1"
NEWORDERERIP="$2"

echo "${NEWORDERERIP} ${NEWORDERERHOST}" >> /etc/hosts