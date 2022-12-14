/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.fabric.samples.erc20;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.fabric.samples.erc20.ContractConstants.ALLOWANCE_PREFIX;
import static org.hyperledger.fabric.samples.erc20.ContractConstants.APPROVAL;
import static org.hyperledger.fabric.samples.erc20.ContractConstants.BALANCE_PREFIX;
import static org.hyperledger.fabric.samples.erc20.ContractConstants.DECIMALS_KEY;
import static org.hyperledger.fabric.samples.erc20.ContractConstants.NAME_KEY;
import static org.hyperledger.fabric.samples.erc20.ContractConstants.SYMBOL_KEY;
import static org.hyperledger.fabric.samples.erc20.ContractConstants.TOTAL_SUPPLY_KEY;
import static org.hyperledger.fabric.samples.erc20.ContractConstants.TRANSFER_EVENT;
import static org.hyperledger.fabric.samples.erc20.ContractErrors.BALANCE_NOT_FOUND;
import static org.hyperledger.fabric.samples.erc20.ContractErrors.INSUFFICIENT_FUND;
import static org.hyperledger.fabric.samples.erc20.ContractErrors.INVALID_AMOUNT;
import static org.hyperledger.fabric.samples.erc20.ContractErrors.INVALID_TRANSFER;
import static org.hyperledger.fabric.samples.erc20.ContractErrors.NOT_FOUND;
import static org.hyperledger.fabric.samples.erc20.ContractErrors.NO_ALLOWANCE_FOUND;
import static org.hyperledger.fabric.samples.erc20.ContractErrors.UNAUTHORIZED_SENDER;
import static org.hyperledger.fabric.samples.erc20.utils.ContractUtility.stringIsNullOrEmpty;

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
import org.hyperledger.fabric.samples.erc20.model.Approval;
import org.hyperledger.fabric.samples.erc20.model.Transfer;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.CompositeKey;

@Contract(
    name = "erc20token",
    info =
        @Info(
            title = "ERC20Token Contract",
            description = "The erc20 fungible token implementation.",
            version = "0.0.1-SNAPSHOT",
            license =
                @License(
                    name = "Apache 2.0 License",
                    url = "http://www.apache.org/licenses/LICENSE-2.0.html"),
            contact =
                @Contact(
                    email = "renjithkn@gmail.com",
                    name = "Renjith Narayanan",
                    url = "https://hyperledger.example.com")))
@Default
public final class ERC20TokenContract implements ContractInterface {

  final Logger logger = Logger.getLogger(ERC20TokenContract.class);

