/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.fabric.samples.erc721.models;

import com.owlike.genson.Genson;
import com.owlike.genson.annotation.JsonProperty;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;
import org.json.JSONObject;

import static java.nio.charset.StandardCharsets.UTF_8;

@DataType()
public final class Approval {

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
  public Approval() {
    super();
  }

  /**
   * Constructor of the class
   *
   * @param owner
   * @param operator
   * @param approved
   */
  public Approval(
      @JsonProperty("owner") final String owner,
      @JsonProperty("operator") final String operator,
      @JsonProperty("approved") final boolean approved) {
    super();
    this.owner = owner;
    this.operator = operator;
    this.approved = approved;
  }

  /**
   * @param data
   * @return
   */
  public static Approval fromJSONString(final String data) {
    final JSONObject json = new JSONObject(data);
    final Approval approver =
        new Approval(
            json.getString("owner"),
            json.getString("operator"),
            Boolean.valueOf(json.getBoolean("approved")));
    return approver;
  }

  /**
   * Constructs new Approval from JSON String.
   *
   * @param json Approval format.
   * @return
   */
  public static Approval fromBytes(final byte[] bytes) {
    return new Genson().deserialize(new String(bytes, UTF_8), Approval.class);
  }

  /** @return */
  public String getOwner() {
    return owner;
  }

  public void setOwner(final String owner) {
    this.owner = owner;
  }

  /** @return */
  public String getOperator() {
    return operator;
  }

  /** @param operator */
  public void setOperator(final String operator) {
    this.operator = operator;
  }

  /** @return */
  public boolean isApproved() {
    return approved;
  }

  /** @param approved */
  public void setApproved(final boolean approved) {
    this.approved = approved;
  }

  /** @return JSON String of the Approval object. */
  public String toJSONString() {
    return new Genson().serialize(this).toString();
  }
}
