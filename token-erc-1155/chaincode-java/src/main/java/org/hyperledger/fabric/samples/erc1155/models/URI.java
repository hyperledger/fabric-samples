/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.fabric.samples.erc1155.models;

import com.owlike.genson.annotation.JsonProperty;
import org.hyperledger.fabric.contract.annotation.Property;

public class URI {

  @Property()
  @JsonProperty("id")
  private long id;

  @Property()
  @JsonProperty("value")
  private long value;

  /** Default constructor */
  public URI() {
    super();
  }

  /**
   * Constructor of the class
   *
   * @param id
   * @param value
   */
  public URI(final long id, final long value) {
    super();
    this.id = id;
    this.value = value;
  }

  /**
   * getter function for the id
   *
   * @return token id
   */
  public long getId() {
    return id;
  }

  /**
   *  getter function for the value
   *
   * @return
   */
  public long getValue() {
    return value;
  }
}
