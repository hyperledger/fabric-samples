/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.assettransfer;

import java.util.logging.Logger;

import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.contract.annotation.Transaction.TYPE;
import org.hyperledger.fabric.shim.ChaincodeException;

/**
 * This example contract shows the different ways that errors can be returned
 * from the functions
 *
 * Note that the recommended way is to use the `ChaincodeException` to return
 * business level errors. Te other methods, are to allow you to test client
 * applications and how they respond in different circumstances.
 *
 */
@Contract(name = "ErrorContract", info = @Info(title = "ErrorContract contract", description = "My Smart Contract", version = "0.0.1", license = @License(name = "Apache-2.0", url = ""), contact = @Contact(email = "basic-java-20@example.com", name = "basic-java-20", url = "http://basic-java-20.me")))
public final class ErrorContract implements ContractInterface {

    private static Logger logger = Logger.getLogger(ErrorContract.class.getName());

    /**
     * Required Default Constructor.
     */
    public ErrorContract() {
        logger.info(() -> "MyAssetContract:<init>");
    }

    @Transaction(intent = TYPE.SUBMIT)
    public void payloadChaincodeException(final Context ctx) {
        String payload = String.format("{\"error\":{\"code\":404,\"owner\":\"MrAnon\"}}");
        ChaincodeException cce = new ChaincodeException("[ErrorContract] Payload & Message", payload);

        throw cce;
    }

    @Transaction(intent = TYPE.SUBMIT)
    public void messageOnlyChaincodeException(final Context ctx) {
        ChaincodeException cce = new ChaincodeException("[ErrorContract] Message Only");
        throw cce;
    }

    @Transaction(intent = TYPE.SUBMIT)
    public void causeChaincodeException(final Context ctx) {
        Throwable cause = new NullPointerException("[ErrorContract] Just a fake NPE");
        ChaincodeException cce = new ChaincodeException(cause);
        throw cce;
    }

    static class AnOtherException extends Exception {
        AnOtherException(final String msg) {
            super(msg);
        }
    }

    @Transaction(intent = TYPE.SUBMIT)
    public void anOtherException(final Context ctx) throws AnOtherException {
        AnOtherException e = new AnOtherException("[ErrorContract] Another type of exception");
        throw e;
    }

    static class AnOtherRuntimeException extends RuntimeException {
        AnOtherRuntimeException(final String msg) {
            super(msg);
        }
    }

    @Transaction(intent = TYPE.SUBMIT)
    public void runtimeException(final Context ctx) {
        AnOtherRuntimeException e = new AnOtherRuntimeException("[ErrorContract] Another type of runtime exception");
        throw e;
    }

}