  /**
   * Mint creates new tokens and adds them to minter's account balance. This function triggers a
   * Transfer event.
   *
   * @param ctx the transaction context
   * @param amount of tokens to be minted
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void Mint(final Context ctx, final long amount) {

    // Check minter authorization - this sample assumes Org1 is the central banker with privilege to
    // mint new tokens
    String clientMSPID = ctx.getClientIdentity().getMSPID();
    ChaincodeStub stub = ctx.getStub();
    if (!clientMSPID.equalsIgnoreCase(ContractConstants.MINTER_ORG_MSPID.getValue())) {
      throw new ChaincodeException(
          "Client is not authorized to mint new tokens", UNAUTHORIZED_SENDER.toString());
    }

    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);

    // Get ID of submitting client identity
    String minter = ctx.getClientIdentity().getId();
    if (amount <= 0) {
      throw new ChaincodeException(
          "Mint amount must be a positive integer", INVALID_AMOUNT.toString());
    }
    CompositeKey balanceKey = stub.createCompositeKey(BALANCE_PREFIX.getValue(), minter);
    String currentBalanceStr = stub.getStringState(balanceKey.toString());
    // If minter current balance doesn't yet exist, we'll create it with a current balance of 0
    long currentBalance = 0;
    if (!stringIsNullOrEmpty(currentBalanceStr)) {
      currentBalance = Long.parseLong(currentBalanceStr);
    }
    // Used safe math .
    long updatedBalance = Math.addExact(currentBalance, amount);
    stub.putStringState(balanceKey.toString(), String.valueOf(updatedBalance));
    // Increase totalSupply
    String totalSupplyStr = stub.getStringState(TOTAL_SUPPLY_KEY.getValue());
    long totalSupply = 0;
    if (!stringIsNullOrEmpty(totalSupplyStr)) {
      totalSupply = Long.parseLong(totalSupplyStr);
    }
    // Used safe math .
    totalSupply = Math.addExact(totalSupply, amount);
    stub.putStringState(TOTAL_SUPPLY_KEY.getValue(), String.valueOf(totalSupply));
    Transfer transferEvent = new Transfer("0x0", minter, amount);
    stub.setEvent(TRANSFER_EVENT.getValue(), this.marshal(transferEvent));
    logger.info(
        String.format(
            "minter account %s balance updated from %d to %d",
            minter, currentBalance, updatedBalance));
  }

  /**
   * Burn redeems tokens the minter's account balance. This function triggers a Transfer event.
   *
   * @param ctx the transaction context
   * @param amount amount of tokens to be burned
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void Burn(final Context ctx, final long amount) {

    // Check minter authorization - this sample assumes Org1 is the central banker with privilege to
    // burn tokens

    String clientMSPID = ctx.getClientIdentity().getMSPID();
    ChaincodeStub stub = ctx.getStub();
    if (!clientMSPID.equalsIgnoreCase(ContractConstants.MINTER_ORG_MSPID.getValue())) {
      throw new ChaincodeException(
          "Client is not authorized to burn tokens", UNAUTHORIZED_SENDER.toString());
    }

    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);

    String minter = ctx.getClientIdentity().getId();
    if (amount <= 0) {
      throw new ChaincodeException(
          "Burn amount must be a positive integer", INVALID_AMOUNT.toString());
    }
    CompositeKey balanceKey = stub.createCompositeKey(BALANCE_PREFIX.getValue(), minter);
    String currentBalanceStr = stub.getStringState(balanceKey.toString());
    if (stringIsNullOrEmpty(currentBalanceStr)) {
      throw new ChaincodeException("The balance does not exist", BALANCE_NOT_FOUND.toString());
    }
    long currentBalance = Long.parseLong(currentBalanceStr);
    // Check if the sender has enough tokens to burn.

    if (currentBalance < amount) {
      String errorMessage = String.format("Client account %s has insufficient funds", minter);
      throw new ChaincodeException(errorMessage, INSUFFICIENT_FUND.toString());
    }
    long updatedBalance = Math.subtractExact(currentBalance, amount);
    stub.putStringState(balanceKey.toString(), String.valueOf(updatedBalance));
    // Decrease totalSupply
    String totalSupplyBytes = stub.getStringState(TOTAL_SUPPLY_KEY.getValue());
    if (stringIsNullOrEmpty(totalSupplyBytes)) {
      throw new ChaincodeException("TotalSupply does not exist", NOT_FOUND.toString());
    }
    long totalSupply = Math.subtractExact(Long.parseLong(totalSupplyBytes), amount);
    stub.putStringState(TOTAL_SUPPLY_KEY.getValue(), String.valueOf(totalSupply));
    // Emit the Transfer event
    final Transfer transferEvent = new Transfer(minter, "0x0", amount);
    stub.setEvent(TRANSFER_EVENT.getValue(), this.marshal(transferEvent));
    logger.info(
        String.format(
            "minter account %s balance updated from %d to %d",
            minter, currentBalance, updatedBalance));
  }

  /**
   * Transfer transfers tokens from client account to recipient account. Recipient account must be a
   * valid client Id as returned by the ClientID() function must be a valid clientID as returned by
   * the ClientAccountID() function. This function triggers a Transfer event.
   *
   * @param ctx the transaction context
   * @param to the recipient
   * @param value the amount of token to be transferred
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void Transfer(final Context ctx, final String to, final long value) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    String from = ctx.getClientIdentity().getId();
    this.transferHelper(ctx, from, to, value);
    final Transfer transferEvent = new Transfer(from, to, value);
    ctx.getStub().setEvent(TRANSFER_EVENT.getValue(), this.marshal(transferEvent));
  }

  /**
   * BalanceOf returns the balance of the given account.
   *
   * @param ctx the transaction context
   * @param owner the owner from which the balance will be retrieved
   * @return the account balance
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public long BalanceOf(final Context ctx, final String owner) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    ChaincodeStub stub = ctx.getStub();
    CompositeKey balanceKey = stub.createCompositeKey(BALANCE_PREFIX.getValue(), owner);
    String balance = stub.getStringState(balanceKey.toString());
    if (stringIsNullOrEmpty(balance)) {
      String errorMessage = String.format("Balance of the owner  %s not exists", owner);
      throw new ChaincodeException(errorMessage, NOT_FOUND.toString());
    }
    logger.info(String.format("%s has balance of %s tokens", owner, balance));
    return Long.parseLong(balance);
  }

  /**
   * ClientAccountBalance returns the balance of the requesting client's account.
   *
   * @param ctx the transaction context
   * @return client the account balance
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public long ClientAccountBalance(final Context ctx) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    // Get ID of submitting client identity
    ChaincodeStub stub = ctx.getStub();
    String clientAccountID = ctx.getClientIdentity().getId();
    CompositeKey balanceKey = stub.createCompositeKey(BALANCE_PREFIX.getValue(), clientAccountID);
    String balanceBytes = stub.getStringState(balanceKey.toString());
    if (stringIsNullOrEmpty(balanceBytes)) {
      String errorMessage = String.format("The account  %s does not exist", clientAccountID);
      throw new ChaincodeException(errorMessage, NOT_FOUND.toString());
    }
    long balance = Long.parseLong(balanceBytes);
    logger.info(String.format("%s has balance of %d tokens", clientAccountID, balance));
    return balance;
  }

  /**
   * ClientAccountID returns the id of the requesting client's account. In this implementation, the
   * client account ID is the clientId itself. Users can use this function to get their own account
   * id, which they can then give to others as the payment address.
   *
   * @return client account id .
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public String ClientAccountID(final Context ctx) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    // Get ID of submitting client identity
    return ctx.getClientIdentity().getId();
  }

  /**
   * Return the total token supply.
   *
   * @param ctx the transaction context
   * @return the total token supply
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public long TotalSupply(final Context ctx) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    String totalSupply = ctx.getStub().getStringState(TOTAL_SUPPLY_KEY.getValue());
    if (stringIsNullOrEmpty(totalSupply)) {
      throw new ChaincodeException("Total Supply  not found", NOT_FOUND.toString());
    }
    logger.info(String.format("TotalSupply: %s tokens", totalSupply));
    return Long.parseLong(totalSupply);
  }

  /**
   * Allows `spender` to spend `value` amount of tokens from the owner.
   *
   * @param ctx the transaction context
   * @param spender The spender
   * @param value The amount of tokens to be approved for transfer
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void Approve(final Context ctx, final String spender, final long value) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    ChaincodeStub stub = ctx.getStub();
    String owner = ctx.getClientIdentity().getId();
    CompositeKey allowanceKey =
        stub.createCompositeKey(ALLOWANCE_PREFIX.getValue(), owner, spender);
    stub.putStringState(allowanceKey.toString(), String.valueOf(value));
    Approval approval = new Approval(owner, spender, value);
    stub.setEvent(APPROVAL.getValue(), this.marshal(approval));
    logger.info(
        String.format(
            "client %s approved a withdrawal allowance of %d for spender %s",
            owner, value, spender));
  }

  /**
   * Returns the amount of tokens which `spender` is allowed to withdraw from `owner`.
   *
   * @param ctx the transaction context
   * @param owner The owner of tokens
   * @param spender The spender who are able to transfer the tokens
   * @return the amount of remaining tokens allowed to spent
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public long Allowance(final Context ctx, final String owner, final String spender) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    ChaincodeStub stub = ctx.getStub();
    CompositeKey allowanceKey =
        stub.createCompositeKey(ALLOWANCE_PREFIX.getValue(), owner, spender);
    String allowanceBytes = stub.getStringState(allowanceKey.toString());
    long allowance = 0;
    if (!stringIsNullOrEmpty(allowanceBytes)) {
      allowance = Long.parseLong(allowanceBytes);
    }
    logger.info(
        String.format(
            "The allowance left for spender %s to withdraw from owner %s: %d",
            spender, owner, allowance));
    return allowance;
  }

  /**
   * Transfer `value` amount of tokens from `from` to `to`.
   *
   * @param ctx the transaction context
   * @param from The sender
   * @param to The recipient
   * @param value The amount of token to be transferred
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void TransferFrom(
      final Context ctx, final String from, final String to, final long value) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    String spender = ctx.getClientIdentity().getId();
    ChaincodeStub stub = ctx.getStub();
    // Retrieve the allowance of the spender
    CompositeKey allowanceKey = stub.createCompositeKey(ALLOWANCE_PREFIX.getValue(), from, spender);
    String currentAllowanceStr = stub.getStringState(allowanceKey.toString());
    if (stringIsNullOrEmpty(currentAllowanceStr)) {
      String errorMessage = String.format("Spender %s has no allowance from %s", spender, from);
      throw new ChaincodeException(errorMessage, NO_ALLOWANCE_FOUND.toString());
    }
    long currentAllowance = Long.parseLong(currentAllowanceStr);
    // Check if the transferred value is less than the allowance
    if (currentAllowance < value) {
      String errorMessage =
          String.format("Spender %s does not have enough allowance to spend", spender);
      throw new ChaincodeException(errorMessage, INSUFFICIENT_FUND.toString());
    }
    this.transferHelper(ctx, from, to, value);
    // Decrease the allowance
    long updatedAllowance = currentAllowance - value;
    stub.putStringState(allowanceKey.toString(), String.valueOf(updatedAllowance));
    final Transfer transferEvent = new Transfer(from, to, value);
    stub.setEvent(TRANSFER_EVENT.getValue(), marshal(transferEvent));
    logger.info(
        String.format(
            "spender %s allowance updated from %d to %d",
            spender, currentAllowance, updatedAllowance));
  }

  /**
   * This is a helper function function that transfers tokens from the "from" address to the "to"
   * address. Dependent functions include Transfer and TransferFrom
   *
   * @param ctx the transaction context
   * @param from the sender
   * @param to the receiver
   * @param value the amount.
   */
  private void transferHelper(
      final Context ctx, final String from, final String to, final long value) {

    if (from.equalsIgnoreCase(to)) {
      throw new ChaincodeException(
          "Cannot transfer to and from same client account", INVALID_TRANSFER.toString());
    }
    // transfer of 0 is allowed in ERC20, so just validate against negative amounts
    if (value < 0) {
      throw new ChaincodeException("Transfer amount cannot be negative", INVALID_AMOUNT.toString());
    }
    ChaincodeStub stub = ctx.getStub();
    // Retrieve the current balance of the sender
    CompositeKey fromBalanceKey = stub.createCompositeKey(BALANCE_PREFIX.getValue(), from);
    String fromCurrentBalanceStr = stub.getStringState(fromBalanceKey.toString());
    if (stringIsNullOrEmpty(fromCurrentBalanceStr)) {
      String errorMessage = String.format("Client account %s has no balance", from);
      throw new ChaincodeException(errorMessage, INSUFFICIENT_FUND.toString());
    }
    long fromCurrentBalance = Long.parseLong(fromCurrentBalanceStr);
    // Check if the sender has enough tokens to spend.
    if (fromCurrentBalance < value) {
      String errorMessage = String.format("Client account %s has insufficient funds", from);
      throw new ChaincodeException(errorMessage, INSUFFICIENT_FUND.toString());
    }
    // Retrieve the current balance of the recipient
    CompositeKey toBalanceKey = stub.createCompositeKey(BALANCE_PREFIX.getValue(), to);
    String toCurrentBalanceStr = stub.getStringState(toBalanceKey.toString());
    long toCurrentBalance = 0;
    // If recipient current balance doesn't yet exist, we'll create it with a
    // current balance of 0
    if (!stringIsNullOrEmpty(toCurrentBalanceStr)) {
      toCurrentBalance = Long.parseLong(toCurrentBalanceStr.trim());
    }
    // Update the balance
    long fromUpdatedBalance = Math.subtractExact(fromCurrentBalance, value);
    long toUpdatedBalance = Math.addExact(toCurrentBalance, value);
    stub.putStringState(fromBalanceKey.toString(), String.valueOf(fromUpdatedBalance));
    stub.putStringState(toBalanceKey.toString(), String.valueOf(toUpdatedBalance));
    logger.info(
        String.format(
            "client %s balance updated from %d to %d",
            from, fromCurrentBalance, fromUpdatedBalance));
    logger.info(
        String.format(
            "recipient %s balance updated from %d to %d", to, toCurrentBalance, toUpdatedBalance));
  }

