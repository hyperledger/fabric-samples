/*
  SPDX-License-Identifier: Apache-2.0
*/

import { Object, Property } from 'fabric-contract-api';
import { nonEmptyString, positiveNumber } from './utils';

@Object()
// Asset describes main asset details that are visible to all organizations
export class Asset {
    @Property()
    objectType?: string;

    @Property()
    ID: string = '';

    @Property()
    Color: string = '';

    @Property()
    Size: number = 0;

    @Property()
    Owner: string = '';

    static fromBytes(bytes: Uint8Array): Asset {
        if (bytes.length === 0) {
            throw new Error('no asset data');
        }
        const json = Buffer.from(bytes).toString('utf8');
        const properties = JSON.parse(json) as Partial<Asset>;

        return {
            objectType: properties.objectType,
            ID: nonEmptyString(
                properties.ID,
                'ID field must be a non-empty string'
            ),
            Color: nonEmptyString(
                properties.Color,
                'Color field must be a non-empty string'
            ),
            Size: positiveNumber(
                properties.Size,
                'Size field must be a positive integer'
            ),
            Owner: nonEmptyString(
                properties.Owner,
                'appraiseOwner field must be a non-empty string'
            ),
        };
    }
}
