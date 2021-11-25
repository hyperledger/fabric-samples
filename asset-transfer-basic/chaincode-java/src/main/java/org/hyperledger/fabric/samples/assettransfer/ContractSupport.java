/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.assettransfer;

import java.util.logging.Logger;

import org.hyperledger.fabric.Logging;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.contract.annotation.Transaction.TYPE;

/**
 * A 'support' contract; specifically this enables the logging level to be
 * altered by sending a transaction.
 */
@Contract(name = "ContractSupport")
public class ContractSupport implements ContractInterface {

    private static Logger logger = Logger.getLogger(ContractSupport.class.getName());

    /**
     * Required Default Constructor.
     */
    public ContractSupport() {
        logger.info(() -> "ContractSupport:<init>");
    }

    /**
     * Sets the log level.
     *
     * The setLogLevel method has the required parsing to manage the levels.
     *
     * @param ctx   Transactional Context
     * @param level string id
     */
    @Transaction(intent = TYPE.EVALUATE)
    public void setLogLevel(final Context ctx, final String level) {
        logger.info(() -> "Setting log lebel to " + level);
        Logging.setLogLevel(level);

    }
}
