/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.fabric.samples.erc20.model;

import com.owlike.genson.annotation.JsonProperty;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

@DataType()
public final class Approval {

  @Property()
  @JsonProperty("owner")
  private String owner;

  @Property()
  @JsonProperty("spender")
  private String spender;

  @Property()
  @JsonProperty("value")
  private long value;

  /** Default constructor */
  public Approval() {
    super();
  }

  /**
   * Constructor of the class
   *
   * @param owner token owner
   * @param spender approved spender of the token
   * @param value amount approved as allowance
   */
  public Approval(
      @JsonProperty("owner") final String owner,
      @JsonProperty("spender") final String spender,
      @JsonProperty("value") final long value) {
    super();
    this.owner = owner;
    this.spender = spender;
    this.value = value;
  }

  public String getOwner() {
    return owner;
  }

  public String getSpender() {
    return spender;
  }

  public long getValue() {
    return value;
  }
}
