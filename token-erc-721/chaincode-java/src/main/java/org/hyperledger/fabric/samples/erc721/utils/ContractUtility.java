/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.fabric.samples.erc721.utils;

public final class ContractUtility {

  private ContractUtility() {

  }

  /**
   * @param string
   * @return
   */
  public static boolean stringIsNullOrEmpty(final String string) {
    return string == null || string.isEmpty();
  }
}
