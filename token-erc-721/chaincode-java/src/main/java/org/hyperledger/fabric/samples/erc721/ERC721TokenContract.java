/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.erc721;

import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.samples.erc721.models.Approval;
import org.hyperledger.fabric.samples.erc721.models.NFT;
import org.hyperledger.fabric.samples.erc721.models.Transfer;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.fabric.samples.erc721.utils.ContractUtility.stringIsNullOrEmpty;

@Contract(
    name = "erc721token",
    info =
        @Info(
            title = "ERC721Token Contract",
            description = "The erc721 NFT implementation",
            version = "0.0.1-SNAPSHOT",
            license =
                @License(
                    name = "Apache 2.0 License",
                    url = "http://www.apache.org/licenses/LICENSE-2.0.html"),
            contact =
                @Contact(
                    email = "renjith.narayanan@gmail.com",
                    name = "Renjith Narayanan",
                    url = "https://hyperledger.example.com")))
@Default
public class ERC721TokenContract implements ContractInterface {

  /**
   * BalanceOf counts all non-fungible tokens assigned to an owner.There is a key record for every
   * non-fungible token in the format of balancePrefix.owner.tokenId. balanceOf() queries for and
   * counts all records matching balancePrefix.owner.*
   *
   * @param ctx the transaction context
   * @param owner An owner for whom to query the balance
   * @return The number of non-fungible tokens owned by the owner, possibly zero
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public long BalanceOf(final Context ctx, final String owner) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    final ChaincodeStub stub = ctx.getStub();
    final CompositeKey balanceKey =
        stub.createCompositeKey(ContractConstants.BALANCE.getValue(), owner);
    final QueryResultsIterator<KeyValue> results = stub.getStateByPartialCompositeKey(balanceKey);
    long balance = 0;
    for (KeyValue result : results) {
      if (!stringIsNullOrEmpty(result.getStringValue())) {
        balance++;
      }
    }
    return balance;
  }

  /**
   * Finds the owner of a non-fungible token.
   *
   * @param ctx the transaction context
   * @param tokenId the identifier for a non-fungible token.
   * @return return the owner of the non-fungible token.
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public String OwnerOf(final Context ctx, final String tokenId) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    final NFT nft = this._readNft(ctx, tokenId);
    if (stringIsNullOrEmpty(nft.getOwner())) {
      final String errorMessage = String.format("No owner is assigned o the token  %s", tokenId);
      throw new ChaincodeException(errorMessage, ContractErrors.NO_OWNER_ASSIGNED.toString());
    }
    return nft.getOwner();
  }

  /**
   * It returns if a client is an authorized operator for another client.
   *
   * @param ctx the transaction context
   * @param owner The client that owns the non-fungible tokens
   * @param operator The client that acts on behalf of the owner
   * @return Return true if the operator is an approved operator for the owner, false otherwise
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public boolean IsApprovedForAll(final Context ctx, final String owner, final String operator) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    final ChaincodeStub stub = ctx.getStub();
    final CompositeKey approvalKey =
        stub.createCompositeKey(ContractConstants.APPROVAL.getValue(), owner, operator);
    final String approvalJson = stub.getStringState(approvalKey.toString());
    if (stringIsNullOrEmpty(approvalJson)) {
      return false;
    } else {
      final Approval approval = Approval.fromJSONString(approvalJson);
      return approval.isApproved();
    }
  }

  /**
   * Approve changes or reaffirms the approved client for a non-fungible token. The function update
   * the approved operator of the non-fungible token.
   *
   * @param ctx the transaction context
   * @param operator: operator The new approved client
   * @param tokenId: tokenId the non-fungible token to approve
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void Approve(final Context ctx, final String operator, final String tokenId) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    final ChaincodeStub stub = ctx.getStub();
    final String sender = ctx.getClientIdentity().getId();
    NFT nft = this._readNft(ctx, tokenId);
    final String owner = nft.getOwner();
    final boolean operatorApproval = this.IsApprovedForAll(ctx, owner, sender);
    if ((!owner.equalsIgnoreCase(sender)) && (!operatorApproval)) {
      final String errorMessage =
          String.format(
              "The sender %s is not the current owner nor an authorized operator of the token %s.",
              sender, tokenId);
      throw new ChaincodeException(errorMessage, ContractErrors.UNAUTHORIZED_SENDER.toString());
    }

    nft.setApproved(operator);
    final CompositeKey nftKey = stub.createCompositeKey(ContractConstants.NFT.getValue(), tokenId);
    stub.putStringState(nftKey.toString(), nft.toJSONString());
  }

  /**
   * Enables or disables approval for a third party ("operator") to manage all the message sender's
   * assets.
   *
   * @param ctx the transaction context
   * @param operator A client to add to the set of authorized operators
   * @param approved: True if the operator is approved, false to revoke approval
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void SetApprovalForAll(final Context ctx, final String operator, final boolean approved) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    final String sender = ctx.getClientIdentity().getId();
    final ChaincodeStub stub = ctx.getStub();
    final Approval nftApproval = new Approval(sender, operator, approved);
    final CompositeKey approvalKey =
        stub.createCompositeKey(ContractConstants.APPROVAL.getValue(), sender, operator);
    stub.putStringState(approvalKey.toString(), nftApproval.toJSONString());
    stub.setEvent(
        ContractConstants.APPROVE_FOR_ALL.getValue(), nftApproval.toJSONString().getBytes(UTF_8));
  }

  /**
   * Get the approved client for a single non-fungible token.
   *
   * @param ctx the transaction context
   * @param tokenId The non-fungible token to find the approved client for
   * @return Return The approved client for this non-fungible token, or null if there is none
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public String GetApproved(final Context ctx, final String tokenId) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    final NFT nft = this._readNft(ctx, tokenId);
    return nft.getApproved();
  }

  /**
   * Transfers the ownership of a non-fungible token from one owner to another owner.
   *
   * @param ctx the transaction context
   * @param from the current owner of the non-fungible token
   * @param to the new token owner
   * @param tokenId The non-fungible token to transfer
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void TransferFrom(
      final Context ctx, final String from, final String to, final String tokenId) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    final String sender = ctx.getClientIdentity().getId();
    final ChaincodeStub stub = ctx.getStub();
    NFT nft = this._readNft(ctx, tokenId);
    final String owner = nft.getOwner();
    final String operator = nft.getApproved();
    final boolean operatorApproval = this.IsApprovedForAll(ctx, owner, sender);
    if ((!owner.equalsIgnoreCase(sender))
        && !operator.equalsIgnoreCase(sender)
        && !operatorApproval) {
      final String errorMessage =
          String.format(
              "The sender %s is not the current owner nor an authorized operator of the token %s.",
              sender, tokenId);
      throw new ChaincodeException(errorMessage, ContractErrors.UNAUTHORIZED_SENDER.toString());
    }

    // Check if `from` is the current owner
    if (!owner.equalsIgnoreCase(from)) {
      throw new ChaincodeException(
          String.format("The from %s is not the current owner of the token %s.", from, tokenId),
          ContractErrors.INVALID_TOKEN_OWNER.toString());
    }

    // Clear the approved client for this non-fungible token
    nft.setApproved("");

    // Overwrite a non-fungible token to assign a new owner.
    nft.setOwner(to);
    final CompositeKey nftKey = stub.createCompositeKey(ContractConstants.NFT.getValue(), tokenId);
    stub.putStringState(nftKey.toString(), nft.toJSONString());

    // Remove a composite key from the balance of the current owner
    final CompositeKey balanceKeyFrom =
        stub.createCompositeKey(ContractConstants.BALANCE.getValue(), from, tokenId);
    stub.delState(balanceKeyFrom.toString());

    // Save a composite key to count the balance of a new owner
    final CompositeKey balanceKeyTo =
        stub.createCompositeKey(ContractConstants.BALANCE.getValue(), to, tokenId);
    stub.putState(balanceKeyTo.toString(), Character.toString(Character.MIN_VALUE).getBytes(UTF_8));

    // Emit the Transfer event
    final Transfer transferEvent = new Transfer(from, to, tokenId);
    stub.setEvent(
        ContractConstants.TRANSFER.getValue(), transferEvent.toJSONString().getBytes(UTF_8));
  }

  // ============== ERC721 metadata extension ===============

  /**
   * Returns a descriptive name for a collection of non-fungible tokens in this contract
   *
   * @param ctx the transaction context
   * @return name of the token.
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public String Name(final Context ctx) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    return ctx.getStub().getStringState(ContractConstants.NAMEKEY.getValue());
  }

  /**
   * Returns an abbreviated name for non-fungible tokens in this contract.
   *
   * @param ctx the transaction context
   * @return token symbol.
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public String Symbol(final Context ctx) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    return ctx.getStub().getStringState(ContractConstants.SYMBOLKEY.getValue());
  }

  /**
   * Return a distinct Uniform Resource Identifier (URI) for a given token.
   *
   * @param ctx the transaction context
   * @param tokenId the identifier for a non-fungible token
   * @return : Returns the URI of the token
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public String TokenURI(final Context ctx, final String tokenId) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    final NFT nft = this._readNft(ctx, tokenId);
    return nft.getTokenURI();
  }

  /** ============= ERC721 enumeration extension =============== * */

