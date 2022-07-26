/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { Context, Contract } from 'fabric-contract-api';
import { Car } from './car';
import {Ruleset} from './ruleset';
import {Validation} from './validation';

export class FabCar extends Contract {

    public currentContractNumber = 0;
    public async validateAge(ctx: Context, age: string) {
        const rulesets: Ruleset[] = [
            {
                minimumAge: 16,
                name: 'First ruleset',
            },
            {
                minimumAge: 18,
                name: 'Second ruleset',
            },
        ];
        const validation: Validation = new Validation();
        validation.id = this.getNewId();
        const minimumAge: number = Math.max(rulesets[0].minimumAge, rulesets[1].minimumAge);
        const ageToValidate = Number(age);
        // Validate age
        validation.isValid = (minimumAge <= ageToValidate);

        await ctx.stub.putState('VALIDATION' + validation.id, Buffer.from(JSON.stringify(validation.toString())));
        console.log('Added <--> ', validation);

    }
    public async initLedger(ctx: Context) {
        console.log('============= START : Initialize Ledger ===========');
        console.info('============= END : Initialize Ledger ===========');
    }

    public async queryAllValidations(ctx: Context): Promise<string> {
        const startKey = '';
        const endKey = '';
        const allResults = [];

        for await (const {key, value} of ctx.stub.getStateByRange(startKey, endKey)) {
            const strValue = Buffer.from(value).toString('utf8');
            let record;
            try {
                record = JSON.parse(strValue);
            } catch (err) {
                console.log(err);
                record = strValue;
            }
            allResults.push({ Key: key, Record: record });
        }
        console.info(allResults);
        return JSON.stringify(allResults);
    }
    public async getValidationById(ctx: Context, validationId: string): Promise<string> {
        const validationrAsBytes = await ctx.stub.getState(validationId); // get the validation from chaincode state
        if (!validationrAsBytes || validationrAsBytes.length === 0) {
            throw new Error(`${validationId} does not exist`);
        }
        console.log(validationrAsBytes.toString());
        return validationrAsBytes.toString();
    }
    private getNewId(): number {
        this.currentContractNumber++;
        return this.currentContractNumber;
    }

}
