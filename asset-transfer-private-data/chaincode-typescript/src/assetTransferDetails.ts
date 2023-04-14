/*
  SPDX-License-Identifier: Apache-2.0
*/

import { Object, Property } from 'fabric-contract-api';

@Object()
// AssetPrivateDetails describes details that are private to owners
export class AssetPrivateDetails {
  @Property()
  public ID: string;
  @Property()
  public AppraisedValue: number;
}
