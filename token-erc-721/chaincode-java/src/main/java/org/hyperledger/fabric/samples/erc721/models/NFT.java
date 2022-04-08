/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.fabric.samples.erc721.models;

import com.owlike.genson.Genson;
import com.owlike.genson.annotation.JsonProperty;
import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;
import org.json.JSONObject;

import java.io.Serializable;

@DataType()
public final class NFT implements Serializable {

  private static final long serialVersionUID = -29439695319757532L;

  @Property()
  @JsonProperty("tokenId")
  private String tokenId;

  @Property()
  @JsonProperty("owner")
  private String owner;

  @Property()
  @JsonProperty("tokenURI")
  private String tokenURI;

  @Property()
  @JsonProperty("approved")
  private String approved;

  /** Default constructor */
  public NFT() {
    super();
  }

  /**
   * Constructor of the class
   *
   * @param tokenId
   * @param owner
   * @param tokenURI
   * @param approved
   */
  public NFT(
      @JsonProperty("tokenId") final String tokenId,
      @JsonProperty("owner") final String owner,
      @JsonProperty("tokenURI") final String tokenURI,
      @JsonProperty("approved") final String approved) {
    super();
    this.tokenId = tokenId;
    this.owner = owner;
    this.tokenURI = tokenURI;
    this.approved = approved;
  }

  /**
   * Convert JSON string to NFT object.
   *
   * @param data JSON string.
   * @return NFT object
   */
  public static NFT fromJSONString(final String data) {
    final JSONObject json = new JSONObject(data);
    final NFT nft =
        new NFT(
            json.getString("tokenId"),
            json.getString("owner"),
            json.getString("tokenURI"),
            json.getString("approved"));

    return nft;
  }

  /** @return */
  public String getTokenId() {
    return tokenId;
  }

  /** @param tokenId */
  public void setTokenId(final String tokenId) {
    this.tokenId = tokenId;
  }

  /** @return */
  public String getOwner() {
    return owner;
  }

  /** @param owner */
  public void setOwner(final String owner) {
    this.owner = owner;
  }

  /** @return */
  public String getTokenURI() {
    return tokenURI;
  }

  /** @param tokenURI */
  public void setTokenURI(final String tokenURI) {
    this.tokenURI = tokenURI;
  }

  /** @return */
  public String getApproved() {
    return approved;
  }

  /** @param approved */
  public void setApproved(final String approved) {
    this.approved = approved;
  }

  /** @return String JSON */
  public String toJSONString() {
    return new Genson().serialize(this).toString();
  }
}
