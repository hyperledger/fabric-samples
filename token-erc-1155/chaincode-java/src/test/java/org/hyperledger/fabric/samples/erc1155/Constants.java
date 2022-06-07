/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.erc1155;

public final class Constants {

  /** Default constructor */
  private Constants() {
    super();
  }

  public static final String ORG1_USER_ID =
      "x509::CN=User0@org1.example.com, L=San Francisco, ST=California,"
          + " C=US::CN=ca.org2.example.com, O=org2.example.com, L=San Francisco, ST=California, C=US";
  public static final String ORG2_USER_ID =
      "x509::CN=User1@org2.example.com, L=San Francisco, ST=California,"
          + " C=US::CN=ca.org2.example.com, O=org2.example.com, L=San Francisco, ST=California, C=US";
  public static final String ORG3_USER_ID =
      "x509::CN=User1@org3.example.com, L=San Francisco, ST=California,"
          + " C=US::CN=ca.org3.example.com, O=org3.example.com, L=San Francisco, ST=California, C=US";
  public static final String TOKEN_NAME = "AirlineToke";
  public static final String TOKEN_SYMBOL = "ART";
}
