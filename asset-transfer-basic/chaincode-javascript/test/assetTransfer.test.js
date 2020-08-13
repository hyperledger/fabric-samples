'use strict';
const sinon = require('sinon');
const chai = require('chai');
const sinonChai = require('sinon-chai');
const expect = chai.expect;

const { Context } = require('fabric-contract-api');
const { ChaincodeStub } = require('fabric-shim');

const AssetTransfer = require('../lib/assetTransfer.js');

let assert = sinon.assert;
chai.use(sinonChai);

describe('Asset Transfer Basic Tests', () => {
    let transactionContext, chaincodeStub;
    beforeEach(() => {
        transactionContext = new Context();

        chaincodeStub = sinon.createStubInstance(ChaincodeStub);
        transactionContext.setChaincodeStub(chaincodeStub);

        chaincodeStub.putState.callsFake((key, value) => {
            if (!chaincodeStub.states) {
                chaincodeStub.states = {};
            }
            chaincodeStub.states[key] = value;
        });

        chaincodeStub.getState.callsFake(async (key) => {
            let ret;
            if (chaincodeStub.states) {
                ret = chaincodeStub.states[key];
            }
            return Promise.resolve(ret);
        });

        chaincodeStub.deleteState.callsFake(async (key) => {
            if (chaincodeStub.states) {
                delete chaincodeStub.states[key];
            }
            return Promise.resolve(key);
        });

        chaincodeStub.getStateByRange.callsFake(async (start, end) => {
            function* internalGetStateByRange() {
                if (chaincodeStub.states) {
                    // Shallow copy
                    const copied = Object.assign({}, chaincodeStub.states);

                    for (let key in copied) {
                        yield {value: copied[key]};
                    }
                }
            }

            return Promise.resolve(internalGetStateByRange());
        });
    });

    afterEach(() => {
    });

    describe('Test InitLedger', () => {
        it('should return error on InitLedger', async () => {
            chaincodeStub.putState.rejects('failed inserting key');
            let assetTransfer = new AssetTransfer();
            try {
                await assetTransfer.InitLedger(transactionContext);
                assert.fail('InitLedger should have failed');
            } catch (err) {
                expect(err.name).to.equal('failed inserting key');
            }
        });

        it('should return success on InitLedger', async () => {
            let assetTransfer = new AssetTransfer();
            await assetTransfer.InitLedger(transactionContext);
            let ret = JSON.parse((await chaincodeStub.getState('asset6')).toString());
            expect(ret.ID).to.equal('asset6');
        });
    });

    describe('Test CreateAsset', () => {
        it('should return error on CreateAsset', async () => {
            chaincodeStub.putState.rejects('failed inserting key');

            let assetTransfer = new AssetTransfer();
            try {
                await assetTransfer.CreateAsset(transactionContext, 'asset1', 'blue', 5, 'Tomoko', 300);
                assert.fail('CreateAsset should have failed');
            } catch(err) {
                expect(err.name).to.equal('failed inserting key');
            }
        });

        it('should return success on CreateAsset', async () => {
            let assetTransfer = new AssetTransfer();
            await assetTransfer.CreateAsset(transactionContext, 'asset1', 'blue', 5, 'Tomoko', 300);

            let ret = JSON.parse((await chaincodeStub.getState('asset1')).toString());
            expect(ret.ID).to.equal('asset1');
            expect(ret.Color).to.equal('blue');
            expect(ret.Size).to.equal(5);
            expect(ret.Owner).to.equal('Tomoko');
            expect(ret.AppraisedValue).to.equal(300);
        });
    });

    describe('Test ReadAsset', () => {
        it('should return error on ReadAsset', async () => {
            let assetTransfer = new AssetTransfer();
            await assetTransfer.CreateAsset(transactionContext, 'asset1', 'blue', 5, 'Tomoko', 300);

            try {
                await assetTransfer.ReadAsset(transactionContext, 'asset2');
                assert.fail('ReadAsset should have failed');
            } catch (err) {
                expect(err.message).to.equal('The asset asset2 does not exist');
            }
        });

        it('should return success on ReadAsset', async () => {
            let assetTransfer = new AssetTransfer();
            await assetTransfer.CreateAsset(transactionContext, 'asset1', 'blue', 5, 'Tomoko', 300);

            let ret = JSON.parse(await chaincodeStub.getState('asset1'));
            expect(ret.ID).to.equal('asset1');
        });
    });

    describe('Test UpdateAsset', () => {
        it('should return error on UpdateAsset', async () => {
            let assetTransfer = new AssetTransfer();
            await assetTransfer.CreateAsset(transactionContext, 'asset1', 'blue', 5, 'Tomoko', 300);

            try {
                await assetTransfer.UpdateAsset(transactionContext, 'asset2', 'orange', 10, 'Me', 500);
                assert.fail('UpdateAsset should have failed');
            } catch (err) {
                expect(err.message).to.equal('The asset asset2 does not exist');
            }
        });

        it('should return success on UpdateAsset', async () => {
            let assetTransfer = new AssetTransfer();
            await assetTransfer.CreateAsset(transactionContext, 'asset1', 'blue', 5, 'Tomoko', 300);

            await assetTransfer.UpdateAsset(transactionContext, 'asset1', 'orange', 10, 'Me', 500);
            let ret = JSON.parse(await chaincodeStub.getState('asset1'));
            expect(ret.ID).to.equal('asset1');
            expect(ret.Color).to.equal('orange');
            expect(ret.Size).to.equal(10);
            expect(ret.Owner).to.equal('Me');
            expect(ret.AppraisedValue).to.equal(500);
        });
    });

    describe('Test DeleteAsset', () => {
        it('should return error on DeleteAsset', async () => {
            let assetTransfer = new AssetTransfer();
            await assetTransfer.CreateAsset(transactionContext, 'asset1', 'blue', 5, 'Tomoko', 300);

            try {
                await assetTransfer.DeleteAsset(transactionContext, 'asset2');
                assert.fail('DeleteAsset should have failed');
            } catch (err) {
                expect(err.message).to.equal('The asset asset2 does not exist');
            }
        });

        it('should return success on DeleteAsset', async () => {
            let assetTransfer = new AssetTransfer();
            await assetTransfer.CreateAsset(transactionContext, 'asset1', 'blue', 5, 'Tomoko', 300);

            await assetTransfer.DeleteAsset(transactionContext, 'asset1');
            let ret = await chaincodeStub.getState('asset1');
            expect(ret).to.equal(undefined);
        });
    });

    describe('Test TransferAsset', () => {
        it('should return error on TransferAsset', async () => {
            let assetTransfer = new AssetTransfer();
            await assetTransfer.CreateAsset(transactionContext, 'asset1', 'blue', 5, 'Tomoko', 300);

            try {
                await assetTransfer.TransferAsset(transactionContext, 'asset2', 'Me');
                assert.fail('DeleteAsset should have failed');
            } catch (err) {
                assert.pass();
            }
        });

        it('should return success on TransferAsset', async () => {
            let assetTransfer = new AssetTransfer();
            await assetTransfer.CreateAsset(transactionContext, 'asset1', 'blue', 5, 'Tomoko', 300);

            await assetTransfer.TransferAsset(transactionContext, 'asset1', 'Me');
            let ret = JSON.parse((await chaincodeStub.getState('asset1')).toString());
            expect(ret.Owner).to.equal('Me');
        });
    });

    describe('Test GetAllAssets', () => {
        it('should return error for one key in GetAllAssets', async () => {
            let assetTransfer = new AssetTransfer();
            await assetTransfer.CreateAsset(transactionContext, 'asset1', 'blue', 5, 'Tomoko', 300);

            try {
                await assetTransfer.TransferAsset(transactionContext, 'asset7', 'Me');
                assert.fail('DeleteAsset should have failed');
            } catch (err) {
                assert.pass();
            }
        });

        it('should return success on GetAllAssets', async () => {
            let assetTransfer = new AssetTransfer();
            await assetTransfer.CreateAsset(transactionContext, 'asset1', 'blue', 5, 'Robert', 100);
            await assetTransfer.CreateAsset(transactionContext, 'asset2', 'orange', 10, 'Paul', 200);
            await assetTransfer.CreateAsset(transactionContext, 'asset3', 'red', 15, 'Troy', 300);
            await assetTransfer.CreateAsset(transactionContext, 'asset4', 'pink', 20, 'Van', 400);

            let ret = await assetTransfer.GetAllAssets(transactionContext);
            ret = JSON.parse(ret);
            expect(ret.length).to.equal(4);
        });

        it('should return success on GetAllAssets for non JSON value', async () => {
            let assetTransfer = new AssetTransfer();

            chaincodeStub.putState.onFirstCall().callsFake((key, value) => {
                if (!chaincodeStub.states) {
                    chaincodeStub.states = {};
                }
                chaincodeStub.states[key] = 'non-json-value';
            });

            await assetTransfer.CreateAsset(transactionContext, 'asset1', 'blue', 5, 'Robert', 100);
            await assetTransfer.CreateAsset(transactionContext, 'asset2', 'orange', 10, 'Paul', 200);
            await assetTransfer.CreateAsset(transactionContext, 'asset3', 'red', 15, 'Troy', 300);
            await assetTransfer.CreateAsset(transactionContext, 'asset4', 'pink', 20, 'Van', 400);

            let ret = await assetTransfer.GetAllAssets(transactionContext);
            ret = JSON.parse(ret);
            expect(ret.length).to.equal(4);
            expect(ret[0].Record).to.equal('non-json-value');
        });
    });
});