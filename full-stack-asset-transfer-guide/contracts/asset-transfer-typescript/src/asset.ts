/*
  SPDX-License-Identifier: Apache-2.0
*/

import { Object as DataType, Property } from 'fabric-contract-api';

@DataType()
export class Asset {
    @Property('ID', 'string')
    ID = '';

    @Property('Color', 'string')
    Color = '';

    @Property('Owner', 'string')
    Owner = '';

    @Property('AppraisedValue', 'number')
    AppraisedValue = 0;

    @Property('Size', 'number')
    Size = 0;

    constructor() {
        // Nothing to do
    }

    static newInstance(state: Partial<Asset> = {}): Asset {
        return {
            ID: assertHasValue(state.ID, 'Missing ID'),
            Color: state.Color ?? '',
            Size: state.Size ?? 0,
            Owner: assertHasValue(state.Owner, 'Missing Owner'),
            AppraisedValue: state.AppraisedValue ?? 0,
        };
    }
}

function assertHasValue<T>(value: T | undefined | null, message: string): T {
    if (value == undefined || (typeof value === 'string' && value.length === 0)) {
        throw new Error(message);
    }

    return value;
}
