/*
  SPDX-License-Identifier: Apache-2.0
*/

import {Object, Property} from 'fabric-contract-api';

@Object()
export class Location {
  @Property()
  public ID: string;

  @Property()
  public Name: string;

  @Property()
  public Latitude: string;

  @Property()
  public Longitude: string;
  
  @Property()
  public Actor: string;
}
