/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.example;

import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.*;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.CompositeKey;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.json.JSONObject;

import com.google.common.base.Strings;

@Contract(name = "TokenERC20Contract", info = @Info(title = "TokenERC20Contract", description = "A java chaincode for erc20 token", version = "0.0.1-SNAPSHOT"))

@Default
public final class TokenERC20Contract implements ContractInterface {

  private static final String BALANCE_PREFIX = "balance";
  private static final String ALLOWANCE_PREFIX = "allowance";
  private static final String NAME_KEY = "name";
  private static final String SYMBOL_KEY = "symbol";
  private static final String DECIMALS_KEY = "decimals";
  private static final String TOTAL_SUPPLY_KEY = "totalSupply";
  private static final String TRANSFER_EVENT = "Transfer";
  private static final String FROM = "from";
  private static final String TO = "to";
  private static final String VALUE= "value";
  private static final String ERC20_OWNER_MSPID= "Org1MSP";
  

  /**
   * @Desc Return the name of the token - e.g. "MyToken". The original function name is `name` in
   * ERC20 specification. However, 'name' conflicts with a parameter `name` in `Contract` class. As
   * a work around, we use `TokenName` as an alternative function name.
   * @param ctx the transaction context
   * @returns Returns the name of the token
   */

  @Transaction()
  public String tokenName(final Context ctx) {

    String tokenName = ctx.getStub().getStringState(NAME_KEY);

    if (Strings.isNullOrEmpty(tokenName)) {

      throw new ChaincodeException("Sorry ! Token name not found.");
    }

    return tokenName;

  }

  /**
   * @Desc Return the symbol of the token. E.g. â€œHIXâ€??.
   * @param ctx the transaction context
   * @returns Returns the symbol of the token
   */
  @Transaction()
  public String tokenSymbol(final Context ctx) {
    String tokenSymbol = ctx.getStub().getStringState(SYMBOL_KEY);
    if (Strings.isNullOrEmpty(tokenSymbol)) {
      throw new ChaincodeException("Sorry ! Token symbol not found.");
    }

    return tokenSymbol;

  }

  /**
   * @Desc Return the number of decimals the token uses e.g. 8, means to divide the token amount by
   * 100000000 to get its user representation.
   * @param ctx the transaction context
   * @returns Returns the number of decimals
   */
  @Transaction()
  public int decimals(final Context ctx) {

    String decimals = ctx.getStub().getStringState(DECIMALS_KEY);
    if (Strings.isNullOrEmpty(decimals)) {

      throw new ChaincodeException("Sorry ! Decimal not found.");
    }
    return Integer.parseInt(decimals);
  }

  /**
   * @Desc Return the total token supply.
   * @param ctx the transaction context
   * @returns Returns the total token supply
   */
  @Transaction()
  public long totalSupply(final Context ctx) {

    String totalSupply = ctx.getStub().getStringState(TOTAL_SUPPLY_KEY);
    if (Strings.isNullOrEmpty(totalSupply)) {

      throw new ChaincodeException("Sorry ! Total Supply  not found.");
    }
    return Long.parseLong(totalSupply);
  }

  /**
   * BalanceOf returns the balance of the given account.
   * 
   * @param ctx the transaction context
   * @param owner The owner from which the balance will be retrieved
   * @returns Returns the account balance
   */
  @Transaction()
  public long balanceOf(final Context ctx, final String owner) {

    ChaincodeStub stub = ctx.getStub();
    CompositeKey balanceKey = ctx.getStub().createCompositeKey(BALANCE_PREFIX, owner);
    String balance = stub.getStringState(balanceKey.toString());
    if (Strings.isNullOrEmpty(balance)) {
      String errorMessage = String.format("Balance of the owner  %s not exists", owner);
      throw new ChaincodeException(errorMessage);
    }
    return Long.parseLong(balance);

  }

