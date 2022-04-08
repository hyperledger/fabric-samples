/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.fabric.samples.erc721;

public enum ContractConstants {
  BALANCE("balance"),
  NFT("nft"),
  APPROVAL("approval"),
  NAMEKEY("nameKey"),
  SYMBOLKEY("symbolKey"),
  APPROVE_FOR_ALL("ApproveForAll"),
  TRANSFER("Transfer"),
  MINTER_ORG_MSP("Org1MSP");
  private final String prefix;

  ContractConstants(final String value) {
    this.prefix = value;
  }

  public String getValue() {
    return prefix;
  }
}
