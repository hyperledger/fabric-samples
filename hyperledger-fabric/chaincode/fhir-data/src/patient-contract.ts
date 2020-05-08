
import { Context, Contract } from 'fabric-contract-api';
import merge from 'ts-deepmerge';
import { Patient } from './patient';

export class PatientContract extends Contract {

    public async initLedger(ctx: Context) {
        console.info('============= START : Initialize Ledger ===========');
        console.info('============= END : Initialize Ledger ===========');
    }

    // GET patient by id
    public async queryPatient(ctx: Context, id: string): Promise<string> {
        const patientBytes = await ctx.stub.getState(id);
        if (!patientBytes || patientBytes.length === 0) {
            throw new Error(`Patient with id '${id}' does not exist`);
        }
        console.log(patientBytes.toString());
        return patientBytes.toString();
    }

    // POST patient
    public async addPatient(ctx: Context, id: string, patientStr: string) {
        console.info('============= START : addPatient ===========');
        const patientBytes = await ctx.stub.getState(id);
        if (patientBytes && patientBytes.length > 0) {
            throw new Error(`Patient with id '${id}' exists; use updatePatient instead.`);
        }

        const patient: Patient = JSON.parse(patientStr);
        console.info(`addPatient: received id: '${id}' patient: ` + JSON.stringify(patient));

        await ctx.stub.putState(id, Buffer.from(JSON.stringify(patient)));
        console.info('============= END : addPatient ===========');
    }

    // PUT patient
    public async replacePatient(ctx: Context, id: string, patientStr: string) {
        console.info('============= START : replacePatient ===========');
        const patientBytes = await ctx.stub.getState(id);
        if (!patientBytes || patientBytes.length === 0) {
            throw new Error(`Patient with id '${id}' does not exist`);
        }

        const patient: Patient = JSON.parse(patientStr);
        console.info(`updatePatient: received id: '${id}' patient: ` + JSON.stringify(patient));

        await ctx.stub.putState(id, Buffer.from(JSON.stringify(patient)));
        console.info('============= END : replacePatient ===========');
    }

    // PATCH patient
    public async updatePatient(ctx: Context, id: string, patientStr: string) {
        console.info('============= START : updatePatient ===========');
        const patientBytes = await ctx.stub.getState(id);
        if (!patientBytes || patientBytes.length === 0) {
            throw new Error(`Patient with id '${id}' does not exist`);
        }

        const existingPatient: Patient = JSON.parse(patientBytes.toString());
        const newPatient: Patient = JSON.parse(patientStr);
        console.info(`updatePatient: id: '${id}' existingPatient: ` + JSON.stringify(existingPatient));
        console.info(`updatePatient: id: '${id}' newPatient: ` + JSON.stringify(newPatient));

        // Merge the values from newPatient into existingPatient
        const result = merge(existingPatient, newPatient);
        console.info(`updatePatient: id: '${id}' merged Patient: ` + JSON.stringify(result));

        await ctx.stub.putState(id, Buffer.from(JSON.stringify(result)));
        console.info('============= END : updatePatient ===========');
    }
}