  /**
   * Set optional information for a token.
   *
   * @param ctx the transaction context
   * @param name The name of the token
   * @param symbol The symbol of the token
   * @param decimals The decimals of the token
   */
  @Transaction(intent = Transaction.TYPE.SUBMIT)
  public void Initialize(
      final Context ctx, final String name, final String symbol, final String decimals) {
    ChaincodeStub stub = ctx.getStub();

    // Check minter authorization - this sample assumes Org1 is the central banker with privilege to set Options for these tokens
    String clientMSPID = ctx.getClientIdentity().getMSPID();
    if (!clientMSPID.equalsIgnoreCase(ContractConstants.MINTER_ORG_MSPID.getValue())) {
      throw new ChaincodeException(
          "Client is not authorized to initialize contract", UNAUTHORIZED_SENDER.toString());
    }

    // Check contract options are not already set, client is not authorized to change them once intitialized
    String tokenName = stub.getStringState(ContractConstants.NAME_KEY.getValue());
    if (!stringIsNullOrEmpty(tokenName)) {
      throw new ChaincodeException("contract options are already set, client is not authorized to change them");
    }

    stub.putStringState(NAME_KEY.getValue(), name);
    stub.putStringState(SYMBOL_KEY.getValue(), symbol);
    stub.putStringState(DECIMALS_KEY.getValue(), decimals);
  }