  /**
   * @Desc Transfer transfers tokens from client account to recipient account. recipient account
   * must be a valid clientID as returned by the ClientAccountID() function.
   * 
   * @param ctx the transaction context
   * @param to The recipient
   * @param value The amount of token to be transferred
   * @returns Return whether the transfer was successful or not
   */
  @Transaction()
  public void transfer(final Context ctx, final String to, long _value) {

    String from = ctx.getClientIdentity().getId();
    this.doTransfer(ctx, from, to, _value);
    ctx.getStub().setEvent(TRANSFER_EVENT,  new JSONObject().put(FROM, from).put(TO, to)
        .put(VALUE, _value).toString().getBytes(UTF_8));

  }

  /**
   * Transfer `value` amount of tokens from `from` to `to`.
   *
   * @param ctx the transaction context
   * @param from The sender
   * @param to The recipient
   * @param value The amount of token to be transferred
   * @returns Return whether the transfer was successful or not
   */
  @Transaction()
  public void transferFrom(Context ctx, final String from, final String to, String _value) {

    String spender = ctx.getClientIdentity().getId();
    ChaincodeStub stub = ctx.getStub();
    // Retrieve the allowance of the spender
    CompositeKey allowanceKey = stub.createCompositeKey(ALLOWANCE_PREFIX, from, spender);
    String currentAllowanceStr = stub.getStringState(allowanceKey.toString());
    if (Strings.isNullOrEmpty(currentAllowanceStr)) {
      String errorMessage = String.format("Spender %s has no allowance from %s", spender, from);
      throw new ChaincodeException(errorMessage);
    }
    long currentAllowance = Long.parseLong(currentAllowanceStr);

    // Convert value from string to int
    long valueInt = Long.parseLong(_value);

    // Check if the transferred value is less than the allowance
    if (currentAllowance < valueInt) {

      String errorMessage = String.format("The spender does not have enough allowance to spend.");
      throw new ChaincodeException(errorMessage);

    }

    this.doTransfer(ctx, from, to, valueInt);

    // Decrease the allowance
    long updatedAllowance = currentAllowance - valueInt;
    stub.putStringState(allowanceKey.toString(), String.valueOf(updatedAllowance));
    stub.setEvent(TRANSFER_EVENT, new JSONObject().put(FROM, from).put(TO, to)
        .put(VALUE, valueInt).toString().getBytes(UTF_8));

  }

  private void doTransfer(final Context ctx, final String _from, final String _to, long _value) {

    if (_from.equalsIgnoreCase(_to)) {
      throw new ChaincodeException("cannot transfer to and from same client account");
    }

    if (_value < 0) { // transfer of 0 is allowed in ERC20, so just validate against negative
      // amounts
      throw new ChaincodeException("transfer amount cannot be negative");
    }

    ChaincodeStub stub = ctx.getStub();
    // Retrieve the current balance of the sender
    CompositeKey fromBalanceKey = stub.createCompositeKey(BALANCE_PREFIX, _from);

    String fromCurrentBalance = stub.getStringState(fromBalanceKey.toString());

    if (Strings.isNullOrEmpty(fromCurrentBalance)) {
      String errorMessage = String.format("client account %s has no balance", _from);
      throw new ChaincodeException(errorMessage);

    }

    long _fromCurrentBalance = Long.parseLong(fromCurrentBalance.toString());

    // Check if the sender has enough tokens to spend.
    if (_fromCurrentBalance < _value) {
      String errorMessage = String.format("client account %s has insufficient funds", _from);
      throw new ChaincodeException(errorMessage);
    }

    // Retrieve the current balance of the recepient
    CompositeKey toBalanceKey = stub.createCompositeKey(BALANCE_PREFIX, _to);
    String toCurrentBalance = stub.getStringState(toBalanceKey.toString());

    long _toCurrentBalance = 0;
    // If recipient current balance doesn't yet exist, we'll create it with a
    // current balance of 0
    if (Strings.isNullOrEmpty(toCurrentBalance)) {
      _toCurrentBalance = 0;
    } else {
      _toCurrentBalance = Long.parseLong(toCurrentBalance.trim());
    }

    // Update the balance
    long fromUpdatedBalance = _fromCurrentBalance - _value;
    long toUpdatedBalance = _toCurrentBalance + _value;

    stub.putStringState(fromBalanceKey.toString(), String.valueOf(fromUpdatedBalance));

    stub.putStringState(toBalanceKey.toString(), String.valueOf(toUpdatedBalance));

  }

