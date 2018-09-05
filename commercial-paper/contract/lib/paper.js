/*
SPDX-License-Identifier: Apache-2.0
*/

'use strict';

// Enumerate commercial paper state values
const cpState = {
    ISSUED: 1,
    TRADING: 2,
    REDEEMED: 3
};

/**
 * State class. States have a type, unique key, and a lifecycle current state
 */
class State {
    constructor(type, [keyParts]) {
        this.type = JSON.stringify(type);
        this.key = makeKey([keyParts]);
        this.currentState = null;
    }

    getType() {
        return this.type;
    }

    static makeKey([keyParts]) {
        return keyParts.map(part => JSON.stringify(part)).join('');
    }

    getKey() {
        return this.key;
    }

}

/**
 * CommercialPaper class extends State class
 * Class will be used by application and smart contract to define a paper
 */
class CommercialPaper extends State {

    constructor(issuer, paperNumber, issueDateTime, maturityDateTime, faceValue) {
        super(`org.papernet.commercialpaper`, [issuer, paperNumber]);

        this.issuer = issuer;
        this.paperNumber = paperNumber;
        this.owner = issuer;
        this.issueDateTime = issueDateTime;
        this.maturityDateTime = maturityDateTime;
        this.faceValue = faceValue;
    }

    /**
     * Basic getters and setters
    */
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
    setIssued() {
        this.currentState = cpState.ISSUED;
    }

    setTrading() {
        this.currentState = cpState.TRADING;
    }

    setRedeemed() {
        this.currentState = cpState.REDEEMED;
    }

    isIssued() {
        return this.currentState === cpState.ISSUED;
    }

    isTrading() {
        return this.currentState === cpState.TRADING;
    }

    isRedeemed() {
        return this.currentState === cpState.REDEEMED;
    }

    /**
     * Serialize/deserialize commercial paper
     **/

    serialize() {
        return Buffer.from(JSON.stringify(this));
    }

    static deserialize(data) {
        return Object.create(new CommercialPaper, JSON.parse(data));
    }

}

module.exports = {
    CommercialPaper,
};
