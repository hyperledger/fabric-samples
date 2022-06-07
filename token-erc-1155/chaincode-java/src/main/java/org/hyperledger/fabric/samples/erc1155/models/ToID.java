/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.fabric.samples.erc1155.models;

import org.hyperledger.fabric.contract.annotation.Property;

import com.owlike.genson.annotation.JsonProperty;

public class ToID {

  @Property()
  @JsonProperty("id")
  private long id;

  @Property()
  @JsonProperty("to")
  private String to;

  /** Default constructor */
  public ToID() {
    super();
  }

  /**
   * Constructor of the class
   *
   * @param id tokenId
   * @param to the recipient account
   */
  public ToID(final String to, final long id) {
    super();
    this.to = to;
    this.id = id;
  }

  /**
   * The getter function for the Id
   *
   * @return  the tokenId
   */
  public long getId() {
    return id;
  }

  /**
   * The getter function for the To.
   *
   * @return the recipient account id
   */
  public String getTo() {
    return to;
  }

  /**
   *
   * @param obj
   * @return
   */
  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    ToID other = (ToID) obj;
    if (id != other.id) {
      return false;
    }
    if (to == null) {
      if (other.to != null) {
        return false;
      }
    } else if (!to.equalsIgnoreCase(other.to)) {
      return false;
    }
    return true;
  }

  /**
   *
   * @return the hashcode
   */

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (int) (id ^ (id >>> 32));
    result = prime * result + ((to == null) ? 0 : to.hashCode());
    return result;
  }
}
