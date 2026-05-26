/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.fabric.samples.erc721.models;

import com.owlike.genson.Genson;
import com.owlike.genson.annotation.JsonProperty;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import static java.nio.charset.StandardCharsets.UTF_8;

@DataType()
public final class Transfer {

  @Property()
  @JsonProperty("from")
  private String from;

  @Property()
  @JsonProperty("to")
  private String to;

  @Property()
  @JsonProperty("tokenId")
  private String tokenId;

  /**
   * Constructor of the class
   *
   * @param owner
   * @param operator
   * @param approved
   */
  public Transfer(
      @JsonProperty("from") final String from,
      @JsonProperty("to") final String to,
      @JsonProperty("tokenId") final String tokenId) {
    super();
    this.from = from;
    this.to = to;
    this.tokenId = tokenId;
  }

  /** Default Constructor of the class. */
  public Transfer() {
    super();
  }

  /**
   * Constructs new Approval from JSON String.
   *
   * @param json Approval format.
   * @return
   */
  public static Transfer fromBytes(final byte[] bytes) {
    return new Genson().deserialize(new String(bytes, UTF_8), Transfer.class);
  }

  public String getFrom() {
    return from;
  }

  public void setFrom(final String from) {
    this.from = from;
  }

  public String getTo() {
    return to;
  }

  /** @param to */
  public void setTo(final String to) {
    this.to = to;
  }

  /** @return */
  public String getTokenId() {
    return tokenId;
  }

  /** @param tokenId */
  public void setTokenId(final String tokenId) {
    this.tokenId = tokenId;
  }

  /** @return String JSON */
  public String toJSONString() {
    return new Genson().serialize(this).toString();
  }
}
