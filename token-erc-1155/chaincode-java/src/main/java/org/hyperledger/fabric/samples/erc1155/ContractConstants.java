/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.erc1155;

public enum ContractConstants {
  URI_KEY("uri"),
  BALANCE_PREFIX("account~tokenId~sender"),
  APPROVAL_PREFIX("account~operator"),
  NAME_KEY("name"),
  SYMBOL_KEY("symbol"),
  APPROVE_FOR_ALL("ApproveForAll"),
  TRANSFER("Transfer"),
  MINTER_ORG_MSP("Org1MSP"),
  ZERO_ADDRESS("0x0"),
  TRANSFER_SINGLE_EVENT("TransferSingle"),
  TRANSFER_BATCH_EVENT("TransferBatch"),
  TRANSFER_BATCH_MULTI_RECIPIENT_EVENT("TransferBatchMultiRecipient"),
  APPROVE_FOR_ALL_EVENT("ApprovalForAll");

  private final String value;

  ContractConstants(final String value) {
    this.value = value;
  }

  public String getValue() {
    return this.value;
  }
}
