/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.fabric.samples.erc1155.models;

import com.owlike.genson.annotation.JsonProperty;
import org.hyperledger.fabric.contract.annotation.Property;

public class TransferBatch extends Event {

  @Property()
  @JsonProperty("ids")
  private long[] ids;

  @Property()
  @JsonProperty("values")
  private long[] values;

  /** Default constructor */
  public TransferBatch() {
    super();
  }

  /**
   * Constructor of the class
   *
   * @param operator
   * @param from
   * @param to
   * @param ids
   * @param values
   */
  public TransferBatch(
      final String operator,
      final String from,
      final String to,
      final long[] ids,
      final long[] values) {
    super(operator, from, to);
    this.ids = ids;
    this.values = values;
  }

  /**
   * Getter function for Ids
   *
   * @return all token Ids
   */
  public long[] getIds() {
    return ids;
  }

  /**
   * Getter function for the values
   *
   * @return get the amount or value of all the tokens
   */
  public long[] getValues() {
    return values;
  }
}
