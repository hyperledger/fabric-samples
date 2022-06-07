/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.erc1155;

import com.owlike.genson.Genson;
import org.hyperledger.fabric.Logger;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.samples.erc1155.models.ApprovalForAll;
import org.hyperledger.fabric.samples.erc1155.models.ToID;
import org.hyperledger.fabric.samples.erc1155.models.TransferBatch;
import org.hyperledger.fabric.samples.erc1155.models.TransferBatchMultiRecipient;
import org.hyperledger.fabric.samples.erc1155.models.TransferSingle;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.fabric.samples.erc1155.utils.ContractUtility.stringIsNullOrEmpty;

@Contract(
    name = "erc1155token",
    info =
        @Info(
            title = "ERC1155Token Contract",
            description = "The erc1155  multi token standard implementation",
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
public class ERC1155Contract implements ContractInterface {

  final Logger logger = Logger.getLogger(ERC1155Contract.class);

  /**
   * Mint a new non-fungible token
   *
   * @param ctx the transaction context
   * @param tokenId Unique ID of the non-fungible token to be minted
   * @param tokenURI URI containing metadata of the minted non-fungible token
   * @return Return the non-fungible token object
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void Mint(final Context ctx, final String account, final long id, final long amount) {

    // check if contract has been initialized first
    this.checkInitialized(ctx);
    // Check minter authorization - this sample assumes Org1 is the central banker with privilege to
    // mint new tokens
    this.authorizationHelper(ctx);
    // Get ID of submitting client identity
    String operator = ctx.getClientIdentity().getId();
    // Mint tokens
    this.mintHelper(ctx, operator, account, id, amount);
    // Emit TransferSingle event
    TransferSingle transferSingleEvent =
        new TransferSingle(
            operator, ContractConstants.ZERO_ADDRESS.getValue(), account, id, amount);
    this.emitTransferSingle(ctx, transferSingleEvent);
  }

  /**
   * MintBatch creates amount tokens for each token type id and assigns them to account This
   * function emits a TransferBatch event
   *
   * @param ctx the transaction context
   * @param tokenId Unique ID of the non-fungible token to be minted
   * @param tokenURI URI containing metadata of the minted non-fungible token
   * @return Return the non-fungible token object
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void MintBatch(
      final Context ctx, final String account, final long[] ids, final long[] amounts) {

    // check if contract has been initialized first
    this.checkInitialized(ctx);

    if (ids.length != amounts.length) {

      throw new ChaincodeException(
          "ids and amounts must have the same length", ContractErrors.INVALID_ARGUMENTS.toString());
    }

    // Check minter authorization - this sample assumes Org1 is the central banker with privilege to
    // mint new tokens
    this.authorizationHelper(ctx);

    // Get ID of submitting client identity
    String operator = ctx.getClientIdentity().getId();

    // Group amount by token id because we can only send token to a recipient only one time in a
    // block. This prevents key conflicts
    Map<Long, Long> amountToSend = new HashMap<>(); // token id => amount

    for (int i = 0; i < amounts.length; i++) {
      if (amountToSend.containsKey(ids[i])) {
        amountToSend.put(ids[i], Math.addExact(amountToSend.get(ids[i]).longValue(), amounts[i]));
      } else {
        amountToSend.put(ids[i], amounts[i]);
      }
    }

    // Copy the map keys and sort it. This is necessary because iterating maps in Go is not
    // deterministic
    List<Long> amountToSendKeys = this.sortedKeys(amountToSend);

    // Mint tokens
    for (long id : amountToSendKeys) {
      long amount = amountToSend.get(id);
      this.mintHelper(ctx, operator, account, id, amount);
    }

    // Emit TransferBatch event
    TransferBatch transferBatchEvent =
        new TransferBatch(
            operator, ContractConstants.ZERO_ADDRESS.getValue(), account, ids, amounts);
    this.emitTransferBatch(ctx, transferBatchEvent);
  }

  /**
   * Burn destroys amount tokens of token type id from account. This function triggers a
   * TransferSingle event.
   *
   * @param ctx the transaction context
   * @param account the account from where to burn token
   * @param id token id
   * @param amount amount of tokens to burn for the given token id.
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void Burn(final Context ctx, final String account, final long id, final long amount) {
    // check if contract has been initialized first
    this.checkInitialized(ctx);
    if (account.equalsIgnoreCase(ContractConstants.ZERO_ADDRESS.getValue())) {
      throw new ChaincodeException(
          "burn to the zero address", ContractErrors.INVALID_ADDRESS.toString());
    }
    // Check minter authorization - this sample assumes Org1 is the central banker with privilege to
    // burn new tokens
    this.authorizationHelper(ctx);
    // Get ID of submitting client identity
    String operator = ctx.getClientIdentity().getId();
    // Burn tokens
    this.removeBalance(ctx, account, new long[] {id}, new long[] {amount});
    TransferSingle transferSingleEvent =
        new TransferSingle(
            operator, account, ContractConstants.ZERO_ADDRESS.getValue(), id, amount);
    this.emitTransferSingle(ctx, transferSingleEvent);
  }

  /**
   * Batched version of Burn.
   *
   * @param ctx the transaction context
   * @param account the account from where to burn token
   * @param ids array of token ids
   * @param amounts array of amount to burn corresponds to the token ids
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void BurnBatch(
      final Context ctx, final String account, final long[] ids, final long[] amounts) {

    // check if contract has been initialized first
    this.checkInitialized(ctx);
    if (account.equalsIgnoreCase(ContractConstants.ZERO_ADDRESS.getValue())) {
      throw new ChaincodeException(
          "burn to the zero address", ContractErrors.INVALID_ADDRESS.toString());
    }
    if (ids.length != amounts.length) {
      throw new ChaincodeException(
          "ids and amounts must have the same length", ContractErrors.INVALID_INPUT.toString());
    }

    // Check minter authorization - this sample assumes Org1 is the central banker with privilege to
    // burn new tokens
    this.authorizationHelper(ctx);

    // Get ID of submitting client identity
    String operator = ctx.getClientIdentity().getId();
    this.removeBalance(ctx, account, ids, amounts);
    TransferBatch transferBatchEvent =
        new TransferBatch(
            operator, account, ContractConstants.ZERO_ADDRESS.getValue(), ids, amounts);
    this.emitTransferBatch(ctx, transferBatchEvent);
  }

  /**
   * TransferFrom transfers tokens from sender account to recipient account. recipient account must
   * be a valid clientID as returned by the ClientID() function This function triggers a
   * TransferSingle event
   *
   * @param ctx
   * @param sender
   * @param recipient
   * @param id
   * @param amount
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void TransferFrom(
      final Context ctx,
      final String sender,
      final String recipient,
      final long id,
      final long amount) {

    // check if contract has been initialized first
    this.checkInitialized(ctx);

    if (sender.equalsIgnoreCase(recipient)) {

      throw new ChaincodeException("transfer to self", ContractErrors.INVALID_INPUT.toString());
    }

    if (recipient.equalsIgnoreCase(ContractConstants.ZERO_ADDRESS.getValue())) {

      throw new ChaincodeException(
          "transfer to the zero address", ContractErrors.INVALID_ADDRESS.toString());
    }

    // Get ID of submitting client identity
    String operator = ctx.getClientIdentity().getId();

    // Check whether operator is owner or approved
    if (!operator.equalsIgnoreCase(sender)) {
      boolean approved = _isApprovedForAll(ctx, sender, operator);

      if (!approved) {
        throw new ChaincodeException(
            "caller is not owner nor is approved", ContractErrors.UNAUTHORIZED_SENDER.toString());
      }
    }

    // Withdraw the funds from the sender address
    this.removeBalance(ctx, sender, new long[] {id}, new long[] {amount});

    // Deposit the fund to the recipient address
    this.addBalance(ctx, sender, recipient, id, amount);

    // Emit TransferSingle event
    TransferSingle transferSingleEvent =
        new TransferSingle(operator, sender, recipient, id, amount);
    this.emitTransferSingle(ctx, transferSingleEvent);
  }

  /**
   * BatchTransferFrom transfers multiple tokens from sender account to recipient account recipient
   * account must be a valid clientID as returned by the ClientID() function This function triggers
   * a TransferBatch event
   *
   * @param ctx the transaction context
   * @param sender the sender account id
   * @param recipient account id of the recipient
   * @param ids token ids
   * @param amounts amount to transfer for different token ids
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void BatchTransferFrom(
      final Context ctx,
      final String sender,
      final String recipient,
      final long[] ids,
      final long[] amounts) {

    this.checkInitialized(ctx); // check if contract has been initialized first
    if (sender.equalsIgnoreCase(recipient)) {
      throw new ChaincodeException("transfer to self", ContractErrors.INVALID_INPUT.toString());
    }
    if (ids.length != amounts.length) {

      throw new ChaincodeException(
          "ids and amounts must have the same length", ContractErrors.INVALID_INPUT.toString());
    }

    String operator = ctx.getClientIdentity().getId(); // Get ID of submitting client identity

    // Check whether operator is owner or approved
    if (!operator.equalsIgnoreCase(sender)) {
      boolean approved = _isApprovedForAll(ctx, sender, operator);

      if (!approved) {

        throw new ChaincodeException(
            "caller is not owner nor is approved", ContractErrors.UNAUTHORIZED_SENDER.toString());
      }
    }

    if (recipient.equalsIgnoreCase(ContractConstants.ZERO_ADDRESS.getValue())) {
      throw new ChaincodeException(
          "transfer to the zero address", ContractErrors.INVALID_ADDRESS.toString());
    }
    this.removeBalance(ctx, sender, ids, amounts); // Withdraw the funds from the sender address

    // Group amount by token id because we can only send token to a recipient only one time in a
    // block. This prevents key conflicts
    Map<Long, Long> amountToSend = new HashMap<>(); // token id => amount
    for (int i = 0; i < amounts.length; i++) {
      amountToSend.put(ids[i], amountToSend.getOrDefault(ids[i], 0L) + amounts[i]);
    }
    // Copy the map keys and sort it. This is necessary because iterating maps in Go is not
    // deterministic
    List<Long> amountToSendKeys = sortedKeys(amountToSend);
    // Deposit the funds to the recipient address

    for (Long id : amountToSendKeys) {
      long amount = amountToSend.get(id);
      this.addBalance(ctx, sender, recipient, id, amount);
    }

    TransferBatch transferBatchEvent = new TransferBatch(operator, sender, recipient, ids, amounts);
    this.emitTransferBatch(ctx, transferBatchEvent);
  }

  /**
   * BatchTransferFromMultiRecipient transfers multiple tokens from sender account to multiple
   * recipient accounts recipient account must be a valid clientID as returned by the ClientID()
   * function This function triggers a TransferBatchMultiRecipient event
   *
   * @param ctx the transaction context
   * @param sender account id of the sender
   * @param recipients account id of the all the recipients
   * @param ids all the token Ids
   * @param amounts amount of token for each token id in the id array.
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void BatchTransferFromMultiRecipient(
      final Context ctx,
      final String sender,
      final String[] recipients,
      final long[] ids,
      final long[] amounts) {

    // check if contract has been initialized first
    this.checkInitialized(ctx);

    if ((recipients.length != ids.length) || (ids.length != amounts.length)) {
      throw new ChaincodeException(
          "recipients, ids, and amounts must have the same length",
          ContractErrors.INVALID_INPUT.toString());
    }

    for (String recipient : recipients) {
      if (sender.equalsIgnoreCase(recipient)) {
        throw new ChaincodeException("transfer to self", ContractErrors.INVALID_ADDRESS.toString());
      }
    }

    // Get ID of submitting client identity
    String operator = ctx.getClientIdentity().getId();

    // Check whether operator is owner or approved
    if (!operator.equalsIgnoreCase(sender)) {
      boolean approved = _isApprovedForAll(ctx, sender, operator);

      if (!approved) {
        throw new ChaincodeException(
            "caller is not owner nor is approved", ContractErrors.INVALID_ADDRESS.toString());
      }
    }

    // Withdraw the funds from the sender address
    this.removeBalance(ctx, sender, ids, amounts);

    // Group amount by (recipient, id ) pair because we can only send token to a recipient only one
    // time in a block. This prevents key conflicts
    Map<ToID, Long> amountToSend = new HashMap<>(); // (recipient, id ) => amount
    for (int i = 0; i < amounts.length; i++) {
      ToID key = new ToID(recipients[i], ids[i]);
      amountToSend.put(key, amountToSend.getOrDefault(key, 0L) + amounts[i]);
    }

    // Copy the map keys and sort it. This is necessary because iterating maps in Go is not
    // deterministic
    List<ToID> amountToSendKeys = sortedKeysToID(amountToSend);

    // Deposit the funds to the recipient addresses
    for (ToID amountoSendKey : amountToSendKeys) {
      if (amountoSendKey.getTo().equalsIgnoreCase(ContractConstants.ZERO_ADDRESS.getValue())) {

        throw new ChaincodeException(
            "transfer to the zero address", ContractErrors.INVALID_ADDRESS.toString());
      }

      long amount = amountToSend.get(amountoSendKey);

      this.addBalance(ctx, sender, amountoSendKey.getTo(), amountoSendKey.getId(), amount);
    }

    // Emit TransferBatchMultiRecipient event
    TransferBatchMultiRecipient transferBatchMultiRecipientEvent =
        new TransferBatchMultiRecipient(operator, sender, recipients, ids, amounts);
    this.emitTransferBatchMultiRecipient(ctx, transferBatchMultiRecipientEvent);
  }

  /**
   * IsApprovedForAll returns true if operator is approved to transfer account's tokens.
   *
   * @param ctx the transaction context
   * @param account account id from where token transferred
   * @param operator the operator of the account
   * @return
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public boolean IsApprovedForAll(final Context ctx, final String account, final String operator) {
    return this._isApprovedForAll(ctx, account, operator);
  }

  /**
   * Helper function for IsApprovedForAll
   *
   * @param ctx the transaction context
   * @param account account id from where token transferred
   * @param operator the operator of the account
   * @return
   */
  private boolean _isApprovedForAll(
      final Context ctx, final String account, final String operator) {

    // check if contract has been initialized first
    this.checkInitialized(ctx);
    CompositeKey approvalKey =
        ctx.getStub()
            .createCompositeKey(ContractConstants.APPROVAL_PREFIX.getValue(), account, operator);

    String approved = ctx.getStub().getStringState(approvalKey.toString());

    if (stringIsNullOrEmpty(approved)) {
      return false;
    }

    return new Boolean(approved);
  }

  /**
   * SetApprovalForAll returns true if operator is approved to transfer account's tokens. Emits an
   * ApprovalForAll event.
   *
   * @param ctx the transaction context
   * @param operator get the permission to transfer account's tokens.
   * @param approved
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void SetApprovalForAll(final Context ctx, final String operator, final boolean approved) {

    // check if contract has been initialized first
    this.checkInitialized(ctx);

    // Get ID of submitting client identity
    String account = ctx.getClientIdentity().getId();
    if (account.equalsIgnoreCase(operator)) {
      throw new ChaincodeException(
          "setting approval status for self", ContractErrors.INVALID_INPUT.toString());
    }

    CompositeKey approvalKey =
        ctx.getStub()
            .createCompositeKey(ContractConstants.APPROVAL_PREFIX.getValue(), account, operator);
    ctx.getStub().putStringState(approvalKey.toString(), String.valueOf(approved));
    ApprovalForAll approvalForAllEvent = new ApprovalForAll(account, operator, approved);
    ctx.getStub()
        .setEvent(
            ContractConstants.APPROVE_FOR_ALL_EVENT.getValue(), this.marshal(approvalForAllEvent));
  }

  /**
   * BalanceOf returns the balance of the given account
   *
   * @param ctx the transaction context
   * @param account the account id
   * @param id the given token id
   * @return
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public long BalanceOf(final Context ctx, final String account, final long id) {

    // check if contract has been initialized first
    this.checkInitialized(ctx);
    return balanceOfHelper(ctx, account, id);
  }

  /**
   * BalanceOfBatch returns the balance of multiple account/token pairs
   *
   * @param ctx the transaction context
   * @param accounts the account ids
   * @param ids the given token ids
   * @return
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public long[] BalanceOfBatch(final Context ctx, final String[] accounts, final long[] ids) {

    // check if contract has been initialized first
    this.checkInitialized(ctx);
    if (accounts.length != ids.length) {
      throw new ChaincodeException(
          "ids and amounts must have the same length", ContractErrors.INVALID_INPUT.toString());
    }

    long[] balances = new long[accounts.length];

    for (int i = 0; i < accounts.length; i++) {

      balances[i] = balanceOfHelper(ctx, accounts[i], ids[i]);
    }

    return balances;
  }

  /**
   * ClientAccountBalance returns the balance of the requesting client's account
   *
   * @param ctx the transaction context
   * @param id token id
   * @return
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public long ClientAccountBalance(final Context ctx, final long id) {

    // check if contract has been initialized first
    this.checkInitialized(ctx);

    // Get ID of submitting client identity
    String clientID = ctx.getClientIdentity().getId();

    return balanceOfHelper(ctx, clientID, id);
  }

  /**
   * ClientAccountID returns the id of the requesting client's account.In this implementation, the
   * client account ID is the clientId itself Users can use this function to get their own account
   * id, which they can then give to others as the payment address.
   *
   * @param ctx the transaction context
   * @return
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public String ClientAccountID(final Context ctx) {
    // check if contract has been initialized first
    this.checkInitialized(ctx);
    // Get ID of submitting client identity
    String clientAccountID = ctx.getClientIdentity().getId();
    return clientAccountID;
  }

  /**
   * SetURI set the URI value. This function triggers URI event for each token id .This
   * implementation returns the same URI for all token types. It relies on the token type ID
   * substitution mechanism .
   *
   * @param ctx the transaction context
   * @param uri the token URI
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void SetURI(final Context ctx, final String uri) {

    // check if contract has been initialized first
    this.checkInitialized(ctx);

    // Check minter authorization - this sample assumes Org1 is the central banker with privilege to
    // mint new tokens
    this.authorizationHelper(ctx);
    if (uri.indexOf("{id}") < 0) {
      throw new ChaincodeException(
          "failed to set uri, uri should contain '{id}'", ContractErrors.INVALID_URI.toString());
    }

    ctx.getStub().putStringState(ContractConstants.URI_KEY.getValue(), uri);
  }

  /**
   * Returns the URI.This implementation returns the same URI for *all* token types. It relies on
   * the token type ID substitution mechanism. Clients calling this function must replace the
   * `\{id\}` substring with the actual token type ID.
   *
   * @param ctx the transaction context
   * @param id tokenid
   * @return
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public String URI(final Context ctx, final long id) {
    // check if contract has been initialized first
    final ChaincodeStub stub = ctx.getStub();
    String uri = stub.getStringState(ContractConstants.URI_KEY.getValue());
    if (stringIsNullOrEmpty(uri)) {
      throw new ChaincodeException("no uri is set", ContractErrors.NOT_FOUND.toString());
    }
    return uri;
  }

  /**
   * @param ctx
   * @param id
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public void BroadcastTokenExistance(final Context ctx, final long id) {
    // check if contract has been initialized first
    this.checkInitialized(ctx);
    // Check minter authorization - this sample assumes Org1 is the central banker with privilege to
    // mint new tokens
    this.authorizationHelper(ctx);
    String operator = ctx.getClientIdentity().getId();
    TransferSingle transferSingleEvent =
        new TransferSingle(
            operator,
            ContractConstants.ZERO_ADDRESS.getValue(),
            ContractConstants.ZERO_ADDRESS.getValue(),
            id,
            0);
    this.emitTransferSingle(ctx, transferSingleEvent);
  }

  /**
   * Returns a descriptive name for a collection of non-fungible tokens in this contract
   *
   * @param ctx the transaction context
   * @return name of the token.
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public String Name(final Context ctx) {
    // check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    return ctx.getStub().getStringState(ContractConstants.NAME_KEY.getValue());
  }

  /**
   * Returns an abbreviated symbol for non-fungible tokens in this contract.
   *
   * @param ctx the transaction context
   * @return token symbol.
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public String Symbol(final Context ctx) {
    // check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    return ctx.getStub().getStringState(ContractConstants.SYMBOL_KEY.getValue());
  }

  /**
   * Initialize the token with name and symbol.
   *
   * @param ctx the transaction context
   * @param name token name
   * @param symbol token symbol
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void Initialize(final Context ctx, final String name, final String symbol) {

    final ChaincodeStub stub = ctx.getStub();
    final String clientMSPID = ctx.getClientIdentity().getMSPID();

    // Check minter authorization - this sample assumes Org1 is the issuer with privilege to set the
    // name and symbol
    if (!clientMSPID.equalsIgnoreCase(ContractConstants.MINTER_ORG_MSP.getValue())) {
      throw new ChaincodeException(
          "Client is not authorized to initialize the contract",
          ContractErrors.UNAUTHORIZED_SENDER.toString());
    }

    // check contract options are not already set, client is not authorized to change them once
    // initialized
    String tokenName = stub.getStringState(ContractConstants.NAME_KEY.getValue());
    if (!stringIsNullOrEmpty(tokenName)) {
      throw new ChaincodeException(
          "contract options are already set, client is not authorized to change them",
          ContractErrors.UNAUTHORIZED_SENDER.toString());
    }
    stub.putStringState(ContractConstants.NAME_KEY.getValue(), name);
    stub.putStringState(ContractConstants.SYMBOL_KEY.getValue(), symbol);
  }

  /**
   * This helper function checks minter authorization - this sample assumes Org1 is the central
   * banker with privilege to mint new tokens Throw exception if not initialized.
   *
   * @param ctx the transaction context
   * @return
   */
  private void authorizationHelper(final Context ctx) {
    String clientMSPID = ctx.getClientIdentity().getMSPID();
    if (!clientMSPID.equalsIgnoreCase(ContractConstants.MINTER_ORG_MSP.getValue())) {
      throw new ChaincodeException(
          "Client is not authorized to set the name and symbol of the token",
          ContractErrors.UNAUTHORIZED_SENDER.toString());
    }
  }

  /**
   * Update the balance of account for the given token id.
   *
   * @param ctx
   * @param account
   * @param id
   * @return
   */
  private long balanceOfHelper(final Context ctx, final String account, final long id) {

    if (account.equalsIgnoreCase(ContractConstants.ZERO_ADDRESS.getValue())) {
      throw new ChaincodeException(
          "balance query for the zero address", ContractErrors.INVALID_ADDRESS.toString());
    }

    // Convert id to string
    String idString = String.valueOf(id);
    long balance = 0;
    final QueryResultsIterator<KeyValue> balanceIterator =
        ctx.getStub()
            .getStateByPartialCompositeKey(
                ContractConstants.BALANCE_PREFIX.getValue(), account, idString);
    for (KeyValue keyVal : balanceIterator) {
      long balAmount = Long.parseLong(keyVal.getStringValue());
      balance = Math.addExact(balance, balAmount);
    }
    return balance;
  }

  /**
   * Helper function to add balance of recipient account
   *
   * @param ctx the transaction context
   * @param sender the sender account id
   * @param recipient the token recipient account id
   * @param id the token id
   * @param amount the amount of token
   */
  private void addBalance(
      final Context ctx,
      final String sender,
      final String recipient,
      final long id,
      final long amount) {
    ChaincodeStub stub = ctx.getStub();
    String idString = String.valueOf(id); // Convert id to string
    CompositeKey balanceKey =
        stub.createCompositeKey(
            ContractConstants.BALANCE_PREFIX.getValue(), recipient, idString, sender);
    String balanceStr = stub.getStringState(balanceKey.toString());
    long balance = 0;
    if (!stringIsNullOrEmpty(balanceStr)) {

      balance = Long.parseLong(balanceStr);
    }
    balance = Math.addExact(balance, amount);
    stub.putStringState(balanceKey.toString(), String.valueOf(balance));
    logger.info(String.format("account %s balance updated to %d", recipient, balance));
  }

  /**
   * Remove Balance of the sender for each given token id and amounts.
   *
   * @param ctx the transaction context
   * @param sender the owner of the token
   * @param ids each token ids
   * @param amounts the amount of the token to transfered for each id
   */
  private void removeBalance(
      final Context ctx, final String sender, final long[] ids, final long[] amounts) {

    // Calculate the total amount of each token to withdraw
    Map<Long, Long> necessaryFunds = new HashMap<Long, Long>(); // token id -> necessary amount
    for (int i = 0; i < amounts.length; i++) {
      necessaryFunds.put(ids[i], necessaryFunds.getOrDefault(i, 0L) + amounts[i]);
    }
    ChaincodeStub stub = ctx.getStub();
    // Copy the map keys and sort it. This is necessary because iterating maps in Go is not
    // deterministic
    List<Long> necessaryFundsKeys = sortedKeys(necessaryFunds);
    // Check whether the sender has the necessary funds and withdraw them from the account
    for (long tokenId : necessaryFundsKeys) {
      long neededAmount = necessaryFunds.get(tokenId);
      String idString = String.valueOf(tokenId);
      long partialBalance = 0;
      boolean selfRecipientKeyNeedsToBeRemoved = false;
      String selfRecipientKey = "";
      final QueryResultsIterator<KeyValue> balanceResulutIterator =
          ctx.getStub()
              .getStateByPartialCompositeKey(
                  ContractConstants.BALANCE_PREFIX.getValue(), sender, idString);
      Iterator<KeyValue> balanceIterator = balanceResulutIterator.iterator();
      while (balanceIterator.hasNext() && partialBalance < neededAmount) {
        KeyValue queryResponse = balanceIterator.next();
        long partBalAmount = Long.valueOf(queryResponse.getStringValue());
        partialBalance = Math.addExact(partialBalance, partBalAmount);
        CompositeKey compositeKeyParts = stub.splitCompositeKey(queryResponse.getKey());
        if (compositeKeyParts.getAttributes().contains(sender)) {
          selfRecipientKeyNeedsToBeRemoved = true;
          selfRecipientKey = queryResponse.getKey();
        } else {
          ctx.getStub().delState(queryResponse.getKey());
        }
      }
      if (partialBalance < neededAmount) {
        String error =
            String.format(
                "sender has insufficient funds for token %s, needed funds: %d, available fund: %d",
                tokenId, neededAmount, partialBalance);
        throw new ChaincodeException(error, ContractErrors.INSUFFICIENT_FUND.toString());

      } else if (partialBalance > neededAmount) {

        // Send the remainder back to the sender
        long remainder = Math.subtractExact(partialBalance, neededAmount);

        if (selfRecipientKeyNeedsToBeRemoved) {

          // Set balance for the key that has the same address for sender and recipient
          this.setBalance(ctx, sender, sender, tokenId, remainder);

        } else {

          this.addBalance(ctx, sender, sender, tokenId, remainder);
        }

      } else {

        // Delete self recipient key
        stub.delState(selfRecipientKey);
      }
    }
  }

  /**
   * Helper function for token mint call.
   *
   * @param ctx the transaction context
   * @param operator the approved operator of the token
   * @param account account id updated with token
   * @param id the token id
   * @param amount quantity of token to be minted
   */
  private void mintHelper(
      final Context ctx,
      final String operator,
      final String account,
      final long id,
      final long amount) {

    if (account.equalsIgnoreCase(ContractConstants.ZERO_ADDRESS.getValue())) {

      throw new ChaincodeException(
          "mint to the zero address", ContractErrors.INVALID_ADDRESS.toString());
    }

    if (amount <= 0) {

      throw new ChaincodeException(
          "Mint amount must be a positive integer", ContractErrors.INVALID_AMOUNT.toString());
    }

    this.addBalance(ctx, operator, account, id, amount);
  }

  private void setBalance(
      final Context ctx,
      final String sender,
      final String recipient,
      final long id,
      final long amount) {
    // Convert id to string
    String idString = String.valueOf(id);
    CompositeKey balanceKey =
        ctx.getStub()
            .createCompositeKey(
                ContractConstants.BALANCE_PREFIX.getValue(), recipient, idString, sender);
    ctx.getStub().putStringState(balanceKey.toString(), String.valueOf(amount));
  }

  /**
   * Emit when single recipient token transfer occur.
   *
   * @param ctx
   * @param transferSingleEvent
   */
  private void emitTransferSingle(final Context ctx, final TransferSingle transferSingleEvent) {

    ctx.getStub()
        .setEvent(
            ContractConstants.TRANSFER_SINGLE_EVENT.getValue(), this.marshal(transferSingleEvent));
  }

  /**
   * Emit transfer batch event.
   *
   * @param ctx the transaction context
   * @param transferBatchEvent BatchEvent details
   */
  private void emitTransferBatch(final Context ctx, final TransferBatch transferBatchEvent) {

    ctx.getStub()
        .setEvent(
            ContractConstants.TRANSFER_BATCH_EVENT.getValue(), this.marshal(transferBatchEvent));
  }

  /**
   * EmitTransferBatchMultiRecipient when multi recipient token batch transfer occurs.
   *
   * @param ctx the transaction context
   * @param transferBatchMutipleRecipientEvent event details
   */
  private void emitTransferBatchMultiRecipient(
      final Context ctx, final TransferBatchMultiRecipient transferBatchMutipleRecipientEvent) {

    ctx.getStub()
        .setEvent(
            ContractConstants.TRANSFER_BATCH_MULTI_RECIPIENT_EVENT.getValue(),
            this.marshal(transferBatchMutipleRecipientEvent));
  }

  /**
   * Returns the sorted array from the keys of map
   *
   * @param map the keys of map data to be sorted
   * @return
   */
  private List<Long> sortedKeys(final Map<Long, Long> map) {
    List<Long> sortedKeys = new ArrayList<>();
    sortedKeys.addAll(map.keySet());
    Collections.sort(sortedKeys);
    return sortedKeys;
  }

  /**
   * Returns the sorted slice ([]ToID) copied from the keys of map[ToID]uint64
   *
   * @param the keys of map data to be sorted
   * @return
   */
  private List<ToID> sortedKeysToID(final Map<ToID, Long> map) {

    List<ToID> sortedKeys = new ArrayList<>();
    sortedKeys.addAll(map.keySet());
    Collections.sort(
        sortedKeys,
        new Comparator<ToID>() {
          public int compare(final ToID toId1, final ToID toId2) {
            if (toId1.getId() != toId2.getId()) {
              return toId1.getTo().compareTo(toId2.getTo());
            }

            return new Long(toId1.getId()).compareTo(toId2.getId());
          }
        });

    return sortedKeys;
  }

  /**
   * Checks that contract options have been already initialized Throw exception if not initialized.
   *
   * @param ctx the transaction context
   * @return
   */
  private void checkInitialized(final Context ctx) {
    String tokenName = ctx.getStub().getStringState(ContractConstants.NAME_KEY.getValue());
    if (stringIsNullOrEmpty(tokenName)) {
      throw new ChaincodeException(
          "Contract options need to be set before calling any function, call Initialize() to initialize contract",
          ContractErrors.TOKEN_NOT_FOUND.toString());
    }
  }

  /**
   * marshal the event data
   *
   * @param obj the object to marshal.
   * @return marshalled object.
   */
  private byte[] marshal(final Object obj) {
    return new Genson().serialize(obj).getBytes(UTF_8);
  }
}
