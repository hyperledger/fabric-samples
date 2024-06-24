/*
  SPDX-License-Identifier: Apache-2.0
*/

import {Object, Property} from 'fabric-contract-api';

@Object()
export class Farmer {

    @Property()
    public FarmerID: string;

    @Property()
    public FarmerName: string;

    @Property()
    public PoNumber: string;

    @Property()
    public ItemType: string;

    @Property()
    public Qty: number;

    @Property()
    public Location: string;

    @Property()
    public PurchasePrice: number;

    @Property()
    public PurchaseDate: string;
}
