/*
 * SPDX-License-Identifier: Apache-2.0
 */
// Deterministic JSON.stringify()
import {Context, Contract, Info, Returns, Transaction} from 'fabric-contract-api';
import stringify from 'json-stringify-deterministic';
import sortKeysRecursive from 'sort-keys-recursive';
import {Asset, Student, Department, Certificate} from './customAsset';
import { createHash } from 'node:crypto';

@Info({title: 'AssetTransfer', description: 'Smart contract for trading assets'})
export class AssetTransferCustomContract extends Contract {

    @Transaction()
    public async InitLedger(ctx: Context): Promise<void> {
        const assets: Student[] = [];

        for (const asset of assets) {
            asset.docType = 'asset';
            // example of how to write to world state deterministically
            // use convetion of alphabetic order
            // we insert data in alphabetic order using 'json-stringify-deterministic' and 'sort-keys-recursive'
            // when retrieving data, in any lang, the order of data will be the same and consequently also the corresonding hash
            await ctx.stub.putState(asset.RollNo, Buffer.from(stringify(sortKeysRecursive(asset))));
            console.info(`Asset ${asset.RollNo} initialized`);
        }
    }

    // ReadAsset returns the asset stored in the world state with given id.
    @Transaction(false)
    public async ReadAsset(ctx: Context, id: string): Promise<string> {
        const assetJSON = await ctx.stub.getState(id); // get the asset from chaincode state
        if (assetJSON.length === 0) {
            throw new Error(`The asset ${id} does not exist`);
        }
        return assetJSON.toString();
    }


    // DeleteAsset deletes an given asset from the world state.
    @Transaction()
    public async DeleteAsset(ctx: Context, id: string): Promise<void> {
        const exists = await this.AssetExists(ctx, id);
        if (!exists) {
            throw new Error(`The asset ${id} does not exist`);
        }
        return ctx.stub.deleteState(id);
    }

    // AssetExists returns true when asset with given ID exists in world state.
    @Transaction(false)
    @Returns('boolean')
    public async AssetExists(ctx: Context, id: string): Promise<boolean> {
        const assetJSON = await ctx.stub.getState(id);
        return assetJSON.length > 0;
    }

    // GetAllAssets returns all assets found in the world state.
    @Transaction(false)
    @Returns('string')
    public async GetAllAssets(ctx: Context): Promise<string> {
        const allResults = [];
        // range query with empty string for startKey and endKey does an open-ended query of all assets in the chaincode namespace.
        const iterator = await ctx.stub.getStateByRange('', '');
        let result = await iterator.next();
        while (!result.done) {
            const strValue = Buffer.from(result.value.value.toString()).toString('utf8');
            let record;
            try {
                record = JSON.parse(strValue) as Asset;
            } catch (err) {
                console.log(err);
                record = strValue;
            }
            allResults.push(record);
            result = await iterator.next();
        }
        return JSON.stringify(allResults);
    }

    @Transaction()
    public async CreateStudentData(ctx: Context, rollNo:string, name:string): Promise<void> {
        const exists = await this.AssetExists(ctx, "Studenet"+rollNo);
        if (exists) {
            throw new Error(`The asset ${rollNo} already exists`);
        }

        const asset: Student = {
            ID: 'Student' + rollNo,
            docType: 'Student',
            RollNo: rollNo,
            Name: name,
            Marks: {},
            DepartmentID: ''
        };

        await ctx.stub.putState(asset.ID, Buffer.from(stringify(sortKeysRecursive(asset))));
    }

    @Transaction()
    public async CreateDept(ctx: Context, deptName:string, year:number): Promise<void> {
        const exists = await this.AssetExists(ctx, 'Department'+deptName + year);
        if (exists) {
            throw new Error(`The asset ${deptName + year} already exists`);
        }

        const department: Department =  {
            ID: 'Department' + deptName + year,
            docType: 'Department',
            DeptID: deptName + year,
            DeptName: deptName,
            Year: year,
            Subject: [],
            RollNos: []
        };

        await ctx.stub.putState(department.ID, Buffer.from(stringify(sortKeysRecursive(department))));
    }

