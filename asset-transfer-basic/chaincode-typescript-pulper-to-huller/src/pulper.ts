/*
  SPDX-License-Identifier: Apache-2.0
*/

import {Object, Property} from 'fabric-contract-api';

@Object()
export class Pulper {

  @Property()
  public ID: string;

  @Property()
  public PoNumber: string;

  @Property()
  public VcpCode: string;

  @Property()
  public BatchNumber: string;

  @Property()
  public VcpFinishProcessDate: string;

  @Property()
  public VcpItemQty: string;

  @Property()
  public VchCode: string;

  @Property()
  public VchDriedParchmentDate: string;

  @Property()
  public VchDriedParchmentQty: string;

  @Property()
  public VchDeliveryDate: string;

  @Property()
  public VchItemQty: string;

  @Property()
  public VchDeliveryNumber: string;

  @Property()
  public VcpProcessType?: string;

  @Property()
  public VcpItemType?: string;

  @Property()
  public VcpStartProcessDate?: string;

  @Property()
  public VcpDeliveryNote?: string;

  @Property()
  public VcpLocation?: string;
  
  @Property()
  public Actor: string;
}
