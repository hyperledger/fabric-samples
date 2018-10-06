/*
SPDX-License-Identifier: Apache-2.0
*/

'use strict';

// Helpful utilities class
const Utils = require('./utils.js');

// Enumeration of commercial paper state values
const cpState = {
    ISSUED: 1,
    TRADING: 2,
    REDEEMED: 3
};

/**
 * CommercialPaper class defines a commercial paper state
 */
class CommercialPaper {

    /**
     * Construct a commercial paper.  Initial state is issued.
     */
    constructor(issuer, paperNumber, issueDateTime, maturityDateTime, faceValue) {
        this.issuer = issuer;
        this.paperNumber = paperNumber;
        this.owner = issuer;
        this.issueDateTime = issueDateTime;
        this.maturityDateTime = maturityDateTime;
        this.faceValue = faceValue;
        this.currentState = cpState.ISSUED;
        this.key = CommercialPaper.createKey(issuer, paperNumber);
    }

    /**
     * The commercial paper is uniquely identified by its key.
     * The key is a simple composite of issuer and paper number as strings.
     */
    static createKey(issuer, paperNumber) {
        return JSON.stringify(issuer) + JSON.stringify(paperNumber);
    }

    /**
     * Basic getters and setters
     */
    getKey() {
        return this.key;
    }

    getIssuer() {
        return this.issuer;
    }

    setIssuer(newIssuer) {
        this.issuer = newIssuer;
    }

    getOwner() {
        return this.owner;
    }

    setOwner(newOwner) {
        this.owner = newOwner;
    }

    /**
     * Useful methods to encapsulate commercial paper states
     */
    setTrading() {
        this.currentState = cpState.TRADING;
    }

    setRedeemed() {
        this.currentState = cpState.REDEEMED;
    }

    isTrading() {
        return this.currentState === cpState.TRADING;
    }

    isRedeemed() {
        return this.currentState === cpState.REDEEMED;
    }

}

/**
 * CommercialPaperList provides a virtual container to access all
 * commercial papers. Each paper has unique key which associates it
 * with the container, rather than the container containing a link to
 * the paper. This is important in Fabric becuase it minimizes
 * collisions for parallel transactions on different papers.
 */
class CommercialPaperList {

    /**
     * For this sample, it is sufficient to create a commercial paper list
     * using a fixed container prefix. The transaction context is saved to
     * access Fabric APIs when required.
     */
    constructor(ctx, prefix) {
        this.api = ctx.stub;
        this.prefix = prefix;
    }

    /**
     * Add a paper to the list. Creates a new state in worldstate with
     * appropriate composite key.  Note that paper defines its own key.
     * Paper object is serialized before writing.
     */
    async addPaper(cp) {
        let key = this.api.createCompositeKey(this.prefix, [cp.getKey()]);
        let data = Utils.serialize(cp);
        await this.api.putState(key, data);
    }

    /**
     * Get a paper from the list using issuer and paper number. Forms composite
     * keys to retrieve data from world state. State data is deserialized
     * into paper object before being returned.
     */
    async getPaper(key) {
        let key = this.api.createCompositeKey(this.prefix, [key]);
        let data = await this.api.getState(key);
        let cp = Utils.deserialize(data);
        return cp;
    }

    /**
     * Update a paper in the list. Puts the new state in world state with
     * appropriate composite key.  Note that paper defines its own key.
     * Paper object is serialized before writing. Logic is very similar to
     * addPaper() but kept separate becuase it is semantically distinct, and
     * may change.
     */
    async updatePaper(cp) {
        let key = this.api.createCompositeKey(this.prefix, [cp.getKey()]);
        let data = Utils.serialize(cp);
        await this.api.putState(key, data);
    }

}

module.exports = {
    CommercialPaper,
    CommercialPaperList
};
