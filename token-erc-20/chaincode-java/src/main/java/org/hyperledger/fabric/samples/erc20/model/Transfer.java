/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.fabric.samples.erc20.model;

import com.owlike.genson.annotation.JsonProperty;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

@DataType()
public final class Transfer {

  @Property()
  @JsonProperty("from")
  private String from;

  @Property()
  @JsonProperty("to")
  private String to;

  @Property()
  @JsonProperty("value")
  private long value;

  /** Default constructor */
  public Transfer() {
    super();
  }

  /**
   * Constructor of the class
   *
   * @param from owner of the token
   * @param to token receiver
   * @param value amount to be transferred
   */
  public Transfer(
      @JsonProperty("from") final String from,
      @JsonProperty("to") final String to,
      @JsonProperty("value") final long value) {
    super();
    this.from = from;
    this.to = to;
    this.value = value;
  }

  public String getFrom() {
    return from;
  }

  public String getTo() {
    return to;
  }

  public long getValue() {
    return value;
  }
}
