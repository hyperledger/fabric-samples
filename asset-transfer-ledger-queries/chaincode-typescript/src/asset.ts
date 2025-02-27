/*
  SPDX-License-Identifier: Apache-2.0
*/

import { Object, Property } from 'fabric-contract-api';

@Object()
export class Asset {
    @Property()
    public docType?: string;

    @Property()
    public ID: string = '';

    @Property()
    public color: string = '';

    @Property()
    public size: number = 0;

    @Property()
    public owner: string = '';

    @Property()
    public appraisedValue: number = 0;
}

@Object()
export class HistoryQueryResult {
    @Property()
    record: Asset | null = null;

    @Property()
    txId: string = '';

    @Property()
    timestamp: Date = new Date(0);

    @Property()
    isDelete: boolean = false;
}

@Object()
export class PaginatedQueryResult {
    @Property()
    records: Asset[] = [];

    @Property()
    fetchedRecordsCount: number = 0;

    @Property()
    bookmark: string = '';
}
