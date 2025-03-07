/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
*/

'use strict';

const { Contract } = require('fabric-contract-api');

// Define objectType names for prefix
const balancePrefix = 'balance';
const allowancePrefix = 'allowance';

// Define key names for options
const nameKey = 'name';
const symbolKey = 'symbol';
const decimalsKey = 'decimals';
const totalSupplyKey = 'totalSupply';

class TokenERC20Contract extends Contract {

    /**
     * Return the name of the token - e.g. "MyToken".
     * The original function name is `name` in ERC20 specification.
     * However, 'name' conflicts with a parameter `name` in `Contract` class.
     * As a work around, we use `TokenName` as an alternative function name.
     *
     * @param {Context} ctx the transaction context
     * @returns {Promise<String>} Returns the name of the token
    */
    async TokenName(ctx) {

        // Check contract options are already set first to execute the function
        await this.CheckInitialized(ctx);

        const nameBytes = await ctx.stub.getState(nameKey);

        return nameBytes.toString();
    }

    /**
     * Return the symbol of the token. E.g. “HIX”.
     *
     * @param {Context} ctx the transaction context
     * @returns {Promise<String>} Returns the symbol of the token
    */
    async Symbol(ctx) {

        // Check contract options are already set first to execute the function
        await this.CheckInitialized(ctx);

        const symbolBytes = await ctx.stub.getState(symbolKey);

        return symbolBytes.toString();
    }

    /**
     * Return the number of decimals the token uses
     * e.g. 8, means to divide the token amount by 100000000 to get its user representation.
     *
     * @param {Context} ctx the transaction context
     * @returns {Promise<Number>} Returns the number of decimals
    */
    async Decimals(ctx) {

        // Check contract options are already set first to execute the function
        await this.CheckInitialized(ctx);

        const decimalsBytes = await ctx.stub.getState(decimalsKey);
        const decimals = parseInt(decimalsBytes.toString());

        return decimals;
    }

    /**
     * Return the total token supply.
     *
     * @param {Context} ctx the transaction context
     * @returns {Promise<Number>} Returns the total token supply
    */
    async TotalSupply(ctx) {

        // Check contract options are already set first to execute the function
        await this.CheckInitialized(ctx);

        const totalSupplyBytes = await ctx.stub.getState(totalSupplyKey);
        const totalSupply = parseInt(totalSupplyBytes.toString());

        return totalSupply;
    }

    /**
     * BalanceOf returns the balance of the given account.
     *
     * @param {Context} ctx the transaction context
     * @param {String} owner The owner from which the balance will be retrieved
     * @returns {Promise<Number>} Returns the account balance
     */
    async BalanceOf(ctx, owner) {

        // Check contract options are already set first to execute the function
        await this.CheckInitialized(ctx);

        const balanceKey = ctx.stub.createCompositeKey(balancePrefix, [owner]);
        const balanceBytes = await ctx.stub.getState(balanceKey);
        const balance = this.isEmpty(balanceBytes) ? 0 : parseInt(balanceBytes.toString());

        return balance;
    }

    /**
     *  Transfer transfers tokens from client account to recipient account.
     *  recipient account must be a valid clientID as returned by the ClientAccountID() function.
     *
     * @param {Context} ctx the transaction context
     * @param {String} to The recipient
     * @param {Number} value The amount of token to be transferred
     * @returns {Promise<Boolean>} Return whether the transfer was successful or not
     */
    async Transfer(ctx, to, value) {

        // Check contract options are already set first to execute the function
        await this.CheckInitialized(ctx);

        const from = this.ClientAccountID(ctx);
        await this._transfer(ctx, from, to, value);

        console.log('transfer ended successfully');
        return true;
    }

