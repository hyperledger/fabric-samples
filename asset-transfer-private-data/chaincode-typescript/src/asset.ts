/*
  SPDX-License-Identifier: Apache-2.0
*/

import { Object, Property } from 'fabric-contract-api';

@Object()
// Asset describes main asset details that are visible to all organizations
export class Asset {
  @Property()
  public docType?: string;

  @Property()
  public ID: string;

  @Property()
  public Color: string;

  @Property()
  public Size: number;

  @Property()
  public Owner: string;
}
