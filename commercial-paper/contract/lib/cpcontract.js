/*
SPDX-License-Identifier: Apache-2.0
*/

'use strict';

// Smart contract API brought into scope
const {Contract} = require('fabric-contract-api');

// Commercial paper classes brought into scope
const {CommercialPaper, CommercialPaperList} = require('./cpstate.js');

/**
 * Define the commercial paper smart contract extending Fabric Contract class
 */
class CommercialPaperContract extends Contract {

    /**
     * Each smart contract can have a unique namespace; useful when multiple
     * smart contracts per file.
     * Use transaction context (ctx) to access list of all commercial papers.
     */
    constructor() {
        super('org.papernet.commercialpaper');

        this.setBeforeFn = (ctx)=>{
            ctx.cpList = new CommercialPaperList(ctx, 'COMMERCIALPAPER');
            return ctx;
        };
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

        // {issuer:"MagnetoCorp", paperNumber:"00001", "May31 2020", "Nov 30 2020", "5M USD"}

        await ctx.cpList.addPaper(cp);
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

        let cpKey = CommercialPaper.createKey(issuer, paperNumber);
        let cp = await ctx.cpList.getPaper(cpKey);

        if (cp.getOwner() !== currentOwner) {
            throw new Error('Paper '+issuer+paperNumber+' is not owned by '+currentOwner);
        }
        // First buy moves state from ISSUED to TRADING
        if (cp.isIssued()) {
            cp.setTrading();
        }
        // Check paper is TRADING, not REDEEMED
        if (cp.IsTrading()) {
            cp.setOwner(newOwner);
        } else {
            throw new Error('Paper '+issuer+paperNumber+' is not trading. Current state = '+cp.getCurrentState());
        }

        await ctx.cpList.updatePaper(cp);
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

        let cpKey = CommercialPaper.createKey(issuer, paperNumber);
        let cp = await ctx.cpList.getPaper(cpKey);

        // Check paper is TRADING, not REDEEMED
        if (cp.IsRedeemed()) {
            throw new Error('Paper '+issuer+paperNumber+' already redeemed');
        }

        // Verify that the redeemer owns the commercial paper before redeeming it
        if (cp.getOwner() === redeemingOwner) {
            cp.setOwner(cp.getIssuer());
            cp.setRedeemed();
        } else {
            throw new Error('Redeeming owner does not own paper'+issuer+paperNumber);
        }

        await ctx.cpList.updatePaper(cp);
    }

}

module.exports = CommericalPaperContract;
