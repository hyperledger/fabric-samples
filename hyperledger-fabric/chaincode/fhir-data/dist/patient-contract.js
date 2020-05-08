"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const fabric_contract_api_1 = require("fabric-contract-api");
const ts_deepmerge_1 = require("ts-deepmerge");
class PatientContract extends fabric_contract_api_1.Contract {
    async initLedger(ctx) {
        console.info('============= START : Initialize Ledger ===========');
        console.info('============= END : Initialize Ledger ===========');
    }
    // GET patient by id
    async queryPatient(ctx, id) {
        const patientBytes = await ctx.stub.getState(id);
        if (!patientBytes || patientBytes.length === 0) {
            throw new Error(`Patient with id '${id}' does not exist`);
        }
        console.log(patientBytes.toString());
        return patientBytes.toString();
    }
    // POST patient
    async addPatient(ctx, id, patientStr) {
        console.info('============= START : addPatient ===========');
        const patientBytes = await ctx.stub.getState(id);
        if (patientBytes && patientBytes.length > 0) {
            throw new Error(`Patient with id '${id}' exists; use updatePatient instead.`);
        }
        const patient = JSON.parse(patientStr);
        console.info(`addPatient: received id: '${id}' patient: ` + JSON.stringify(patient));
        await ctx.stub.putState(id, Buffer.from(JSON.stringify(patient)));
        console.info('============= END : addPatient ===========');
    }
    // PUT patient
    async replacePatient(ctx, id, patientStr) {
        console.info('============= START : replacePatient ===========');
        const patientBytes = await ctx.stub.getState(id);
        if (!patientBytes || patientBytes.length === 0) {
            throw new Error(`Patient with id '${id}' does not exist`);
        }
        const patient = JSON.parse(patientStr);
        console.info(`updatePatient: received id: '${id}' patient: ` + JSON.stringify(patient));
        await ctx.stub.putState(id, Buffer.from(JSON.stringify(patient)));
        console.info('============= END : replacePatient ===========');
    }
    // PATCH patient
    async updatePatient(ctx, id, patientStr) {
        console.info('============= START : updatePatient ===========');
        const patientBytes = await ctx.stub.getState(id);
        if (!patientBytes || patientBytes.length === 0) {
            throw new Error(`Patient with id '${id}' does not exist`);
        }
        const existingPatient = JSON.parse(patientBytes.toString());
        const newPatient = JSON.parse(patientStr);
        console.info(`updatePatient: id: '${id}' existingPatient: ` + JSON.stringify(existingPatient));
        console.info(`updatePatient: id: '${id}' newPatient: ` + JSON.stringify(newPatient));
        // Merge the values from newPatient into existingPatient
        const result = ts_deepmerge_1.default(existingPatient, newPatient);
        console.info(`updatePatient: id: '${id}' merged Patient: ` + JSON.stringify(result));
        await ctx.stub.putState(id, Buffer.from(JSON.stringify(result)));
        console.info('============= END : updatePatient ===========');
    }
}
exports.PatientContract = PatientContract;
//# sourceMappingURL=patient-contract.js.map