  /**
   * @Desc Allows `spender` to spend `value` amount of tokens from the owner.
   *
   * @param ctx the transaction context
   * @param spender The spender
   * @param value The amount of tokens to be approved for transfer
   * @returns Return whether the approval was successful or not
   */
  @Transaction()
  public void approve(final Context ctx, final String spender, final String value) {

    String owner = ctx.getClientIdentity().getId();
    ChaincodeStub stub = ctx.getStub();
    CompositeKey allowanceKey = stub.createCompositeKey(ALLOWANCE_PREFIX, owner, spender);
    long valueInt = Long.parseLong(value);
    stub.putStringState(allowanceKey.toString(), String.valueOf(valueInt));
    stub.setEvent("Approval",  new JSONObject().put("owner", owner).put("spender", spender)
        .put(VALUE, valueInt).toString().getBytes(UTF_8));

  }

  /**
   * @Desc Returns the amount of tokens which `spender` is allowed to withdraw from `owner`.
   *
   * @param ctx the transaction context
   * @param owner The owner of tokens
   * @param spender The spender who are able to transfer the tokens
   * @returns Return the amount of remaining tokens allowed to spent
   */

  @Transaction()
  public long allowance(final Context ctx, final String owner, final String spender) {

    ChaincodeStub stub = ctx.getStub();

    CompositeKey allowanceKey = stub.createCompositeKey(ALLOWANCE_PREFIX, owner, spender);
    String allowanceBytes = stub.getStringState(allowanceKey.toString());

    if (Strings.isNullOrEmpty(allowanceBytes)) {

      String errorMessage = String.format("spender account %s has no allowance from", spender,
          owner);
      throw new ChaincodeException(errorMessage);
    }

    long allowance = Long.parseLong(allowanceBytes);
    return allowance;
  }

  /**
   * @Desc Set optional information for a token.
   *
   * @param ctx the transaction context
   * @param name The name of the token
   * @param symbol The symbol of the token
   * @param decimals The decimals of the token
   * @param totalSupply The totalSupply of the token
   */
  @Transaction()
  public void setOptions(final Context ctx, final String name, final String symbol,
      final String decimals) {
    ChaincodeStub stub = ctx.getStub();
    stub.putStringState(NAME_KEY, name);
    stub.putStringState(SYMBOL_KEY, symbol);
    stub.putStringState(DECIMALS_KEY, decimals);

  }

  /**
   * Mint creates new tokens and adds them to minter's account balance
   *
   * @param ctx the transaction context
   * @param amount amount of tokens to be minted
   * @returns The balance
   */
  @Transaction()
  public void mint(final Context ctx, final String amount) {

    // Check minter authorization - this sample assumes Org1 is the central banker
    // with privilege to mint new tokens

    String clientMSPID = ctx.getClientIdentity().getMSPID();
    ChaincodeStub stub = ctx.getStub();
    if (!clientMSPID.equalsIgnoreCase(ERC20_OWNER_MSPID)) {
      throw new ChaincodeException("Client is not authorized to mint new tokens");
    }

    // Get ID of submitting client identity
    String minter = ctx.getClientIdentity().getId();
    long amountInt = Long.parseLong(amount.trim());
    if (amountInt <= 0) {
      throw new ChaincodeException("Mint amount must be a positive integer");
    }

    CompositeKey balanceKey = stub.createCompositeKey(BALANCE_PREFIX, minter);

    String currentBalanceBytes = stub.getStringState(balanceKey.toString());
    // If minter current balance doesn't yet exist, we'll create it with a current
    // balance of 0
    long currentBalance = 0;

    if (Strings.isNullOrEmpty(currentBalanceBytes)) {

      currentBalance = 0;

    } else {

      currentBalance = Long.parseLong(currentBalanceBytes);

    }
    long updatedBalance = currentBalance + amountInt;

    stub.putStringState(balanceKey.toString(), String.valueOf(updatedBalance));

    // Increase totalSupply
    String totalSupplyBytes = stub.getStringState(TOTAL_SUPPLY_KEY);
    long totalSupply = 0;
    if (Strings.isNullOrEmpty(totalSupplyBytes)) {

      totalSupply = 0;

    } else {

      totalSupply = Long.parseLong(totalSupplyBytes.toString());
    }

    totalSupply = totalSupply + amountInt;
    stub.putStringState(TOTAL_SUPPLY_KEY, String.valueOf(totalSupply));
    stub.setEvent(TRANSFER_EVENT,  new JSONObject().put(FROM, "0x0").put(TO, minter)
        .put(VALUE, amountInt).toString().getBytes(UTF_8));

  }

