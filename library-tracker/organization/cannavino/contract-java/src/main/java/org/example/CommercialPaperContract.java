/*
SPDX-License-Identifier: Apache-2.0
*/
package org.example;

import java.util.logging.Logger;

import org.example.ledgerapi.State;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeStub;

/**
 * A custom context provides easy access to list of all commercial papers
 */

/**
 * Define commercial paper smart contract by extending Fabric Contract class
 *
 */
@Contract(name = "org.papernet.commercialpaper", info = @Info(title = "MyAsset contract", description = "", version = "0.0.1", license = @License(name = "SPDX-License-Identifier: Apache-2.0", url = ""), contact = @Contact(email = "java-contract@example.com", name = "java-contract", url = "http://java-contract.me")))
@Default
public class CommercialPaperContract implements ContractInterface {

    // use the classname for the logger, this way you can refactor
    private final static Logger LOG = Logger.getLogger(CommercialPaperContract.class.getName());

    @Override
    public Context createContext(ChaincodeStub stub) {
        return new CommercialPaperContext(stub);
    }

    public CommercialPaperContract() {

    }

    /**
     * Define a custom context for commercial paper
     */

    /**
     * Instantiate to perform any setup of the ledger that might be required.
     *
     * @param {Context} ctx the transaction context
     */
    @Transaction
    public void instantiate(CommercialPaperContext ctx) {
        // No implementation required with this example
        // It could be where data migration is performed, if necessary
        LOG.info("No data migration to perform");
    }

    /**
     * Issue commercial paper
     *
     * @param {Context} ctx the transaction context
     * @param {String} issuer commercial paper issuer
     * @param {Integer} paperNumber paper number for this issuer
     * @param {String} issueDateTime paper issue date
     * @param {String} maturityDateTime paper maturity date
     * @param {Integer} faceValue face value of paper
     */
    @Transaction
    public CommercialPaper issue(CommercialPaperContext ctx, String issuer, String paperNumber, String issueDateTime,
            String maturityDateTime, int faceValue) {

        System.out.println(ctx);

        // create an instance of the paper
        CommercialPaper paper = CommercialPaper.createInstance(issuer, paperNumber, issueDateTime, maturityDateTime,
                faceValue,issuer,"");

        // Smart contract, rather than paper, moves paper into ISSUED state
        paper.setIssued();

        // Newly issued paper is owned by the issuer
        paper.setOwner(issuer);

        System.out.println(paper);
        // Add the paper to the list of all similar commercial papers in the ledger
        // world state
        ctx.paperList.addPaper(paper);

        // Must return a serialized paper to caller of smart contract
        return paper;
    }

    /**
     * Buy commercial paper
     *
     * @param {Context} ctx the transaction context
     * @param {String} issuer commercial paper issuer
     * @param {Integer} paperNumber paper number for this issuer
     * @param {String} currentOwner current owner of paper
     * @param {String} newOwner new owner of paper
     * @param {Integer} price price paid for this paper
     * @param {String} purchaseDateTime time paper was purchased (i.e. traded)
     */
    @Transaction
    public CommercialPaper buy(CommercialPaperContext ctx, String issuer, String paperNumber, String currentOwner,
            String newOwner, int price, String purchaseDateTime) {

        // Retrieve the current paper using key fields provided
        String paperKey = State.makeKey(new String[] { paperNumber });
        CommercialPaper paper = ctx.paperList.getPaper(paperKey);

        // Validate current owner
        if (!paper.getOwner().equals(currentOwner)) {
            throw new RuntimeException("Paper " + issuer + paperNumber + " is not owned by " + currentOwner);
        }

        // First buy moves state from ISSUED to TRADING
        if (paper.isIssued()) {
            paper.setTrading();
        }

        // Check paper is not already REDEEMED
        if (paper.isTrading()) {
            paper.setOwner(newOwner);
        } else {
            throw new RuntimeException(
                    "Paper " + issuer + paperNumber + " is not trading. Current state = " + paper.getState());
        }

        // Update the paper
        ctx.paperList.updatePaper(paper);
        return paper;
    }

    /**
     * Redeem commercial paper
     *
     * @param {Context} ctx the transaction context
     * @param {String} issuer commercial paper issuer
     * @param {Integer} paperNumber paper number for this issuer
     * @param {String} redeemingOwner redeeming owner of paper
     * @param {String} redeemDateTime time paper was redeemed
     */
    @Transaction
    public CommercialPaper redeem(CommercialPaperContext ctx, String issuer, String paperNumber, String redeemingOwner,
            String redeemDateTime) {

        String paperKey = CommercialPaper.makeKey(new String[] { paperNumber });

        CommercialPaper paper = ctx.paperList.getPaper(paperKey);

        // Check paper is not REDEEMED
        if (paper.isRedeemed()) {
            throw new RuntimeException("Paper " + issuer + paperNumber + " already redeemed");
        }

        // Verify that the redeemer owns the commercial paper before redeeming it
        if (paper.getOwner().equals(redeemingOwner)) {
            paper.setOwner(paper.getIssuer());
            paper.setRedeemed();
        } else {
            throw new RuntimeException("Redeeming owner does not own paper" + issuer + paperNumber);
        }

        ctx.paperList.updatePaper(paper);
        return paper;
    }

}
