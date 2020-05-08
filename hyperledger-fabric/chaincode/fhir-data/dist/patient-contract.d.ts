import { Context, Contract } from 'fabric-contract-api';
export declare class PatientContract extends Contract {
    initLedger(ctx: Context): Promise<void>;
    queryPatient(ctx: Context, id: string): Promise<string>;
    addPatient(ctx: Context, id: string, patientStr: string): Promise<void>;
    replacePatient(ctx: Context, id: string, patientStr: string): Promise<void>;
    updatePatient(ctx: Context, id: string, patientStr: string): Promise<void>;
}