    /**
    * Transfer `value` amount of tokens from `from` to `to`.
    *
    * @param {Context} ctx the transaction context
    * @param {String} from The sender
    * @param {String} to The recipient
    * @param {Number} value The amount of token to be transferred
    * @returns {Promise<Boolean>} Return whether the transfer was successful or not
    */
    async TransferFrom(ctx, from, to, value) {

        // Check contract options are already set first to execute the function
        await this.CheckInitialized(ctx);

        const spender = this.ClientAccountID(ctx);
        await this._spendAllowance(ctx, from, spender, value);
        await this._transfer(ctx, from, to, value);

        console.log('transferFrom ended successfully');
        return true;
    }

    /**
     * Moves a `value` amount of tokens from `from` to `to`.
     *
     * @param {Context} ctx the transaction context
     * @param {String} from The sender
     * @param {String} to The recipient
     * @param {Promise<Number>} value The amount of token to be transferred
     */
    async _transfer(ctx, from, to, value) {
        if (from === to) {
            throw new Error('cannot transfer to and from same client account');
        }
        if (this.isEmpty(from)) {
            throw new Error('invalid sender');
        }
        if (this.isEmpty(to)) {
            throw new Error('invalid receiver');
        }

        await this._update(ctx, from, to, value);
    }

    /**
     * Allows `spender` to spend `value` amount of tokens from the owner.
     *
     * @param {Context} ctx the transaction context
     * @param {String} spender The spender
     * @param {Number} value The amount of tokens to be approved for transfer
     * @returns {Promise<Boolean>} Return whether the approval was successful or not
     */
    async Approve(ctx, spender, value) {

        // Check contract options are already set first to execute the function
        await this.CheckInitialized(ctx);

        const owner = this.ClientAccountID(ctx);
        await this._approve(ctx, owner, spender, value);
        console.log('approve ended successfully');

        return true;
    }

    /**
     * Returns the amount of tokens which `spender` is allowed to withdraw from `owner`.
     *
     * @param {Context} ctx the transaction context
     * @param {String} owner The owner of tokens
     * @param {String} spender The spender who are able to transfer the tokens
     * @returns {Promise<Number>} Return the amount of remaining tokens allowed to spent
     */
    async Allowance(ctx, owner, spender) {

        // Check contract options are already set first to execute the function
        await this.CheckInitialized(ctx);

        const allowanceKey = ctx.stub.createCompositeKey(allowancePrefix, [owner, spender]);

        const allowanceBytes = await ctx.stub.getState(allowanceKey);
        const allowance = this.isEmpty(allowanceBytes) ? 0 : parseInt(allowanceBytes.toString());

        return allowance;
    }

    // ================== Extended Functions ==========================

    /**
     * Set optional infomation for a token.
     *
     * @param {Context} ctx the transaction context
     * @param {String} name The name of the token
     * @param {String} symbol The symbol of the token
     * @param {String} decimals The decimals of the token
     * @param {String} totalSupply The totalSupply of the token
     */
    async Initialize(ctx, name, symbol, decimals) {
        // Check client authorization
        this.CheckAuthorization(ctx);

        // Check contract options are not already set, client is not authorized to change them once intitialized
        const nameBytes = await ctx.stub.getState(nameKey);
        if (!this.isEmpty(nameBytes)) {
            throw new Error('contract options are already set, client is not authorized to change them');
        }

        await ctx.stub.putState(nameKey, Buffer.from(name));
        await ctx.stub.putState(symbolKey, Buffer.from(symbol));
        await ctx.stub.putState(decimalsKey, Buffer.from(decimals));
        await ctx.stub.putState(totalSupplyKey, Buffer.from('0'));

        console.log(`name: ${name}, symbol: ${symbol}, decimals: ${decimals}`);
        return true;
    }

    /**
     * Mint creates new tokens and adds them to minter's account balance
     *
     * @param {Context} ctx the transaction context
     * @param {Number} amount amount of tokens to be minted
     * @returns {Promise<Boolean>} Return whether the mint was successful or not
     */
    async Mint(ctx, amount) {

        // Check contract options are already set first to execute the function
        await this.CheckInitialized(ctx);

        // Check minter authorization
        this.CheckAuthorization(ctx);

        const minter = this.ClientAccountID(ctx);
        await this._update(ctx, '', minter, amount);

        return true;
    }

