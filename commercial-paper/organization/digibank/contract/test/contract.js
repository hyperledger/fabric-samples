/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
*/
'use strict';

const Chaincode = require('../lib/chaincode');
const { Stub } = require('fabric-shim');

require('chai').should();
const sinon = require('sinon');

describe('Chaincode', () => {

    describe('#Init', () => {

        it('should work', async () => {
            const cc = new Chaincode();
            const stub = sinon.createStubInstance(Stub);
            stub.getFunctionAndParameters.returns({ fcn: 'initFunc', params: [] });
            const res = await cc.Init(stub);
            res.status.should.equal(Stub.RESPONSE_CODE.OK);
        });

    });

    describe('#Invoke', async () => {

        it('should work', async () => {
            const cc = new Chaincode();
            const stub = sinon.createStubInstance(Stub);
            stub.getFunctionAndParameters.returns({ fcn: 'initFunc', params: [] });
            let res = await cc.Init(stub);
            res.status.should.equal(Stub.RESPONSE_CODE.OK);
            stub.getFunctionAndParameters.returns({ fcn: 'invokeFunc', params: [] });
            res = await cc.Invoke(stub);
            res.status.should.equal(Stub.RESPONSE_CODE.OK);
        });

    });

});
