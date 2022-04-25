/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.fabric.samples.assettransfer;

import org.hyperledger.fabric.contract.ContractRouter;

public final class ContractMain {

    private ContractMain() {
    }

    public static void main(final String[] args) throws Exception {
        if (!System.getenv().containsKey("CHAINCODE_SERVER_ADDRESS")) {
            throw new IllegalArgumentException("Missing required 'CHAINCODE_SERVER_ADDRESS' parameter from env");

        } else if (!System.getenv().containsKey("CORE_CHAINCODE_ID_NAME")) {
            throw new IllegalArgumentException("Missing required 'CORE_CHAINCODE_ID_NAME' parameter from env");
        }

        ContractRouter.main(args);
    }
}
