/*
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

const { ChaincodeStub, ClientIdentity } = require('fabric-shim');
const { AssetContract } = require('..');
const winston = require('winston');

const chai = require('chai');
const chaiAsPromised = require('chai-as-promised');
const sinon = require('sinon');
const sinonChai = require('sinon-chai');

chai.should();
chai.use(chaiAsPromised);
chai.use(sinonChai);

class TestContext {

    constructor() {
        this.stub = sinon.createStubInstance(ChaincodeStub);
        this.clientIdentity = sinon.createStubInstance(ClientIdentity);
        this.logger = {
            getLogger: sinon.stub().returns(sinon.createStubInstance(winston.createLogger().constructor)),
            setLevel: sinon.stub(),
        };
    }

}

describe('AssetContract', () => {

    let contract;
    let ctx;

    beforeEach(() => {
        contract = new AssetContract();
        ctx = new TestContext();
        ctx.stub.getState.withArgs('1001').resolves(Buffer.from('{"value":"asset 1001 value"}'));
        ctx.stub.getState.withArgs('1002').resolves(Buffer.from('{"value":"asset 1002 value"}'));
    });

    describe('#assetExists', () => {

        it('should return true for a asset', async () => {
            await contract.assetExists(ctx, '1001').should.eventually.be.true;
        });

        it('should return false for a asset that does not exist', async () => {
            await contract.assetExists(ctx, '1003').should.eventually.be.false;
        });

    });

    describe('#createAsset', () => {

        it('should create a asset', async () => {
            await contract.createAsset(ctx, '1003', 'asset 1003 value');
            ctx.stub.putState.should.have.been.calledOnceWithExactly('1003', Buffer.from('{"value":"asset 1003 value"}'));
        });

        it('should throw an error for a asset that already exists', async () => {
            await contract.createAsset(ctx, '1001', 'myvalue').should.be.rejectedWith(/The asset 1001 already exists/);
        });

    });

    describe('#readAsset', () => {

        it('should return a asset', async () => {
            await contract.readAsset(ctx, '1001').should.eventually.deep.equal({ value: 'asset 1001 value' });
        });

        it('should throw an error for a asset that does not exist', async () => {
            await contract.readAsset(ctx, '1003').should.be.rejectedWith(/The asset 1003 does not exist/);
        });

    });

    describe('#updateAsset', () => {

        it('should update a asset', async () => {
            await contract.updateAsset(ctx, '1001', 'asset 1001 new value');
            ctx.stub.putState.should.have.been.calledOnceWithExactly('1001', Buffer.from('{"value":"asset 1001 new value"}'));
        });

        it('should throw an error for a asset that does not exist', async () => {
            await contract.updateAsset(ctx, '1003', 'asset 1003 new value').should.be.rejectedWith(/The asset 1003 does not exist/);
        });

    });

    describe('#deleteAsset', () => {

        it('should delete a asset', async () => {
            await contract.deleteAsset(ctx, '1001');
            ctx.stub.deleteState.should.have.been.calledOnceWithExactly('1001');
        });

        it('should throw an error for a asset that does not exist', async () => {
            await contract.deleteAsset(ctx, '1003').should.be.rejectedWith(/The asset 1003 does not exist/);
        });

    });

});