  /**
   * Counts non-fungible tokens tracked by this contract. There is a key record for every
   * non-fungible token in the format of nftPrefix.tokenId. The function queries for and counts all
   * records matching nftPrefix.*
   *
   * @param ctx the transaction context
   * @return count of valid non-fungible tokens tracked by this contract,where each one of them has
   *     an assigned and queryable owner.
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public long TotalSupply(final Context ctx) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    final ChaincodeStub stub = ctx.getStub();
    final CompositeKey nftKey = stub.createCompositeKey(ContractConstants.NFT.getValue());
    final QueryResultsIterator<KeyValue> iterator = stub.getStateByPartialCompositeKey(nftKey);
    long totalSupply = 0;
    for (KeyValue result : iterator) {
      if (!stringIsNullOrEmpty(result.getStringValue())) {
        totalSupply++;
      }
    }
    return totalSupply;
  }

  /** ============== Extended Functions for this sample =============== * */

  /**
   * Set optional information for a token.
   *
   * @param ctx the transaction context
   * @param name The name of the token
   * @param symbol The symbol of the token
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void Initialize(final Context ctx, final String name, final String symbol) {

    final ChaincodeStub stub = ctx.getStub();

    final String clientMSPID = ctx.getClientIdentity().getMSPID();
    // Check minter authorization - this sample assumes Org1 is the issuer with privilege to set the
    // name and symbol
    if (!clientMSPID.equalsIgnoreCase(ContractConstants.MINTER_ORG_MSP.getValue())) {
      throw new ChaincodeException(
          "Client is not authorized to initialize the contract (set the name and symbol of the token)");
    }

    // Check contract options are not already set, client is not authorized to change them once intitialized
    String tokenName = stub.getStringState(ContractConstants.NAMEKEY.getValue());
    if (!stringIsNullOrEmpty(tokenName)) {
      throw new ChaincodeException("contract options are already set, client is not authorized to change them");
    }

    stub.putStringState(ContractConstants.NAMEKEY.getValue(), name);
    stub.putStringState(ContractConstants.SYMBOLKEY.getValue(), symbol);
  }

  /**
   * Mint a new non-fungible token
   *
   * @param ctx the transaction context
   * @param tokenId Unique ID of the non-fungible token to be minted
   * @param tokenURI URI containing metadata of the minted non-fungible token
   * @return Return the non-fungible token object
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public NFT MintWithTokenURI(final Context ctx, final String tokenId, final String tokenURI) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    final String clientMSPID = ctx.getClientIdentity().getMSPID();
    final ChaincodeStub stub = ctx.getStub();
    // Check minter authorization this sample assumes Org1 is the issuer with privilege to mint a
    // new token
    if (!clientMSPID.equalsIgnoreCase(ContractConstants.MINTER_ORG_MSP.getValue())) {
      throw new ChaincodeException(
          "Client is not authorized to set the name and symbol of the token",
          ContractErrors.UNAUTHORIZED_SENDER.toString());
    }
    final String minter = ctx.getClientIdentity().getId();
    final boolean exists = this._nftExists(ctx, tokenId);
    if (exists) {
      throw new ChaincodeException(
          String.format("The token %s is already minted.", tokenId),
          ContractErrors.TOKEN_ALREADY_EXITS.toString());
    }
    final NFT nft = new NFT(tokenId, minter, tokenURI, "");
    final CompositeKey nftKey = stub.createCompositeKey(ContractConstants.NFT.getValue(), tokenId);
    stub.putStringState(nftKey.toString(), nft.toJSONString());
    // A composite key would be balancePrefix.owner.tokenId, which enables partial
    // composite key query to find and count all records matching balance.owner.*
    // An empty value would represent a delete, so we simply insert the null character.
    final CompositeKey balanceKey =
        stub.createCompositeKey(ContractConstants.BALANCE.getValue(), minter, tokenId);
    stub.putStringState(balanceKey.toString(), Character.toString(Character.MIN_VALUE));
    final Transfer transferEvent = new Transfer("0x0", minter, tokenId);
    stub.setEvent(
        ContractConstants.TRANSFER.getValue(), transferEvent.toJSONString().getBytes(UTF_8));
    return nft;
  }

  /**
   * Burn a non-fungible token
   *
   * @param ctx the transaction context
   * @param tokenId Unique ID of a non-fungible token
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void Burn(final Context ctx, final String tokenId) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    final ChaincodeStub stub = ctx.getStub();
    final String owner = ctx.getClientIdentity().getId();
    // Check if a caller is the owner of the non-fungible token
    final NFT nft = this._readNft(ctx, tokenId);
    if (!nft.getOwner().equalsIgnoreCase(owner)) {
      throw new ChaincodeException(
          String.format("Non-fungible token %s is not owned by %s", tokenId, owner),
          ContractErrors.TOKEN_NONOWNER.toString());
    }
    // Delete the token
    final CompositeKey nftKey = stub.createCompositeKey(ContractConstants.NFT.getValue(), tokenId);
    stub.delState(nftKey.toString());

    // Remove a composite key from the balance of the owner
    final CompositeKey balanceKey =
        stub.createCompositeKey(ContractConstants.BALANCE.getValue(), owner, tokenId);
    stub.delState(balanceKey.toString());
    final Transfer transferEvent = new Transfer(owner, "0x0", tokenId);
    stub.setEvent(
        ContractConstants.TRANSFER.getValue(), transferEvent.toJSONString().getBytes(UTF_8));
  }

  /**
   * Returns the balance of the requesting client's account.
   *
   * @param ctx the transaction context
   * @return the account balance
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public long ClientAccountBalance(final Context ctx) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    return this.BalanceOf(ctx, ctx.getClientIdentity().getId());
  }

  /**
   * Returns the id of the requesting client's account.In this implementation, the client account ID
   * is the clientId itself. Users can use this function to get their own account id, which they can
   * then give to others as the payment address.
   *
   * @param ctx the transaction context
   * @return sender account id .
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public String ClientAccountID(final Context ctx) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    return ctx.getClientIdentity().getId();
  }

  /**
   * Get the the NFT details by token id.
   *
   * @param ctx the transaction context
   * @param tokenId Unique ID of a non-fungible token
   * @return token details.
   */
  private NFT _readNft(final Context ctx, final String tokenId) {
    final ChaincodeStub stub = ctx.getStub();
    final CompositeKey nftKey = stub.createCompositeKey(ContractConstants.NFT.getValue(), tokenId);
    final String nft = stub.getStringState(nftKey.toString());
    if (stringIsNullOrEmpty(nft)) {
      final String errorMessage = String.format("Token with id  %s not found!.", tokenId);
      throw new ChaincodeException(errorMessage, ContractErrors.TOKEN_NOT_FOUND.toString());
    }
    return NFT.fromJSONString(nft);
  }

  /**
   * Check NFT exits.
   *
   * @param ctx the transaction context
   * @param tokenId Unique ID of a non-fungible token
   * @return true if token exits else false.
   */
  private boolean _nftExists(final Context ctx, final String tokenId) {
    final ChaincodeStub stub = ctx.getStub();
    final CompositeKey nftKey = stub.createCompositeKey(ContractConstants.NFT.getValue(), tokenId);
    final String nft = stub.getStringState(nftKey.toString());
    return ((stringIsNullOrEmpty(nft)) ? false : true);
  }

  /**
   * Checks that contract options have been already initialized
   *
   * @param ctx the transaction context
   * @return the number of decimals
   */
  private void checkInitialized(final Context ctx) {
    String tokenName = ctx.getStub().getStringState(ContractConstants.NAMEKEY.getValue());
    if (stringIsNullOrEmpty(tokenName)) {
      throw new ChaincodeException("Contract options need to be set before calling any function, call Initialize() to initialize contract", ContractErrors.TOKEN_NOT_FOUND.toString());
    }
  }
}
