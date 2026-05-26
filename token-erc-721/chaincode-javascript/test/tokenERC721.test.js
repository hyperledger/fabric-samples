/*
SPDX-License-Identifier: Apache-2.0
*/

'use strict';

const { Context } = require('fabric-contract-api');
const { ChaincodeStub, ClientIdentity } = require('fabric-shim');

const { tokenERC721Contract } = require('..');

const chai = require('chai');
const chaiAsPromised = require('chai-as-promised');
const sinon = require('sinon');
const expect = chai.expect;

chai.should();
chai.use(chaiAsPromised);

class MockIterator {
    constructor(data) {
        this.array = data;
        this.cur = 0;
    }
    next() {
        if (this.cur < this.array.length) {
            const value = this.array[this.cur];
            this.cur++;
            return Promise.resolve({ value: value });
        } else {
            return Promise.resolve({ done: true });
        }
    }
    close() {
        return Promise.resolve();
    }
}

describe('Chaincode', () => {
    let sandbox;
    let token;
    let ctx;
    let mockStub;
    let mockClientIdentity;

    beforeEach('Sandbox creation', () => {
        sandbox = sinon.createSandbox();
        token = new tokenERC721Contract('token-erc721');

        ctx = sinon.createStubInstance(Context);
        mockStub = sinon.createStubInstance(ChaincodeStub);
        ctx.stub = mockStub;
        mockClientIdentity = sinon.createStubInstance(ClientIdentity);
        ctx.clientIdentity = mockClientIdentity;
    });

    afterEach('Sandbox restoration', () => {
        sandbox.restore();
    });

    describe('#BalanceOf', () => {
        it('should work', async () => {
            const mockResponse = [
                { key: 'balance_Alice_101', value: Buffer.from('\u0000') },
                { key: 'balance_Alice_102', value: Buffer.from('\u0000') }
            ];
            mockStub.getStateByPartialCompositeKey.resolves(new MockIterator(mockResponse));

            const response = await token.BalanceOf(ctx, 'Alice');
            expect(response).to.equals(2);
        });
    });

    describe('#OwnerOf', () => {
        it('should work', async () => {
            const nft = {
                tokenId: 101,
                owner: 'Alice'
            };
            sinon.stub(token, '_readNFT').resolves(nft);

            const response = await token.OwnerOf(ctx, '101');
            expect(response).to.equal('Alice');
        });
    });

    describe('#TransferFrom', () => {
        let currentNft;
        let updatedNft;

        beforeEach('Set up test parameters', () => {
            currentNft = {
                tokenId: 101,
                owner: 'Alice',
                approved: 'Charlie'
            };

            updatedNft = {
                tokenId: 101,
                owner: 'Bob',
                approved: ''
            };

            sinon.stub(token, '_readNFT').resolves(currentNft);
            mockStub.createCompositeKey.withArgs('nft', ['101']).returns('nft_101');
            mockStub.createCompositeKey.withArgs('balance', ['Alice', '101']).returns('balance_Alice_101');
            mockStub.createCompositeKey.withArgs('balance', ['Bob', '101']).returns('balance_Bob_101');
        });

        it('should work when a sender is the current owner', async () => {
            mockClientIdentity.getID.returns('Alice');
            sinon.stub(token, 'IsApprovedForAll').resolves(false);

            const response = await token.TransferFrom(ctx, 'Alice', 'Bob', '101');
            sinon.assert.calledWith(mockStub.putState, 'nft_101', Buffer.from(JSON.stringify(updatedNft)));
            expect(response).to.equals(true);
        });

        it('should work when a sender is the approved client for this token', async () => {
            mockClientIdentity.getID.returns('Charlie');
            sinon.stub(token, 'IsApprovedForAll').resolves(false);

            const response = await token.TransferFrom(ctx, 'Alice', 'Bob', '101');
            sinon.assert.calledWith(mockStub.putState, 'nft_101', Buffer.from(JSON.stringify(updatedNft)));
            expect(response).to.equals(true);
        });

        it('should work when a sender is an authorized operator', async () => {
            mockClientIdentity.getID.returns('Dave');
            sinon.stub(token, 'IsApprovedForAll').resolves(true);

            const response = await token.TransferFrom(ctx, 'Alice', 'Bob', '101');
            sinon.assert.calledWith(mockStub.putState, 'nft_101', Buffer.from(JSON.stringify(updatedNft)));
            expect(response).to.equals(true);
        });

        it('should throw an error when a sender is invalid', async () => {
            mockClientIdentity.getID.returns('Eve');
            sinon.stub(token, 'IsApprovedForAll').resolves(false);

            await expect(token.TransferFrom(ctx, 'Alice', 'Bob', '101'))
                .to.be.rejectedWith(Error, 'The sender is not allowed to transfer the non-fungible token');
        });

        it('should throw an error when a current owner does not match', async () => {
            mockClientIdentity.getID.returns('Dave');
            sinon.stub(token, 'IsApprovedForAll').resolves(true);

            await expect(token.TransferFrom(ctx, 'Charlie', 'Bob', '101'))
                .to.be.rejectedWith(Error, 'The from is not the current owner.');
        });

    });

    describe('#Approve', () => {
        it('should work with the token owner', async () => {
            mockClientIdentity.getID.returns('Alice');
            const currentNft = {
                tokenId: 101,
                owner: 'Alice',
            };
            sinon.stub(token, '_readNFT').resolves(currentNft);
            sinon.stub(token, 'IsApprovedForAll').resolves(false);
            mockStub.createCompositeKey.withArgs('nft', ['101']).returns('nft_101');

            const response = await token.Approve(ctx, 'Bob', '101');
            const updatedNft = {
                tokenId: 101,
                owner: 'Alice',
                approved: 'Bob'
            };
            sinon.assert.calledWith(mockStub.putState, 'nft_101', Buffer.from(JSON.stringify(updatedNft)));
            expect(response).to.equals(true);
        });
    });

    describe('#SetApprovalForAll', () => {
        it('should work', async () => {
            mockClientIdentity.getID.returns('Alice');
            mockStub.createCompositeKey.withArgs('approval', ['Alice', 'Bob']).returns('approval_Alice_Bob');

            const response = await token.SetApprovalForAll(ctx, 'Bob', true);
            const approval = {
                owner: 'Alice',
                operator: 'Bob',
                approved: true
            };
            sinon.assert.calledWith(mockStub.putState, 'approval_Alice_Bob', Buffer.from(JSON.stringify(approval)));
            expect(response).to.equals(true);
        });
    });

    describe('#GetApproved', () => {
        it('should work', async () => {
            const nft = {
                tokenId: 101,
                owner: 'Alice',
                approved: 'Bob',
            };
            sinon.stub(token, '_readNFT').resolves(nft);

            const response = await token.GetApproved(ctx, '101');
            expect(response).to.equals('Bob');
        });
    });

    describe('#IsApprovedForAll', () => {
        it('should work', async () => {
            mockStub.createCompositeKey.withArgs('approval', ['Alice', 'Bob']).returns('approval_Alice_Bob');
            const approval = {
                owner: 'Alice',
                operator: 'Bob',
                approved: true
            };
            mockStub.getState.withArgs('approval_Alice_Bob').resolves(Buffer.from(JSON.stringify(approval)));

            const response = await token.IsApprovedForAll(ctx, 'Alice', 'Bob');
            expect(response).to.equals(true);
        });
    });

    describe('#Name', () => {
        it('should work', async () => {
            mockStub.getState.resolves('some state');

            const response = await token.Name(ctx);
            expect(response).to.equals('some state');
        });
    });

    describe('#Symbol', () => {
        it('should work', async () => {
            mockStub.getState.resolves('some state');

            const response = await token.Symbol(ctx);
            expect(response).to.equals('some state');
        });
    });

    describe('#TokenURI', () => {
        it('should work', async () => {
            const nft = {
                tokenId: 101,
                owner: 'Alice',
                tokenURI: 'DummyURI'
            };
            sinon.stub(token, '_readNFT').resolves(nft);

            const response = await token.TokenURI(ctx, '101');
            expect(response).to.equal('DummyURI');
        });
    });

    describe('#TotalSupply', () => {
        it('should work', async () => {
            const mockResponse = [
                { key: 'nft_101', value: Buffer.from(JSON.stringify({ tokenId: 101, owner: 'Alice' })) },
                { key: 'nft_102', value: Buffer.from(JSON.stringify({ tokenId: 102, owner: 'Bob' })) }
            ];
            mockStub.getStateByPartialCompositeKey.resolves(new MockIterator(mockResponse));

            const response = await token.TotalSupply(ctx);
            expect(response).to.equals(2);
        });
    });

    describe('#MintWithTokenURI', () => {
        it('should work with a new token', async () => {
            mockClientIdentity.getMSPID.returns('Org1MSP');
            mockClientIdentity.getID.returns('Alice');
            sinon.stub(token, '_nftExists').resolves(false);
            mockStub.createCompositeKey.withArgs('nft', ['101']).returns('nft_101');
            mockStub.createCompositeKey.withArgs('balance', ['Alice', '101']).returns('balance_Alice_101');

            const response = await token.MintWithTokenURI(ctx, '101', 'DummyURI');
            const nft = { tokenId: 101, owner: 'Alice', tokenURI: 'DummyURI'};
            sinon.assert.calledWith(mockStub.putState.getCall(0), 'nft_101', Buffer.from(JSON.stringify(nft)));
            sinon.assert.calledWith(mockStub.putState.getCall(1), 'balance_Alice_101', Buffer.from('\u0000'));
            expect(response).to.deep.equal(nft);
        });

        it('should throw an error when a tokenId alreay exists', async () => {
            mockClientIdentity.getMSPID.returns('Org1MSP');
            mockClientIdentity.getID.returns('Alice');
            sinon.stub(token, '_nftExists').resolves(true);

            await expect(token.MintWithTokenURI(ctx, 'mytoken1', 'DummyURI'))
                .to.be.rejectedWith(Error, 'The token mytoken1 is already minted.');
        });

        it('should throw an error when a tokenId is not an integer', async () => {
            mockClientIdentity.getMSPID.returns('Org1MSP');
            mockClientIdentity.getID.returns('Alice');
            sinon.stub(token, '_nftExists').resolves(false);

            await expect(token.MintWithTokenURI(ctx, 'mytoken1', 'DummyURI'))
                .to.be.rejectedWith(Error, 'The tokenId mytoken1 is invalid. tokenId must be an integer');
        });

    });

    describe('#Burn', () => {
        it('should work', async () => {
            mockClientIdentity.getID.returns('Bob');

            const nft = {
                tokenId: 101,
                owner: 'Bob',
            };
            sinon.stub(token, '_readNFT').resolves(nft);

            mockStub.createCompositeKey.withArgs('nft', ['101']).returns('nft_101');
            mockStub.createCompositeKey.withArgs('balance', ['Bob', '101']).returns('balance_Bob_101');

            const response = await token.Burn(ctx, '101');
            sinon.assert.calledWith(mockStub.deleteState.getCall(0), 'nft_101');
            sinon.assert.calledWith(mockStub.deleteState.getCall(1), 'balance_Bob_101');
            expect(response).to.equals(true);
        });
    });

    describe('#_readNFT', () => {
        it('should work', async () => {
            mockStub.createCompositeKey.returns('nft_101');
            const nft = {
                tokenId: 101,
                owner: 'Alice',
                approved: 'Bob',
                tokenURI: 'DummyURI'
            };
            mockStub.getState.resolves(Buffer.from(JSON.stringify(nft)));

            const response = await token._readNFT(ctx, '101');
            expect(response).to.deep.equal(nft);
        });
    });

});
