/*
  SPDX-License-Identifier: Apache-2.0
*/

import {Object, Property} from 'fabric-contract-api';

@Object()
export class Asset {
    @Property()
    public docType?: string;

    @Property()
    public ID: string = '';

    @Property()
    public Name:string = '';

    @Property()
    public DOB: string = '';

    @Property()
    public Marks: number = 0;
}


@Object()
export class Student{

    @Property()
    public ID: string = '';

    @Property()
    public docType:string = 'Student';

    @Property()
    public RollNo: string = '';

    @Property()
    public Name: string = '';

    @Property()
    public Marks: object = {};

    @Property()
    public DepartmentID:string = '';
}

@Object()
export class Department{

    @Property()
    public ID: string = '';

    @Property()
    public docType:string = 'Department';

    @Property()
    public DeptID: string = '';

    @Property()
    public DeptName: string = '';

    @Property()
    public Year: number = 0;

    @Property()
    public Subject: string[] = [];

    @Property()
    public RollNos: string[] = [];
}

@Object()
export class Certificate{

    @Property()
    public ID: string = '';

    @Property()
    public docType:string = 'Certificate';

    @Property()
    public Name:string = '';

    @Property()
    public Event:string = '';

    @Property()
    public Links:string = '';
}
