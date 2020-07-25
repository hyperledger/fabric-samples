/*
 * SPDX-License-Identifier: Apache-2.0
 */
import { Object, Property } from 'fabric-contract-api';

@Object()

export class Asset {
    @Property()
    public docType?: string;
    public ID: string;
    public Color: string;
    public Size: number;
    public Owner: string;
    public AppraisedValue: number;
}
