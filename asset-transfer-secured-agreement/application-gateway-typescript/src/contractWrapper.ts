/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import { Contract, ProposalOptions } from '@hyperledger/fabric-gateway';
import { TextDecoder } from 'util';

export class ContractWrapper {

    contract?: Contract;
    utf8Decoder: TextDecoder = new TextDecoder();

    public constructor(contract:Contract) {
        this.contract = contract;
    }

    public async submit(transactionName:string, options: ProposalOptions ) {
        await this.contract?.submit(transactionName, options);
        return;
    }

    public async evaluate(transactionName:string, options: ProposalOptions): Promise<string> {
        const resultBytes = await this.contract?.evaluate(transactionName, options);
        const result = this.utf8Decoder.decode(resultBytes);
        return result;
    }

    public async evaluateTransaction(transactionName:string, ...args: (string | Uint8Array)[]): Promise<string> {
        const resultBytes = await this.contract?.evaluateTransaction(transactionName, ...args);
        return this.utf8Decoder.decode(resultBytes);
    }
}