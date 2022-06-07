/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.fabric.samples.erc1155.models;

import com.owlike.genson.annotation.JsonProperty;
import org.hyperledger.fabric.contract.annotation.Property;

public class TransferBatchMultiRecipient {

  @Property()
  @JsonProperty("operator")
  private String operator;

  @Property()
  @JsonProperty("from")
  private String from;

  @Property()
  @JsonProperty("to")
  private String[] to;

  @Property()
  @JsonProperty("ids")
  private long[] ids;

  @Property()
  @JsonProperty("values")
  private long[] values;

  /** Default constructor */
  public TransferBatchMultiRecipient() {
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
  public TransferBatchMultiRecipient(
      final String operator,
      final String from,
      final String[] to,
      final long[] ids,
      final long[] values) {
    super();
    this.operator = operator;
    this.from = from;
    this.to = to;
    this.ids = ids;
    this.values = values;
  }

  /**
   * getter function for from account
   *
   * @return get the from account
   */
  public String getFrom() {
    return from;
  }

  /**
   * getter function for Ids
   *
   * @return get all token ids
   */
  public long[] getIds() {
    return ids;
  }

  /**
   * getter function for the operator
   *
   * @return gett all aproved token operators
   */
  public String getOperator() {
    return operator;
  }

  /**
   * getter function for recipients
   *
   * @return all the recipients
   */
  public String[] getTo() {
    return to;
  }

  /**
   * getter function for all the values
   *
   * @return get all the token values or ammount
   */
  public long[] getValues() {
    return values;
  }
}
