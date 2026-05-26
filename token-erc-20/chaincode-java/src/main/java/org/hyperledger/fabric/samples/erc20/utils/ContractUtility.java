/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.fabric.samples.erc20.utils;
/**
 * @author Renjith
 * @Desc Utility class
 */

public final class ContractUtility {

  private ContractUtility() {
  }


  public static boolean stringIsNullOrEmpty(final String string) {
        return string == null || string.isEmpty();
  }
}
