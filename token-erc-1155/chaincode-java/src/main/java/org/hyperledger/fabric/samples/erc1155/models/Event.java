/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.fabric.samples.erc1155.models;

import com.owlike.genson.annotation.JsonProperty;
import org.hyperledger.fabric.contract.annotation.Property;

public abstract class Event {

  @Property()
  @JsonProperty("operator")
  private String operator;

  @Property()
  @JsonProperty("from")
  private String from;

  @Property()
  @JsonProperty("to")
  private String to;

  /** Default constructor */
  public Event() {
    super();
  }

  /**
   * Constructor of the class
   *
   * @param operator
   * @param from
   * @param to
   */
  public Event(final String operator, final String from, final String to) {
    super();
    this.operator = operator;
    this.from = from;
    this.to = to;
  }

  /**
   * getter function for the operator
   *
   * @return the token operator
   */
  public String getOperator() {
    return operator;
  }

  /**
   * getter function for the from
   *
   * @return the sender account
   */
  public String getFrom() {
    return from;
  }

  /**
   * getter function for the to
   *
   * @return the recipient account
   */
  public String getTo() {
    return to;
  }
}
