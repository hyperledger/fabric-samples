/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
*/

'use strict';

const { Context } = require('fabric-contract-api');
const { ChaincodeStub, ClientIdentity } = require('fabric-shim');

const { TokenERC20Contract } = require('..');

const chai = require('chai');
const chaiAsPromised = require('chai-as-promised');
const sinon = require('sinon');
const expect = chai.expect;

chai.should();
chai.use(chaiAsPromised);

describe('Chaincode', () => {
    let sandbox;
    let token;
    let ctx;
    let mockStub;
    let mockClientIdentity;

    beforeEach('Sandbox creation', async () => {
        sandbox = sinon.createSandbox();
        token = new TokenERC20Contract('token-erc20');

        ctx = sinon.createStubInstance(Context);
        mockStub = sinon.createStubInstance(ChaincodeStub);
        ctx.stub = mockStub;
        mockClientIdentity = sinon.createStubInstance(ClientIdentity);
        ctx.clientIdentity = mockClientIdentity;

        await token.Initialize(ctx, 'some name', 'some symbol', '2');

        mockStub.putState.resolves('some state');
        mockStub.setEvent.returns('set event');

    });

    afterEach('Sandbox restoration', () => {
        sandbox.restore();
    });

    describe('#TokenName', () => {
        it('should work', async () => {
            mockStub.getState.resolves('some state');

            const response = await token.TokenName(ctx);
            sinon.assert.calledWith(mockStub.getState, 'name');
            expect(response).to.equals('some state');
        });
    });

    describe('#Symbol', () => {
        it('should work', async () => {
            mockStub.getState.resolves('some state');

            const response = await token.Symbol(ctx);
            sinon.assert.calledWith(mockStub.getState, 'symbol');
            expect(response).to.equals('some state');
        });
    });

    describe('#Decimals', () => {
        it('should work', async () => {
            mockStub.getState.resolves(Buffer.from('2'));

            const response = await token.Decimals(ctx);
            sinon.assert.calledWith(mockStub.getState, 'decimals');
            expect(response).to.equals(2);
        });
    });

    describe('#TotalSupply', () => {
        it('should work', async () => {
            mockStub.getState.resolves(Buffer.from('10000'));

            const response = await token.TotalSupply(ctx);
            sinon.assert.calledWith(mockStub.getState, 'totalSupply');
            expect(response).to.equals(10000);
        });
    });

    describe('#BalanceOf', () => {
        it('should work', async () => {
            mockStub.createCompositeKey.returns('balance_Alice');
            mockStub.getState.resolves(Buffer.from('1000'));

            const response = await token.BalanceOf(ctx, 'Alice');
            expect(response).to.equals(1000);
        });
    });

    describe('#_transfer', () => {

        it('should fail when the sender and the receipient are the same', async () => {
            await expect(token._transfer(ctx, 'Alice', 'Alice', '1000'))
                .to.be.rejectedWith(Error, 'cannot transfer to and from same client account');
        });

        it('should fail when the sender does not have enough token', async () => {
            mockStub.createCompositeKey.withArgs('balance', ['Alice']).returns('balance_Alice');
            mockStub.getState.withArgs('balance_Alice').resolves(Buffer.from('500'));

            await expect(token._transfer(ctx, 'Alice', 'Bob', '1000'))
                .to.be.rejectedWith(Error, 'client account Alice has insufficient funds.');
        });

        it('should transfer to a new account when the sender has enough token', async () => {
            mockStub.createCompositeKey.withArgs('balance', ['Alice']).returns('balance_Alice');
            mockStub.getState.withArgs('balance_Alice').resolves(Buffer.from('1000'));

            mockStub.createCompositeKey.withArgs('balance', ['Bob']).returns('balance_Bob');
            mockStub.getState.withArgs('balance_Bob').resolves(null);

            const response = await token._transfer(ctx, 'Alice', 'Bob', '1000');
            sinon.assert.calledWith(mockStub.putState.getCall(0), 'balance_Alice', Buffer.from('0'));
            sinon.assert.calledWith(mockStub.putState.getCall(1), 'balance_Bob', Buffer.from('1000'));
            expect(response).to.equals(true);
        });

        it('should transfer to the existing account when the sender has enough token', async () => {
            mockStub.createCompositeKey.withArgs('balance', ['Alice']).returns('balance_Alice');
            mockStub.getState.withArgs('balance_Alice').resolves(Buffer.from('1000'));

            mockStub.createCompositeKey.withArgs('balance', ['Bob']).returns('balance_Bob');
            mockStub.getState.withArgs('balance_Bob').resolves(Buffer.from('2000'));

            const response = await token._transfer(ctx, 'Alice', 'Bob', '1000');
            sinon.assert.calledWith(mockStub.putState.getCall(0), 'balance_Alice', Buffer.from('0'));
            sinon.assert.calledWith(mockStub.putState.getCall(1), 'balance_Bob', Buffer.from('3000'));
            expect(response).to.equals(true);
        });

    });

    describe('#Transfer', () => {
        it('should work', async () => {
            mockClientIdentity.getID.returns('Alice');
            sinon.stub(token, '_transfer').returns(true);

            const response = await token.Transfer(ctx, 'Bob', '1000');
            const event = { from: 'Alice', to: 'Bob', value: 1000 };
            sinon.assert.calledWith(mockStub.setEvent, 'Transfer', Buffer.from(JSON.stringify(event)));
            expect(response).to.equals(true);
        });
    });

    describe('#TransferFrom', () => {
        it('should fail when the spender is not allowed to spend the token', async () => {
            mockClientIdentity.getID.returns('Charlie');

            mockStub.createCompositeKey.withArgs('allowance', ['Alice', 'Charlie']).returns('allowance_Alice_Charlie');
            mockStub.getState.withArgs('allowance_Alice_Charlie').resolves(Buffer.from('0'));

            await expect(token.TransferFrom(ctx, 'Alice', 'Bob', '1000'))
                .to.be.rejectedWith(Error, 'The spender does not have enough allowance to spend.');
        });

        it('should transfer when the spender is allowed to spend the token', async () => {
            mockClientIdentity.getID.returns('Charlie');

            mockStub.createCompositeKey.withArgs('allowance', ['Alice', 'Charlie']).returns('allowance_Alice_Charlie');
            mockStub.getState.withArgs('allowance_Alice_Charlie').resolves(Buffer.from('3000'));

            sinon.stub(token, '_transfer').returns(true);

            const response = await token.TransferFrom(ctx, 'Alice', 'Bob', '1000');
            sinon.assert.calledWith(mockStub.putState, 'allowance_Alice_Charlie', Buffer.from('2000'));
            const event = { from: 'Alice', to: 'Bob', value: 1000 };
            sinon.assert.calledWith(mockStub.setEvent, 'Transfer', Buffer.from(JSON.stringify(event)));
            expect(response).to.equals(true);
        });
    });

    describe('#Approve', () => {
        it('should work', async () => {
            mockClientIdentity.getID.returns('Dave');
            mockStub.createCompositeKey.returns('allowance_Dave_Eve');

            const response = await token.Approve(ctx, 'Ellen', '1000');
            sinon.assert.calledWith(mockStub.putState, 'allowance_Dave_Eve', Buffer.from('1000'));
            expect(response).to.equals(true);
        });
    });

    describe('#Allowance', () => {
        it('should work', async () => {
            mockStub.createCompositeKey.returns('allowance_Dave_Eve');
            mockStub.getState.resolves(Buffer.from('1000'));

            const response = await token.Allowance(ctx, 'Dave', 'Eve');
            expect(response).to.equals(1000);
        });
    });

    describe('#Initialize', () => {
        it('should work', async () => {
            //we consider it has already been initialized in the before-each statement
            sinon.assert.calledWith(mockStub.putState, 'name', Buffer.from('some name'));
            sinon.assert.calledWith(mockStub.putState, 'symbol', Buffer.from('some symbol'));
            sinon.assert.calledWith(mockStub.putState, 'decimals', Buffer.from('2'));
        });

        it('should failed if called a second time', async () => {
            //we consider it has already been initialized in the before-each statement
            await expect(await token.Initialize(ctx, 'some name', 'some symbol', '2'))
                .to.be.rejectedWith(Error, 'contract options are already set, client is not authorized to change them');
        });
    });

    describe('#Mint', () => {
        it('should add token to a new account and a new total supply', async () => {
            mockClientIdentity.getMSPID.returns('Org1MSP');
            mockClientIdentity.getID.returns('Alice');
            mockStub.createCompositeKey.returns('balance_Alice');
            mockStub.getState.withArgs('balance_Alice').resolves(null);
            mockStub.getState.withArgs('totalSupply').resolves(null);

            const response = await token.Mint(ctx, '1000');
            sinon.assert.calledWith(mockStub.putState.getCall(0), 'balance_Alice', Buffer.from('1000'));
            sinon.assert.calledWith(mockStub.putState.getCall(1), 'totalSupply', Buffer.from('1000'));
            expect(response).to.equals(true);
        });

        it('should add token to the existing account and the existing total supply', async () => {
            mockClientIdentity.getMSPID.returns('Org1MSP');
            mockClientIdentity.getID.returns('Alice');
            mockStub.createCompositeKey.returns('balance_Alice');
            mockStub.getState.withArgs('balance_Alice').resolves(Buffer.from('1000'));
            mockStub.getState.withArgs('totalSupply').resolves(Buffer.from('2000'));

            const response = await token.Mint(ctx, '1000');
            sinon.assert.calledWith(mockStub.putState.getCall(0), 'balance_Alice', Buffer.from('2000'));
            sinon.assert.calledWith(mockStub.putState.getCall(1), 'totalSupply', Buffer.from('3000'));
            expect(response).to.equals(true);
        });

        it('should add token to a new account and the existing total supply', async () => {
            mockClientIdentity.getMSPID.returns('Org1MSP');
            mockClientIdentity.getID.returns('Alice');
            mockStub.createCompositeKey.returns('balance_Alice');
            mockStub.getState.withArgs('balance_Alice').resolves(null);
            mockStub.getState.withArgs('totalSupply').resolves(Buffer.from('2000'));

            const response = await token.Mint(ctx, '1000');
            sinon.assert.calledWith(mockStub.putState.getCall(0), 'balance_Alice', Buffer.from('1000'));
            sinon.assert.calledWith(mockStub.putState.getCall(1), 'totalSupply', Buffer.from('3000'));
            expect(response).to.equals(true);
        });

    });

    describe('#Burn', () => {
        it('should work', async () => {
            mockClientIdentity.getMSPID.returns('Org1MSP');
            mockClientIdentity.getID.returns('Alice');
            mockStub.createCompositeKey.returns('balance_Alice');
            mockStub.getState.withArgs('balance_Alice').resolves(Buffer.from('1000'));
            mockStub.getState.withArgs('totalSupply').resolves(Buffer.from('2000'));

            const response = await token.Burn(ctx, '1000');
            sinon.assert.calledWith(mockStub.putState.getCall(0), 'balance_Alice', Buffer.from('0'));
            sinon.assert.calledWith(mockStub.putState.getCall(1), 'totalSupply', Buffer.from('1000'));
            expect(response).to.equals(true);
        });
    });

    describe('#ClientAccountBalance', () => {
        it('should work', async () => {
            mockClientIdentity.getID.returns('Alice');
            mockStub.createCompositeKey.returns('balance_Alice');
            mockStub.getState.resolves(Buffer.from('1000'));

            const response = await token.ClientAccountBalance(ctx,);
            expect(response).to.equals(1000);
        });
    });

    describe('#ClientAccountID', () => {
        it('should work', async () => {
            mockClientIdentity.getID.returns('x509::{subject DN}::{issuer DN}');

            const response = await token.ClientAccountID(ctx);
            sinon.assert.calledOnce(mockClientIdentity.getID);
            expect(response).to.equals('x509::{subject DN}::{issuer DN}');
        });
    });

});
