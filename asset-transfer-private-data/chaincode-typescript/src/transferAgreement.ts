/*
  SPDX-License-Identifier: Apache-2.0
*/

import { Object, Property } from 'fabric-contract-api';

@Object()
// TransferAgreement describes the buyer agreement returned by ReadTransferAgreement
export class TransferAgreement {
  @Property()
  public ID: string;
  @Property()
  public BuyerID: string;

}