    /**
     * Burn redeem tokens from burner's account balance
     *
     * @param {Context} ctx the transaction context
     * @param {Number} amount amount of tokens to be burned
     * @returns {Promise<Boolean>} Return whether the burn was successful or not
     */
    async Burn(ctx, amount) {

        // Check contract options are already set first to execute the function
        await this.CheckInitialized(ctx);

        // Check burner authorization
        this.CheckAuthorization(ctx);

        const burner = this.ClientAccountID(ctx);
        await this._update(ctx, burner, '', amount);

        return true;
    }

    /**
     * ClientAccountBalance returns the balance of the requesting client's account.
     *
     * @param {Context} ctx the transaction context
     * @returns {Promise<Number>} Returns the account balance
     */
    async ClientAccountBalance(ctx) {

        // Check contract options are already set first to execute the function
        await this.CheckInitialized(ctx);

        // Get ID of submitting client identity
        const clientAccountID = this.ClientAccountID(ctx);

        return await this.BalanceOf(ctx, clientAccountID);
    }

    /**
     * ClientAccountID returns the id of the requesting client's account.
     * In this implementation, the client account ID is the clientId itself.
     * Users can use this function to get their own account id, which they can then give to others as the payment address
     *
     * @param {Context} ctx the transaction context
     * @returns {String} Returns the account id
     */
    ClientAccountID(ctx) {

        // Get ID of submitting client identity
        const clientAccountID = ctx.clientIdentity.getID();
        return clientAccountID;
    }

    /**
     * ClientAccountMSPID returns the MSP id of the requesting client's account.
     * In this implementation, the client account MSP ID is the clientMspId itself.
     *
     * @param {Context} ctx the transaction context
     * @returns {String} Returns the account MSP id
     */
    ClientAccountMSPID(ctx) {

        // Get ID of submitting client identity
        const clientAccountMSPID = ctx.clientIdentity.getMSPID();
        return clientAccountMSPID;
    }

    /**
     * Checks that contract options have been already initialized
     *
     * @param {Context} ctx the transaction context
     */
    async CheckInitialized(ctx) {
        const nameBytes = await ctx.stub.getState(nameKey);
        if (this.isEmpty(nameBytes)) {
            throw new Error('contract options need to be set before calling any function, call Initialize() to initialize contract');
        }
    }

    /**
     * Check client authorization - this sample assumes Org1 is the central banker with privilege to burn tokens
     *
     * @param {Context} ctx the transaction context
     */
    CheckAuthorization(ctx) {
        const clientMSPID = this.ClientAccountMSPID(ctx);
        if (clientMSPID !== 'Org1MSP') {
            throw new Error('client is not authorized');
        }
    }

    /**
     * Transfers a `value` amount of tokens from `from` to `to`, or alternatively mints (or burns) if `from`
     * (or `to`) is the zero address. All customizations to transfers, mints, and burns should be done by overriding
     * this function.
     *
     * @param {Context} ctx the transaction context
     * @param {String} from The sender
     * @param {String} to The recipient
     * @param {Number} value The amount of token to be transferred
     */
    async _update(ctx, from, to, value) {

        // Convert value from string to int
        const valueInt = parseInt(value);

        if (valueInt < 0) { // transfer of 0 is allowed in ERC20, so just validate against negative amounts
            throw new Error('transfer amount cannot be negative');
        }

        let totalSupply = await this.TotalSupply(ctx);
        if (this.isEmpty(from)) {
            // Overflow check required: The rest of the code assumes that totalSupply never overflows
            totalSupply = this.add(totalSupply, valueInt);
        } else {
            // Retrieve the current balance of the sender
            const fromCurrentBalance = await this.BalanceOf(ctx, from);
            // Check if the sender has enough tokens to spend.
            if (fromCurrentBalance < valueInt) {
                throw new Error(`client account ${from} has insufficient funds.`);
            }
            // Overflow not possible: valueInt <= fromCurrentBalance <= totalSupply.
            const fromBalanceKey = ctx.stub.createCompositeKey(balancePrefix, [from]);
            const fromUpdatedBalance = fromCurrentBalance - valueInt;
            await ctx.stub.putState(fromBalanceKey, Buffer.from(fromUpdatedBalance.toString()));
            console.log(`client ${from} balance updated from ${fromCurrentBalance} to ${fromUpdatedBalance}`);
        }
        if (this.isEmpty(to)) {
            // Overflow not possible: valueInt <= totalSupply.
            totalSupply -= valueInt;
        } else {
            // Overflow not possible: toCurrentBalance + valueInt is at most totalSupply
            const toCurrentBalance = await this.BalanceOf(ctx, to);
            const toBalanceKey = ctx.stub.createCompositeKey(balancePrefix, [to]);
            const toUpdatedBalance = toCurrentBalance + valueInt;
            await ctx.stub.putState(toBalanceKey, Buffer.from(toUpdatedBalance.toString()));
            console.log(`recipient ${to} balance updated from ${toCurrentBalance} to ${toUpdatedBalance}`);
        }

        await ctx.stub.putState(totalSupplyKey, Buffer.from(totalSupply.toString()));

        // Emit the Transfer event
        const transferEvent = { from, to, value: valueInt };
        ctx.stub.setEvent('Transfer', Buffer.from(JSON.stringify(transferEvent)));
    }

