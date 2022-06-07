/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.fabric.samples.erc1155.models;

import org.hyperledger.fabric.contract.annotation.Property;

import com.owlike.genson.annotation.JsonProperty;

public class TransferSingle extends Event {

  @Property()
  @JsonProperty("id")
  private long id;

  @Property()
  @JsonProperty("value")
  private long value;

  /** Default constructor */
  public TransferSingle() {
    super();
  }

  /**
   * Constructor of the class
   *
   * @param operator
   * @param from
   * @param to
   * @param id
   * @param value
   */
  public TransferSingle(final String operator, final String from, final String to, final long id, final long value) {
    super(operator, from, to);
    this.id = id;
    this.value = value;
  }

  /**
   * gettter function for id
   *
   * @return get the token id
   */
  public long getId() {
    return id;
  }

  /**
   * getter function for value
   *
   * @return the value or amount of the token id
   */
  public long getValue() {
    return value;
  }
}
