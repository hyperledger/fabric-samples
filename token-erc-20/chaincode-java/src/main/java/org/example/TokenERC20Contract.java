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

@Contract(name = "TokenERC20Contract", info = @Info(title = "TokenERC20Contract", description = "A java chaincode for erc20 token", version = "0.0.1-SNAPSHOT"))

@Default
public final class TokenERC20Contract implements ContractInterface {

 
    final private String balancePrefix = "balance";
    final private String allowancePrefix = "allowance";
    final private String nameKey = "name";
    final private String symbolKey = "symbol";
    final private String decimalsKey = "decimals";
    final private String totalSupplyKey = "totalSupply";

    /**
     * Return the name of the token - e.g. "MyToken". The original function name is
     * `name` in ERC20 specification. However, 'name' conflicts with a parameter
     * `name` in `Contract` class. As a work around, we use `TokenName` as an
     * alternative function name.
     *
     * @param {Context} ctx the transaction context
     * @returns {String} Returns the name of the token
     */
    @Transaction()
    public String tokenName(final Context ctx) {

        ChaincodeStub stub = ctx.getStub();
        String tokenName = stub.getStringState(nameKey);
        if (tokenName.isEmpty()) {

            throw new ChaincodeException("Sorry ! Token name not found");
        }

        return tokenName;

    }

    /**
     * Return the symbol of the token. E.g. “HIX”.
     *
     * @param {Context} ctx the transaction context
     * @returns {String} Returns the symbol of the token
     */
    @Transaction()
    public String tokenSymbol(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();
        String tokenSymbol = stub.getStringState(symbolKey);
        if (tokenSymbol.isEmpty()) {

            throw new ChaincodeException("Sorry ! Token symbol not found");
        }

        return tokenSymbol;

    }

    /**
     * Return the number of decimals the token uses e.g. 8, means to divide the
     * token amount by 100000000 to get its user representation.
     *
     * @param {Context} ctx the transaction context
     * @returns {Number} Returns the number of decimals
     */
    @Transaction()
    public Integer decimals(final Context ctx) {

        ChaincodeStub stub = ctx.getStub();
        String decimals = stub.getStringState(decimalsKey);
        if (decimals.isEmpty()) {

            throw new ChaincodeException("Sorry ! Decimal not found");
        }
        return Integer.parseInt(decimals);
    }

    /**
     * Return the total token supply.
     *
     * @param {Context} ctx the transaction context
     * @returns {Number} Returns the total token supply
     */
    @Transaction()
    public Long totalSupply(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();
        String totalSupply = stub.getStringState(totalSupplyKey);
        if (totalSupply.isEmpty()) {

            throw new ChaincodeException("Sorry ! Total Supply  not found");
        }
        return Long.parseLong(totalSupply);
    }

    /**
     * BalanceOf returns the balance of the given account.
     *
     * @param {Context} ctx the transaction context
     * @param {String}  owner The owner from which the balance will be retrieved
     * @returns {Number} Returns the account balance
     */
    @Transaction()
    public long balanceOf(final Context ctx, final String owner) {

        ChaincodeStub stub = ctx.getStub();
        CompositeKey balanceKey = stub.createCompositeKey(balancePrefix, owner);

        String balance = stub.getStringState(balanceKey.toString().trim());
        if (balance == null || balance.isEmpty() || balance.length() == 0) {
            String errorMessage = String.format("Balance of the owner  %s not exists", owner);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage);
        }

