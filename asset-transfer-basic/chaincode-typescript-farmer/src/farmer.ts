/*
  SPDX-License-Identifier: Apache-2.0
*/

import {Object, Property} from 'fabric-contract-api';

@Object()
export class Farmer {
  @Property()
  public ID: string;

  @Property()
  public FarmerName: string;

  @Property()
  public Location: string;
  
  @Property()
  public Actor: string;
}