    /**
     * @param {Context} ctx the transaction context
     * @param {String} owner The owner of tokens
     * @param {String} spender The spender
     * @param {Number} value The amount of token to be transferred
     */
    async _approve(ctx, owner, spender, value) {
        await this._approveEvent(ctx, owner, spender, value, true);
    }

    /**
     * @param {Context} ctx the transaction context
     * @param {String} owner The owner of tokens
     * @param {String} spender The spender
     * @param {Number} value The amount of token to be transferred
     * @param {Boolean} emitEvent Whether to emit event
     */
    async _approveEvent(ctx, owner, spender, value, emitEvent) {
        if (this.isEmpty(owner)) {
            throw new Error('invalid approver');
        }
        if (this.isEmpty(spender)) {
            throw new Error('invalid spender');
        }

        const allowanceKey = ctx.stub.createCompositeKey(allowancePrefix, [owner, spender]);
        await ctx.stub.putState(allowanceKey, Buffer.from(value.toString()));

        // Emit the Approval event
        if (emitEvent) {
            const valueInt = parseInt(value);
            const approvalEvent = { owner, spender, value: valueInt };
            ctx.stub.setEvent('Approval', Buffer.from(JSON.stringify(approvalEvent)));
        }
    }

    /**
     * @param {Context} ctx the transaction context
     * @param {String} owner The owner of tokens
     * @param {String} spender The spender
     * @param {Number} value The amount of token to be transferred
     */
    async _spendAllowance(ctx, owner, spender, value) {

        // Retrieve the allowance of the spender
        const currentAllowance = await this.Allowance(ctx, owner, spender);

        // Convert value from string to int
        const valueInt = parseInt(value);

        // Check if the transferred value is less than the allowance
        if (currentAllowance < valueInt) {
            throw new Error('The spender does not have enough allowance to spend.');
        }
        // Decrease the allowance
        const updatedAllowance = currentAllowance - valueInt;
        await this._approveEvent(ctx, owner, spender, updatedAllowance, false);
        console.log(`spender ${spender} allowance updated from ${currentAllowance} to ${updatedAllowance}`);
    }

    // Return whether the value is empty or not
    isEmpty(value) {
        return (!value || value.length === 0);
    }

    // add two number checking for overflow
    add(a, b) {
        let c = a + b;
        if (a !== c - b || b !== c - a){
            throw new Error(`Math: addition overflow occurred ${a} + ${b}`);
        }
        return c;
    }

    // add two number checking for overflow
    sub(a, b) {
        let c = a - b;
        if (a !== c + b || b !== a - c){
            throw new Error(`Math: subtraction overflow occurred ${a} - ${b}`);
        }
        return c;
    }
}

module.exports = TokenERC20Contract;
