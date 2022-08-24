package org.hyperledger.fabric.samples.insfraudcheck;

import java.util.ArrayList;
import java.util.List;


import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.samples.insfraudcheck.InsClaim;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

import com.owlike.genson.Genson;

@Contract(
        name = "basic",
        info = @Info(
                title = "Claim Processing",
                description = "The hyperlegendary claim processing",
                version = "0.0.1-SNAPSHOT",
                license = @License(
                        name = "Apache 2.0 License",
                        url = "http://www.apache.org/licenses/LICENSE-2.0.html"),
                contact = @Contact(
                        email = "aniket.anerao@lexisnexisrisk.com",
                        name = "Aniket Anerao",
                        url = "https://github.com/aneran01")))
@Default
public final class ClaimProcessing implements ContractInterface {

    private final Genson genson = new Genson();

    private enum ClaimProcessingErrors {
        CLAIM_NOT_FOUND,
        CLAIM_ALREADY_EXISTS
    }

    /**
     * Creates some initial claims on the ledger.
     *
     * @param ctx the transaction context
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void InitLedger(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();

        CreateClaim(ctx, "cl-1", "v-123", 1, "ins-1", 3000);        

    }

    /**
     * Creates a new claim on the ledger.
     *
     * @param ctx the transaction context
     * @param claimID the ID of the new claim
     * @param vin the vin of the new claim
     * @param custId the custId for the new claim
     * @param insuranceId the insuranceId of the new claim
     * @param claimAmount the claimAmount of the new claim
     * @return the created claim
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public InsClaim CreateClaim(final Context ctx, final String claimID, final String vin, final int custId,
        final String insuranceId, final int claimAmount) {
        ChaincodeStub stub = ctx.getStub();

        if (ClaimExists(ctx, claimID)) {
            String errorMessage = String.format("Claim %s already exists", claimID);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, ClaimProcessingErrors.CLAIM_ALREADY_EXISTS.toString());
        }

        InsClaim claim = new InsClaim(claimID, color, size, owner, appraisedValue);
        //Use Genson to convert the Claim into string, sort it alphabetically and serialize it into a json string
        String sortedJson = genson.serialize(claim);
        stub.putStringState(claimID, sortedJson);

        return claim;
    }

}