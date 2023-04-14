/*
  SPDX-License-Identifier: Apache-2.0
*/

import { Object, Property } from 'fabric-contract-api';

@Object()
export class AssetTransferTransientInput {
  @Property()
  public ID: string;
  @Property()
  public BuyerMSP: string;
}