        return Long.parseLong(balance.toString());

    }

    /**
     * Transfer transfers tokens from client account to recipient account. recipient
     * account must be a valid clientID as returned by the ClientAccountID()
     * function.
     *
     * @param {Context} ctx the transaction context
     * @param {String}  to The recipient
     * @param {Integer} value The amount of token to be transferred
     * @returns {Boolean} Return whether the transfer was successful or not
     */
    @Transaction()
    public boolean transfer(final Context ctx, final String to, String _value) {

        String from = ctx.getClientIdentity().getId();
        long value = Long.parseLong(_value.trim());
        boolean transferResp = this.doTransfer(ctx, from, to, value);

        if (!transferResp) {
            String errorMessage = String.format("Cannot transfer to and from same client account");
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage);
        }

        ChaincodeStub stub = ctx.getStub();
        JSONObject obj = new JSONObject();
        obj.put("from", from);
        obj.put("to", to);
        obj.put("value", value);
        stub.setEvent("Transfer", this.serialize(obj));
        return true;

    }

    /**
     * Transfer `value` amount of tokens from `from` to `to`.
     *
     * @param {Context} ctx the transaction context
     * @param {String}  from The sender
     * @param {String}  to The recipient
     * @param {Integer} value The amount of token to be transferred
     * @returns {Boolean} Return whether the transfer was successful or not
     */
    @Transaction()
    public boolean transferFrom(Context ctx, final String from, final String to, String _value) {

        String spender = ctx.getClientIdentity().getId();
        ChaincodeStub stub = ctx.getStub();
        // Retrieve the allowance of the spender
        CompositeKey allowanceKey = stub.createCompositeKey(allowancePrefix, from, spender);
        String currentAllowanceStr = stub.getStringState(allowanceKey.toString().trim());
        if (currentAllowanceStr.isBlank() || currentAllowanceStr.length() == 0) {
            String errorMessage = String.format("Spender %s has no allowance from %s", spender, from);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage);
        }
        long currentAllowance = Long.parseLong(currentAllowanceStr.toString());

        // Convert value from string to int
        Long valueInt = Long.parseLong(_value);

        // Check if the transferred value is less than the allowance
        if (currentAllowance < valueInt) {

            String errorMessage = String.format("The spender does not have enough allowance to spend.");
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage);

        }

        boolean transferResp = this.doTransfer(ctx, from, to, valueInt);

        if (!transferResp) {
            throw new ChaincodeException("Failed to transfer");
        }

        // Decrease the allowance
        long updatedAllowance = currentAllowance - valueInt;
        stub.putStringState(allowanceKey.toString().trim(), String.valueOf(updatedAllowance));
        System.out.printf("spender %s allowance updated from %d to %d", spender, currentAllowance, updatedAllowance);

        JSONObject obj = new JSONObject();
        obj.put("from", from);
        obj.put("to", to);
        obj.put("value", valueInt);
        stub.setEvent("Transfer", this.serialize(obj));
        System.out.println("transferFrom ended successfully");

        return true;
    }

    @Transaction()
    private boolean doTransfer(final Context ctx, final String _from, final String _to, long _value) {

        if (_from.equalsIgnoreCase(_to)) {
            throw new ChaincodeException("cannot transfer to and from same client account");
        }

        if (_value < 0) { // transfer of 0 is allowed in ERC20, so just validate against negative amounts
            throw new ChaincodeException("transfer amount cannot be negative");
        }

        ChaincodeStub stub = ctx.getStub();
        // Retrieve the current balance of the sender
        CompositeKey fromBalanceKey = stub.createCompositeKey(balancePrefix, _from.trim());
        String fromCurrentBalance = stub.getStringState(fromBalanceKey.toString().trim());
        if (fromCurrentBalance.isBlank() || fromCurrentBalance.length() == 0) {
            String errorMessage = String.format("client account %s has no balance", _from);
            throw new ChaincodeException(errorMessage);

        }

        long _fromCurrentBalance = Long.parseLong(fromCurrentBalance.toString().trim());

        // Check if the sender has enough tokens to spend.
        if (_fromCurrentBalance < _value) {
            String errorMessage = String.format("client account %s has insufficient funds", _from);
            throw new ChaincodeException(errorMessage);
        }

        // Retrieve the current balance of the recepient
        CompositeKey toBalanceKey = stub.createCompositeKey(balancePrefix, _to);
        String toCurrentBalance = stub.getStringState(toBalanceKey.toString().trim());

        long _toCurrentBalance = 0;
        // If recipient current balance doesn't yet exist, we'll create it with a
        // current balance of 0
        if (toCurrentBalance.isBlank() || toCurrentBalance.length() == 0) {
            _toCurrentBalance = 0;
        } else {
            _toCurrentBalance = Long.parseLong(toCurrentBalance.trim());
        }

        // Update the balance
        long fromUpdatedBalance = _fromCurrentBalance - _value;
        long toUpdatedBalance = _toCurrentBalance + _value;

        stub.putStringState(fromBalanceKey.toString().trim(), String.valueOf(fromUpdatedBalance));

        stub.putStringState(toBalanceKey.toString().trim(), String.valueOf(toUpdatedBalance));

        System.out.printf("client %s balance updated from  %d to %d", _from, _fromCurrentBalance, fromUpdatedBalance);
        System.out.printf("recipient %s balance updated from  %d to %d", _to, _toCurrentBalance, toUpdatedBalance);

        return true;
    }

    /**
     * Allows `spender` to spend `value` amount of tokens from the owner.
     *
     * @param {Context} ctx the transaction context
     * @param {String}  spender The spender
     * @param {Integer} value The amount of tokens to be approved for transfer
     * @returns {Boolean} Return whether the approval was successful or not
     */
    @Transaction()
    public boolean approve(final Context ctx, final String spender, final String value) {

        String owner = ctx.getClientIdentity().getId();
        ChaincodeStub stub = ctx.getStub();
        CompositeKey allowanceKey = stub.createCompositeKey(allowancePrefix, owner, spender);
        long valueInt = Long.parseLong(value);
        stub.putStringState(allowanceKey.toString().trim(), String.valueOf(valueInt));
        JSONObject obj = new JSONObject();
        obj.put("owner", owner);
        obj.put("spender", spender);
        obj.put("value", valueInt);
        stub.setEvent("Approval", this.serialize(obj));
        System.out.println("Approve ended successfully");

        return true;

    }

    /**
     * Returns the amount of tokens which `spender` is allowed to withdraw from
     * `owner`.
     *
     * @param {Context} ctx the transaction context
     * @param {String}  owner The owner of tokens
     * @param {String}  spender The spender who are able to transfer the tokens
     * @returns {Number} Return the amount of remaining tokens allowed to spent
     */

    @Transaction()
    public long allowance(final Context ctx, final String owner, final String spender) {

        ChaincodeStub stub = ctx.getStub();

        CompositeKey allowanceKey = stub.createCompositeKey(allowancePrefix, owner, spender);
        String allowanceBytes = stub.getStringState(allowanceKey.toString().trim());

        if (allowanceBytes.isBlank() || allowanceBytes.length() == 0) {

            String errorMessage = String.format("spender account %s has no allowance from", spender, owner);
            throw new ChaincodeException(errorMessage);
        }

        long allowance = Long.parseLong(allowanceBytes.toString().trim());
        return allowance;
    }

    /**
     * Set optional infomation for a token.
     *
     * @param {Context} ctx the transaction context
     * @param {String}  name The name of the token
     * @param {String}  symbol The symbol of the token
     * @param {String}  decimals The decimals of the token
     * @param {String}  totalSupply The totalSupply of the token
     */
    @Transaction()
    public boolean setOptions(final Context ctx, final String name, final String symbol, final String decimals) {
        ChaincodeStub stub = ctx.getStub();
        stub.putStringState(nameKey, name);
        stub.putStringState(symbolKey, symbol);
        stub.putStringState(decimalsKey, decimals);

        System.out.printf("name:%s, symbol: %s, decimals: %s", name, symbol, decimals);
        return true;
    }

    /**
     * Mint creates new tokens and adds them to minter's account balance
     *
     * @param {Context} ctx the transaction context
     * @param {Integer} amount amount of tokens to be minted
     * @returns {Object} The balance
     */
    @Transaction()
    public boolean mint(final Context ctx, final String amount) {

        // Check minter authorization - this sample assumes Org1 is the central banker
        // with privilege to mint new tokens

        String clientMSPID = ctx.getClientIdentity().getMSPID();
        ChaincodeStub stub = ctx.getStub();
        if (!clientMSPID.equalsIgnoreCase("Org1MSP")) {
            throw new ChaincodeException("client is not authorized to mint new tokens");
        }

        // Get ID of submitting client identity
        String minter = ctx.getClientIdentity().getId();
        long amountInt = Long.parseLong(amount.trim());
        if (amountInt <= 0) {
            throw new ChaincodeException("mint amount must be a positive integer");
        }

        CompositeKey balanceKey = stub.createCompositeKey(balancePrefix, minter);

        String currentBalanceBytes = stub.getStringState(balanceKey.toString().trim());
        // If minter current balance doesn't yet exist, we'll create it with a current
        // balance of 0
        long currentBalance = 0;

        if (currentBalanceBytes.isBlank() || currentBalanceBytes.length() == 0) {

            currentBalance = 0;

        } else {

            currentBalance = Long.parseLong(currentBalanceBytes.toString());

        }
        long updatedBalance = currentBalance + amountInt;

        stub.putStringState(balanceKey.toString().trim(), String.valueOf(updatedBalance));

        // Increase totalSupply
        String totalSupplyBytes = stub.getStringState(totalSupplyKey.trim());
        long totalSupply = 0;
        if (totalSupplyBytes.isBlank() || totalSupplyBytes.length() == 0) {
            System.out.println("Initialize the tokenSupply");
            totalSupply = 0;

        } else {

            totalSupply = Long.parseLong(totalSupplyBytes.toString());
        }

        totalSupply = totalSupply + amountInt;
        stub.putStringState(totalSupplyKey.trim(), String.valueOf(totalSupply));

        JSONObject obj = new JSONObject();
        obj.put("from", "0x0");
        obj.put("to", minter);
        obj.put("value", amountInt);
        stub.setEvent("Transfer", this.serialize(obj));

        // System.out.printf("minter account %s balance updated from %d to %d",minter,
        // currentBalance ,updatedBalance);
        return true;
    }

    /**
     * Burn redeem tokens from minter's account balance
     *
     * @param {Context} ctx the transaction context
     * @param {Integer} amount amount of tokens to be burned
     * @returns {Object} The balance
     */
    @Transaction()
    public boolean burn(final Context ctx, final String amount) {

        // Check minter authorization - this sample assumes Org1 is the central banker
        // with privilege to burn tokens
        String clientMSPID = ctx.getClientIdentity().getMSPID();
        ChaincodeStub stub = ctx.getStub();
        if (!clientMSPID.equalsIgnoreCase("Org1MSP")) {
            throw new ChaincodeException("client is not authorized to mint new tokens");
        }

        String minter = ctx.getClientIdentity().getId();

        long amountInt = Long.parseLong(amount);

        CompositeKey balanceKey = stub.createCompositeKey(balancePrefix, minter);

        String currentBalanceBytes = stub.getStringState(balanceKey.toString().trim());
        if (currentBalanceBytes.isBlank() || currentBalanceBytes.length() == 0) {
            throw new ChaincodeException("The balance does not exist");
        }
        long currentBalance = Long.valueOf(currentBalanceBytes.toString());
        long updatedBalance = currentBalance - amountInt;

        stub.putStringState(balanceKey.toString().trim(), String.valueOf(updatedBalance));

        // Decrease totalSupply
        String totalSupplyBytes = stub.getStringState(totalSupplyKey.toString().trim());
        if (totalSupplyBytes.isBlank() || totalSupplyBytes.length() == 0) {
            throw new ChaincodeException("totalSupply does not exist.");
        }
        long totalSupply = Long.parseLong(totalSupplyBytes.toString()) - amountInt;
        stub.putStringState(totalSupplyKey.toString().trim(), String.valueOf(totalSupply));

        // Emit the Transfer event

        JSONObject obj = new JSONObject();
        obj.put("from", minter);
        obj.put("to", "0x0");
        obj.put("value", amountInt);
        stub.setEvent("Transfer", this.serialize(obj));
        System.out.printf("minter account %s balance updated from %d to %d", minter, currentBalance, updatedBalance);
        return true;
    }

    /**
     * ClientAccountBalance returns the balance of the requesting client's account.
     *
     * @param {Context} ctx the transaction context
     * @returns {Number} Returns the account balance
     */

    @Transaction()
    public long getClientAccountBalance(final Context ctx) {
        // Get ID of submitting client identity
        ChaincodeStub stub = ctx.getStub();
        String clientAccountID = ctx.getClientIdentity().getId();
        CompositeKey balanceKey = stub.createCompositeKey(balancePrefix, clientAccountID);
        String balanceBytes = stub.getStringState(balanceKey.toString().trim());
        if (balanceBytes.isBlank() || balanceBytes.length() == 0) {

            String errorMessage = String.format("the account  %s does not exist", clientAccountID);
            throw new ChaincodeException(errorMessage);
        }
        long balance = Long.parseLong(balanceBytes.trim());

        return balance;
    }

    // ClientAccountID returns the id of the requesting client's account.
    // In this implementation, the client account ID is the clientId itself.
    // Users can use this function to get their own account id, which they can then
    // give to others as the payment address
    @Transaction()
    public String getClientAccountID(final Context ctx) {
        // Get ID of submitting client identity
        String clientAccountID = ctx.getClientIdentity().getId();
        return clientAccountID;
    }

    private byte[] serialize(Object object) {
        String jsonStr = new JSONObject(object).toString();
        return jsonStr.getBytes(UTF_8);
    }

}