  /**
   * @Desc Burn redeem tokens from minter's account balance.
   * @param ctx the transaction context
   * @param amount amount of tokens to be burned
   * @returns The balance
   */
  @Transaction()
  public void burn(final Context ctx, final String amount) {

    // Check minter authorization - this sample assumes Org1 is the central banker
    // with privilege to burn tokens
    String clientMSPID = ctx.getClientIdentity().getMSPID();
    ChaincodeStub stub = ctx.getStub();
    if (!clientMSPID.equalsIgnoreCase(ERC20_OWNER_MSPID)) {
      throw new ChaincodeException("client is not authorized to mint new tokens");
    }

    String minter = ctx.getClientIdentity().getId();

    long amountInt = Long.parseLong(amount);

    CompositeKey balanceKey = stub.createCompositeKey(BALANCE_PREFIX, minter);

    String currentBalanceBytes = stub.getStringState(balanceKey.toString());
    if (Strings.isNullOrEmpty(currentBalanceBytes)) {
      throw new ChaincodeException("The balance does not exist");
    }
    long currentBalance = Long.valueOf(currentBalanceBytes);
    long updatedBalance = currentBalance - amountInt;

    stub.putStringState(balanceKey.toString(), String.valueOf(updatedBalance));

    // Decrease totalSupply
    String totalSupplyBytes = stub.getStringState(TOTAL_SUPPLY_KEY);
    if (Strings.isNullOrEmpty(totalSupplyBytes)) {
      throw new ChaincodeException("totalSupply does not exist.");
    }
    long totalSupply = Long.parseLong(totalSupplyBytes.toString()) - amountInt;
    stub.putStringState(TOTAL_SUPPLY_KEY, String.valueOf(totalSupply));

    // Emit the Transfer event

    stub.setEvent(TRANSFER_EVENT, new JSONObject().put(FROM, minter).put(TO, "0x0")
        .put(VALUE, amountInt).toString().getBytes(UTF_8));

  }

  /**
   * @Desc: ClientAccountBalance returns the balance of the requesting client's account.
   *
   * @param ctx the transaction context
   * @returns Returns the account balance
   */

  @Transaction()
  public long getClientAccountBalance(final Context ctx) {
    // Get ID of submitting client identity
    ChaincodeStub stub = ctx.getStub();
    String clientAccountID = ctx.getClientIdentity().getId();
    CompositeKey balanceKey = stub.createCompositeKey(BALANCE_PREFIX, clientAccountID);
    String balanceBytes = stub.getStringState(balanceKey.toString());
    if (Strings.isNullOrEmpty(balanceBytes)) {

      String errorMessage = String.format("the account  %s does not exist", clientAccountID);
      throw new ChaincodeException(errorMessage);
    }
    long balance = Long.parseLong(balanceBytes);

    return balance;
  }

  /**
   * @Desc: ClientAccountID returns the id of the requesting client's account. In this
   * implementation, the client account ID is the clientId itself. Users can use this function to
   * get their own account id, which they can then give to others as the payment address.
   * 
   */

  @Transaction()
  public String getClientAccountID(final Context ctx) {
    // Get ID of submitting client identity
    String clientAccountID = ctx.getClientIdentity().getId();
    return clientAccountID;
  }

}
