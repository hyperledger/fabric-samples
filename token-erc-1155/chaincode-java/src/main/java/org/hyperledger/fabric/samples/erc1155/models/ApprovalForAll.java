/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.fabric.samples.erc1155.models;

import com.owlike.genson.annotation.JsonProperty;
import org.hyperledger.fabric.contract.annotation.Property;

public class ApprovalForAll {

  @Property()
  @JsonProperty("owner")
  private String owner;

  @Property()
  @JsonProperty("operator")
  private String operator;

  @Property()
  @JsonProperty("approved")
  private boolean approved;

  /** Default constructor */
  public ApprovalForAll() {
    super();
  }

  /**
   * Constructor of the class
   *
   * @param owner
   * @param operator
   * @param approved
   */
  public ApprovalForAll(final String owner, final String operator, final boolean approved) {
    super();
    this.owner = owner;
    this.operator = operator;
    this.approved = approved;
  }

  /**
   * getter function for the owner
   *
   * @return the token owner account
   */
  public String getOwner() {
    return owner;
  }

  /**
   * getter function for operator
   *
   * @return the token approved operator
   */
  public String getOperator() {
    return operator;
  }

  /**
   * getter function for the approved
   *
   * @return is operator is approved one or not
   */
  public boolean getApproved() {
    return approved;
  }
}