  /**
   * Return the name of the token - e.g. "MyToken". The original function name is `name` in ERC20
   * specification. However, 'name' conflicts with a parameter `name` in `Contract` class. As a work
   * around, we use `TokenName` as an alternative function name.
   *
   * @param ctx the transaction context
   * @return the name of the token
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public String TokenName(final Context ctx) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    String tokenName = ctx.getStub().getStringState(ContractConstants.NAME_KEY.getValue());
    if (stringIsNullOrEmpty(tokenName)) {
      throw new ChaincodeException("Token name not found", NOT_FOUND.toString());
    }
    return tokenName;
  }

  /**
   * Return the symbol of the token.
   *
   * @param ctx the transaction context
   * @return the symbol of the token
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public String TokenSymbol(final Context ctx) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    String tokenSymbol = ctx.getStub().getStringState(SYMBOL_KEY.getValue());
    if (stringIsNullOrEmpty(tokenSymbol)) {
      throw new ChaincodeException("Token symbol not found", NOT_FOUND.toString());
    }
    return tokenSymbol;
  }

  /**
   * Return the number of decimals the token uses e.g. 8, means to divide the token amount by
   * 100000000 to get its user representation.
   *
   * @param ctx the transaction context
   * @return the number of decimals
   */
  @Transaction(intent = Transaction.TYPE.EVALUATE)
  public int Decimals(final Context ctx) {
    // Check contract options are already set first to execute the function
    this.checkInitialized(ctx);
    String decimals = ctx.getStub().getStringState(DECIMALS_KEY.getValue());
    if (stringIsNullOrEmpty(decimals)) {
      throw new ChaincodeException("Decimal not found", NOT_FOUND.toString());
    }
    return Integer.parseInt(decimals);
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

  /**
   * Checks that contract options have been already initialized
   *
   * @param ctx the transaction context
   * @return the number of decimals
   */
  private void checkInitialized(final Context ctx) {
    String tokenName = ctx.getStub().getStringState(ContractConstants.NAME_KEY.getValue());
    if (stringIsNullOrEmpty(tokenName)) {
      throw new ChaincodeException("Contract options need to be set before calling any function, call Initialize() to initialize contract", NOT_FOUND.toString());
    }
  }
}
