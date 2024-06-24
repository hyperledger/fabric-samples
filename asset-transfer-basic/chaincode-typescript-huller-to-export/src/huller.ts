/*
  SPDX-License-Identifier: Apache-2.0
*/

import {Object, Property} from 'fabric-contract-api';

@Object()
export class Huller {
  @Property()
  public ID: string;

  @Property()
  public PoNumber: string;

  @Property()
  public VcpCode: string;

  @Property()
  public BatchNumber: string;

  @Property()
  public VchCode: string;

  @Property()
  public VchDeliveryDate: string;

  @Property()
  public VchItemQty: string;

  @Property()
  public VchDeliveryNumber: string;

  @Property()
  public EvcItemQty: string;

  @Property()
  public EvcCode: string;

  @Property()
  public Lot: string;

  @Property()
  public ItemGrade: string;

  @Property()
  public CustomerPO: string;

  @Property()
  public ShippingDate: string;

  @Property()
  public Port: string;
    
  @Property()
  public Actor: string;
}
