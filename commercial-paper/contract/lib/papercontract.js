/*
SPDX-License-Identifier: Apache-2.0
*/

'use strict';

// Fabric smart contract classes
const { Contract, Context } = require('fabric-contract-api');

// PaperNet specifc classes
const { CommercialPaper } = require('./paper.js');

// Utility classes
const { StateList } = require('./ledgerutils.js');

/**
 * Define custom context for commercial paper by extending Fabric Context class
 */
class CommericalPaperContext extends Context {

    constructor() {
        // All papers held ins a list of Fabric states
        this.cpList = new StateList(this, 'org.papernet.commercialpaperlist');
    }

}

/**
 * Define commercial paper smart contract by extending Fabric Contract class
 */
class CommercialPaperContract extends Contract {

    constructor() {
        // Unique namespace when multiple contracts per chaincode file
        super('org.papernet.commercialpaper');
    }

    // This method is called when a smart contract is instantiated
    // Often used to set up the ledger  main transactions are called
    instantiate() {

    }

    // A custom context provides easy access to the list of commercial papers
    createContext() {
        return new CommericalPaperContext();
    }

    /**
     * Issue commercial paper
     * @param {TxContext} ctx the transaction context
     * @param {String} issuer commercial paper issuer
     * @param {Integer} paperNumber paper number for this issuer
     * @param {String} issueDateTime paper issue date
     * @param {String} maturityDateTime paper maturity date
     * @param {Integer} faceValue face value of paper
    */
    async issue(ctx, issuer, paperNumber, issueDateTime, maturityDateTime, faceValue) {

        let cp = new CommercialPaper(issuer, paperNumber, issueDateTime, maturityDateTime, faceValue);

        // Smart contract, rather than paper, moves paper into ISSUED state
        cp.setIssued();

        // Add the paper to the list of all similar commercial papers in the ledger world state
        await ctx.cpList.addState(cp);

        return cp.serialize();
    }

    /**
     * Buy commercial paper
     * @param {TxContext} ctx the transaction context
     * @param {String} issuer commercial paper issuer
     * @param {Integer} paperNumber paper number for this issuer
     * @param {String} currentOwner current owner of paper
     * @param {String} newOwner new owner of paper
     * @param {Integer} price price paid for this paper
     * @param {String} purchaseDateTime time paper was purchased (i.e. traded)
    */
    async buy(ctx, issuer, paperNumber, currentOwner, newOwner, price, purchaseDateTime) {

        let cpKey = CommercialPaper.makeKey([issuer, paperNumber]);

        let cp = await ctx.cpList.getState(cpKey);

        if (cp.getOwner() !== currentOwner) {
            throw new Error('Paper ' + issuer + paperNumber + ' is not owned by ' + currentOwner);
        }
        // First buy moves state from ISSUED to TRADING
        if (cp.isIssued()) {
            cp.setTrading();
        }
        // Check paper is not already REDEEMED
        if (cp.IsTrading()) {
            cp.setOwner(newOwner);
        } else {
            throw new Error('Paper ' + issuer + paperNumber + ' is not trading. Current state = ' + cp.getCurrentState());
        }

        await ctx.cpList.updateState(cp);
        return cp.deserialize();
    }

    /**
     * Redeem commercial paper
     * @param {TxContext} ctx the transaction context
     * @param {String} issuer commercial paper issuer
     * @param {Integer} paperNumber paper number for this issuer
     * @param {String} redeemingOwner redeeming owner of paper
     * @param {String} redeemDateTime time paper was redeemed
    */
    async redeem(ctx, issuer, paperNumber, redeemingOwner, redeemDateTime) {

        let cpKey = CommercialPaper.makeKey([issuer, paperNumber]);

        let cp = await ctx.cpList.getState(cpKey);

        // Check paper is TRADING, not REDEEMED
        if (cp.IsRedeemed()) {
            throw new Error('Paper ' + issuer + paperNumber + ' already redeemed');
        }

        // Verify that the redeemer owns the commercial paper before redeeming it
        if (cp.getOwner() === redeemingOwner) {
            cp.setOwner(cp.getIssuer());
            cp.setRedeemed();
        } else {
            throw new Error('Redeeming owner does not own paper' + issuer + paperNumber);
        }

        await ctx.cpList.updateState(cp);
        return cp.serialize();
    }

}

module.exports = CommericalPaperContract;
