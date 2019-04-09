#!/bin/bash
#
# Copyright IBM Corp All Rights Reserved
#
# SPDX-License-Identifier: Apache-2.0
#
# Exit on first error
set -e

# don't rewrite paths for Windows Git Bash users
export MSYS_NO_PATHCONV=1
starttime=$(date +%s)

# launch network; create channel and join peer to channel
cd ../basic-network
./start.sh

cat <<EOF

Total setup execution time : $(($(date +%s) - starttime)) secs ...

Next, use the FabToken application to interact with the Fabric network.

  Start by changing into the "javascript" directory:
    cd javascript

  Next, install all required packages:
    npm install

  Then run the fabtoken application to perform the token operations.

    node fabtoken
      - when no argument is passed, it will run a demo with predefined token operations
    node fabtoken issue <username> <token_type> <quantity>
      - example: node fabtoken issue user1 USD 100
    node fabtoken list <username>
      - example: node fabtoken list user1
      - select a token to transfer or redeem and pass "tx_id" and "index" as input parameters
    node fabtoken transfer <from_user> <to_user> <quantity> <tx_id> <index>
      - example: node fabtoken transfer user1 user2 30 c9b1211d9ad809e6ee1b542de6886d8d1d9e1c938d88eff23a3ddb4e8c080e4d 0
      - <tx_id> and <index> are the "tx_id" and "index" returned from the list operation that specifies the token id for transfer
    node fabtoken redeem <username> <quantity> <tx_id> <index>
      - example: node fabtoken redeem user2 10 477c7bf2002814497c228fd8cbc4d80c8b7f1602b2c17ffadb6cf7e5783fa47a 0
      - <tx_id> and <index> are the "tx_id" and "index" returned from the list operation

EOF