    @Transaction()
    public async deptAddSubject(ctx: Context, deptID:string, subject:string): Promise<void> {
        const exists = await this.AssetExists(ctx,"Department"+ deptID);
        if (!exists) {
            throw new Error(`The asset ${deptID} already exists`);
        }

        const dept: Department = JSON.parse(await this.ReadAsset(ctx,"Department"+ deptID));

        const new_dept: Department = {
            ID: dept.ID,
            docType: dept.docType,
            DeptID: dept.DeptID,
            DeptName: dept.DeptName,
            Subject: [...dept.Subject, subject],
            RollNos: dept.RollNos,
            Year: dept.Year
        };

        await ctx.stub.putState(new_dept.ID, Buffer.from(stringify(sortKeysRecursive(new_dept))));

    }

    @Transaction()
    public async deptAddStudent(ctx: Context, deptID:string, rollNo:string): Promise<void> {
        const exists = await this.AssetExists(ctx,"Department"+ deptID);
        if (!exists) {
            throw new Error(`The Department ${deptID} doesnt exists`);
        }

        const dept: Department = JSON.parse(await this.ReadAsset(ctx,"Department"+deptID));
        const student: Student = JSON.parse(await this.ReadAsset(ctx, "Student"+rollNo))

        const new_stud: Student = {
            ID: student.ID,
            docType: student.docType,
            RollNo: student.RollNo,
            Name: student.Name,
            DepartmentID: deptID,
            Marks: student.Marks
        }

        const new_dept: Department = {
            ID: dept.ID,
            docType: dept.docType,
            DeptID: dept.DeptID,
            DeptName: dept.DeptName,
            Subject: dept.Subject,
            RollNos: [...dept.RollNos,rollNo],
            Year: dept.Year
        };

        await ctx.stub.putState(new_stud.ID, Buffer.from(stringify(sortKeysRecursive(new_dept))));
        await ctx.stub.putState(new_dept.ID, Buffer.from(stringify(sortKeysRecursive(new_dept))));
    }

    @Transaction()
    public async addStudentMark(ctx: Context, rollNo: string, subjectG: string, mark: number): Promise<void> {
        const exists = await this.AssetExists(ctx,"Student" + rollNo);
        if (!exists) {
            throw new Error(`The asset ${rollNo} already exists`);
        }

        const student: Student = JSON.parse(await this.ReadAsset(ctx,"Student"+ rollNo));
        
        ctx.clientIdentity.assertAttributeValue

        const newStudent: Student = {
            ID: student.ID,
            docType: student.docType,
            RollNo: student.RollNo,
            Name: student.Name,
            Marks: {...student.Marks,[subjectG] : mark},
            DepartmentID: student.DepartmentID
        };

        await ctx.stub.putState(newStudent.ID, Buffer.from(stringify(sortKeysRecursive(newStudent))));
    }

    @Transaction(false)
    public async getStudent(ctx: Context, rollNo: string)
    {
        const exists = await this.AssetExists(ctx, "Student"+rollNo);
        if (!exists) {
            throw new Error(`The asset doest exists`);
        }
        const student = await this.ReadAsset(ctx,"Student"+rollNo);

        return JSON.stringify(student)

    }

    @Transaction()
    public async addCertificate(ctx: Context, name: string, event: string, links: string): Promise<String> {

        const hash = createHash('sha256')

        hash.update(name+event+links)

        const hashStr: string = hash.copy().digest('hex');

        const exists = await this.AssetExists(ctx,"Certificate" + hashStr);
        if (exists) {
            throw new Error(`The asset ${hashStr} already exists`);
        }
    

        const newCertificate: Certificate = {
            ID: 'Certificate' + hashStr,
            Name: name,
            Event: event,
            Links: links,
            docType: 'Certificate'
        }


        await ctx.stub.putState(newCertificate.ID, Buffer.from(stringify(sortKeysRecursive(newCertificate))));

        return hashStr;
    }

    @Transaction(false)
    public async validateCertificate(ctx: Context, hashStr: string): Promise<string> {
        const assetJSON = await ctx.stub.getState('Certificate'+hashStr); // get the asset from chaincode state
        if (assetJSON.length === 0) {
            throw new Error(`The Certificate does not exist`);
        }
        return assetJSON.toString();
    }




}
