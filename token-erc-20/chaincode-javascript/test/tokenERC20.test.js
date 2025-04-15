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
    let token;
    let ctx;
    let mockStub;
    let mockClientIdentity;

    beforeEach(async () => {
        token = new TokenERC20Contract();

        ctx = sinon.createStubInstance(Context);
        mockStub = sinon.createStubInstance(ChaincodeStub);
        ctx.stub = mockStub;
        mockClientIdentity = sinon.createStubInstance(ClientIdentity);
        ctx.clientIdentity = mockClientIdentity;

        mockClientIdentity.getMSPID.returns('Org1MSP');

        await token.Initialize(ctx, 'some name', 'some symbol', '2');
        mockStub.getState.withArgs('name').resolves(Buffer.from('some name'));
        mockStub.getState.withArgs('symbol').resolves(Buffer.from('some symbol'));
        mockStub.getState.withArgs('decimals').resolves(Buffer.from('2'));
        mockStub.getState.withArgs('totalSupply').resolves(Buffer.from('0'));
        console.log('Initialized');
    });

    describe('#TokenName', () => {
        it('should work', async () => {
            const response = await token.TokenName(ctx);
            sinon.assert.calledWith(mockStub.getState, 'name');
            expect(response).to.equals('some name');
        });
    });

    describe('#Symbol', () => {
        it('should work', async () => {
            const response = await token.Symbol(ctx);
            sinon.assert.calledWith(mockStub.getState, 'symbol');
            expect(response).to.equals('some symbol');
        });
    });

    describe('#Decimals', () => {
        it('should work', async () => {
            const response = await token.Decimals(ctx);
            sinon.assert.calledWith(mockStub.getState, 'decimals');
            expect(response).to.equals(2);
        });
    });

    describe('#TotalSupply', () => {
        it('should work', async () => {
            const response = await token.TotalSupply(ctx);
            sinon.assert.calledWith(mockStub.getState, 'totalSupply');
            expect(response).to.equals(0);
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
            const response = token._transfer(ctx, 'Alice', 'Alice', '1000');
            await expect(response).to.be.rejectedWith(Error, 'cannot transfer to and from same client account');
        });

        it('should fail when the sender does not have enough token', async () => {
            mockStub.createCompositeKey.withArgs('balance', ['Alice']).returns('balance_Alice');
            mockStub.getState.withArgs('balance_Alice').resolves(Buffer.from('500'));

            const response = token._transfer(ctx, 'Alice', 'Bob', '1000');
            await expect(response).to.be.rejectedWith(Error, 'client account Alice has insufficient funds.');
        });

        it('should transfer to a new account when the sender has enough token', async () => {
            mockStub.createCompositeKey.withArgs('balance', ['Alice']).returns('balance_Alice');
            mockStub.getState.withArgs('balance_Alice').resolves(Buffer.from('1000'));

            mockStub.createCompositeKey.withArgs('balance', ['Bob']).returns('balance_Bob');
            mockStub.getState.withArgs('balance_Bob').resolves(null);

            await token._transfer(ctx, 'Alice', 'Bob', '1000');
            sinon.assert.calledWith(mockStub.putState.getCall(4), 'balance_Alice', Buffer.from('0'));
            sinon.assert.calledWith(mockStub.putState.getCall(5), 'balance_Bob', Buffer.from('1000'));
        });

        it('should transfer to the existing account when the sender has enough token', async () => {
            mockStub.createCompositeKey.withArgs('balance', ['Alice']).returns('balance_Alice');
            mockStub.getState.withArgs('balance_Alice').resolves(Buffer.from('1000'));

            mockStub.createCompositeKey.withArgs('balance', ['Bob']).returns('balance_Bob');
            mockStub.getState.withArgs('balance_Bob').resolves(Buffer.from('2000'));

            await token._transfer(ctx, 'Alice', 'Bob', '1000');
            sinon.assert.calledWith(mockStub.putState.getCall(4), 'balance_Alice', Buffer.from('0'));
            sinon.assert.calledWith(mockStub.putState.getCall(5), 'balance_Bob', Buffer.from('3000'));
        });

    });

    describe('#Transfer', () => {
        it('should work', async () => {
            mockClientIdentity.getID.returns('Alice');

            mockStub.createCompositeKey.withArgs('balance', ['Alice']).returns('balance_Alice');
            mockStub.getState.withArgs('balance_Alice').resolves(Buffer.from('1000'));

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

            const response = token.TransferFrom(ctx, 'Alice', 'Bob', '1000');
            await expect(response).to.be.rejectedWith(Error, 'The spender does not have enough allowance to spend.');
        });

        it('should transfer when the spender is allowed to spend the token', async () => {
            mockClientIdentity.getID.returns('Charlie');

            mockStub.createCompositeKey.withArgs('balance', ['Alice']).returns('balance_Alice');
            mockStub.getState.withArgs('balance_Alice').resolves(Buffer.from('1000'));

            mockStub.createCompositeKey.withArgs('allowance', ['Alice', 'Charlie']).returns('allowance_Alice_Charlie');
            mockStub.getState.withArgs('allowance_Alice_Charlie').resolves(Buffer.from('3000'));

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
            // We consider it has already been initialized in the before-each statement
            sinon.assert.calledWith(mockStub.putState, 'name', Buffer.from('some name'));
            sinon.assert.calledWith(mockStub.putState, 'symbol', Buffer.from('some symbol'));
            sinon.assert.calledWith(mockStub.putState, 'decimals', Buffer.from('2'));
        });

        it('should failed if called a second time', async () => {
            // We consider it has already been initialized in the before-each statement
            const response = token.Initialize(ctx, 'some name', 'some symbol', '2');
            await expect(response).to.be.rejectedWith(Error, 'contract options are already set, client is not authorized to change them');
        });
    });

    describe('#Mint', () => {
        it('should add token to a new account and a new total supply', async () => {
            mockClientIdentity.getID.returns('Alice');
            mockStub.createCompositeKey.returns('balance_Alice');

            const response = await token.Mint(ctx, '1000');
            sinon.assert.calledWith(mockStub.putState.getCall(4), 'balance_Alice', Buffer.from('1000'));
            sinon.assert.calledWith(mockStub.putState.getCall(5), 'totalSupply', Buffer.from('1000'));
            expect(response).to.equals(true);
        });

        it('should add token to the existing account and the existing total supply', async () => {
            mockClientIdentity.getID.returns('Alice');
            mockStub.createCompositeKey.returns('balance_Alice');
            mockStub.getState.withArgs('balance_Alice').resolves(Buffer.from('1000'));
            mockStub.getState.withArgs('totalSupply').resolves(Buffer.from('2000'));

            const response = await token.Mint(ctx, '1000');
            sinon.assert.calledWith(mockStub.putState.getCall(4), 'balance_Alice', Buffer.from('2000'));
            sinon.assert.calledWith(mockStub.putState.getCall(5), 'totalSupply', Buffer.from('3000'));
            expect(response).to.equals(true);
        });

        it('should add token to a new account and the existing total supply', async () => {
            mockClientIdentity.getID.returns('Alice');
            mockStub.createCompositeKey.returns('balance_Alice');
            mockStub.getState.withArgs('balance_Alice').resolves(null);
            mockStub.getState.withArgs('totalSupply').resolves(Buffer.from('2000'));

            const response = await token.Mint(ctx, '1000');
            sinon.assert.calledWith(mockStub.putState.getCall(4), 'balance_Alice', Buffer.from('1000'));
            sinon.assert.calledWith(mockStub.putState.getCall(5), 'totalSupply', Buffer.from('3000'));
            expect(response).to.equals(true);
        });

    });

    describe('#Burn', () => {
        it('should work', async () => {
            mockClientIdentity.getID.returns('Alice');
            mockStub.createCompositeKey.returns('balance_Alice');
            mockStub.getState.withArgs('balance_Alice').resolves(Buffer.from('1000'));
            mockStub.getState.withArgs('totalSupply').resolves(Buffer.from('2000'));

            const response = await token.Burn(ctx, '1000');
            sinon.assert.calledWith(mockStub.putState.getCall(4), 'balance_Alice', Buffer.from('0'));
            sinon.assert.calledWith(mockStub.putState.getCall(5), 'totalSupply', Buffer.from('1000'));
            expect(response).to.equals(true);
        });
    });

    describe('#ClientAccountBalance', () => {
        it('should work', async () => {
            mockClientIdentity.getID.returns('Alice');
            mockStub.createCompositeKey.returns('balance_Alice');
            mockStub.getState.resolves(Buffer.from('1000'));

            const response = await token.ClientAccountBalance(ctx);
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

    describe('#ClientAccountMSPID', () => {
        it('should work', async () => {
            const response = await token.ClientAccountMSPID(ctx);
            sinon.assert.calledTwice(mockClientIdentity.getMSPID);
            expect(response).to.equals('Org1MSP');
        });
    });

    describe('#CheckAuthorization', () => {
        it('should work', async () => {
            await token.CheckAuthorization(ctx);
        });

        it('should failed if called by not Org1MSP', () => {
            mockClientIdentity.getMSPID.returns('Org2MSP');
            expect(() => token.CheckAuthorization(ctx)).to.throw(Error, 'client is not authorized');
        });
    });

});
