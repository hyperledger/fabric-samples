/*
  SPDX-License-Identifier: Apache-2.0
*/

import {Object, Property} from 'fabric-contract-api';

@Object()
export class Farmer {
  @Property()
  public ID: string;

  @Property()
  public PoNumber: string;

  @Property()
  public VcpCode: string;

  @Property()
  public Location: string;

  @Property()
  public FarmerID: string;

  @Property()
  public FarmerName: string;

  @Property()
  public PurchaseDate: string;

  @Property()
  public ReceiptNo: string;

  @Property()
  public ItemType: string;

  @Property()
  public ProcessType: string;

  @Property()
  public Qty: string;

  @Property()
  public PurchasePrice: string;

  @Property()
  public Floating: string;

  @Property()
  public BatchNumber: string;
  
  @Property()
  public Actor: string;
}
