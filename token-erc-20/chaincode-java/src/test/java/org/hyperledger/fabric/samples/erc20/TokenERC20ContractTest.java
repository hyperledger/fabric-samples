/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.fabric.samples.erc20;

import org.hyperledger.fabric.contract.ClientIdentity;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.hyperledger.fabric.samples.erc20.ContractConstants.ALLOWANCE_PREFIX;
import static org.hyperledger.fabric.samples.erc20.ContractConstants.BALANCE_PREFIX;
import static org.hyperledger.fabric.samples.erc20.ContractConstants.DECIMALS_KEY;
import static org.hyperledger.fabric.samples.erc20.ContractConstants.MINTER_ORG_MSPID;
import static org.hyperledger.fabric.samples.erc20.ContractConstants.NAME_KEY;
import static org.hyperledger.fabric.samples.erc20.ContractConstants.SYMBOL_KEY;
import static org.hyperledger.fabric.samples.erc20.ContractConstants.TOTAL_SUPPLY_KEY;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TokenERC20ContractTest {

  private final String org1UserId =
      "x509::CN=User0@org1.example.com, L=San Francisco, ST=California,"
          + " C=US::CN=ca.org2.example.com, O=org2.example.com, L=San Francisco, ST=California, C=US";
  private final String spender =
      "x509::CN=User1@org2.example.com, L=San Francisco, ST=California,"
          + " C=US::CN=ca.org2.example.com, O=org2.example.com, L=San Francisco, ST=California, C=US";

  @Nested
  class InvokeQueryERC20TokenOptionsTransaction {

    @Test
    public void whenTokenNameExists() {
      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      String tokenName = contract.TokenName(ctx);
      assertThat(tokenName).isEqualTo("ARBTToken");
    }

    @Test
    public void whenTokenNameDoesNotExist() {
      ERC20TokenContract contract = new ERC20TokenContract();
      final Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("");
      Throwable thrown = catchThrowable(() -> contract.TokenName(ctx));
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("Contract options need to be set before calling any function, call Initialize() to initialize contract");
    }

    @Test
    public void whenTokenSymbolExists() {
      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(stub.getStringState(SYMBOL_KEY.getValue())).thenReturn("ARBT");
      String toknName = contract.TokenSymbol(ctx);
      assertThat(toknName).isEqualTo("ARBT");
    }

    @Test
    public void whenTokenSymbolDoesNotExist() {
      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(stub.getStringState(SYMBOL_KEY.getValue())).thenReturn("");
      Throwable thrown = catchThrowable(() -> contract.TokenSymbol(ctx));
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("Token symbol not found");
    }

    @Test
    public void whenTokenDecimalExists() {
      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(stub.getStringState(DECIMALS_KEY.getValue())).thenReturn("18");
      long decimal = contract.Decimals(ctx);
      assertThat(decimal).isEqualTo(18);
    }

    @Test
    public void whenTokenDecimalNotExists() {
      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(stub.getStringState(DECIMALS_KEY.getValue())).thenReturn("");
      Throwable thrown = catchThrowable(() -> contract.Decimals(ctx));
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("Decimal not found");
    }

    @Test
    public void whenTokenTotalSupplyExists() {
      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(stub.getStringState(TOTAL_SUPPLY_KEY.getValue())).thenReturn("222222222222");
      long totalSupply = contract.TotalSupply(ctx);
      assertThat(totalSupply).isEqualTo(222222222222L);
    }

    @Test
    public void whenTokenTotalSupplyNotExists() {
      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(stub.getStringState(TOTAL_SUPPLY_KEY.getValue())).thenReturn("");
      Throwable thrown = catchThrowable(() -> contract.TotalSupply(ctx));
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("Total Supply  not found");
    }

    @Test
    public void ClientAccountIDTest() {
      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      ClientIdentity ci = mock(ClientIdentity.class);
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(ci.getMSPID()).thenReturn(MINTER_ORG_MSPID.getValue());
      when(ci.getId()).thenReturn(org1UserId);
      when(ctx.getStub()).thenReturn(stub);
      assertThat(ci.getMSPID()).isEqualTo(MINTER_ORG_MSPID.getValue());
      String id = contract.ClientAccountID(ctx);
      assertThat(id).isEqualTo(org1UserId);
    }
  }

  @Nested
  class TokenOperationsInvoke {

    @Test
    public void invokeInitializeTest() {
      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      ClientIdentity ci = mock(ClientIdentity.class);
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getMSPID()).thenReturn(MINTER_ORG_MSPID.getValue());
      contract.Initialize(ctx, "ARBTToken", "ARBT", "18");
      verify(stub).putStringState(NAME_KEY.getValue(), "ARBTToken");
      verify(stub).putStringState(SYMBOL_KEY.getValue(), "ARBT");
      verify(stub).putStringState(DECIMALS_KEY.getValue(), "18");
    }

    @Test
    public void invokeBalanceOfTest() {
      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      ClientIdentity ci = mock(ClientIdentity.class);
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getMSPID()).thenReturn(MINTER_ORG_MSPID.getValue());
      when(ci.getId()).thenReturn(org1UserId);
      when(ctx.getStub()).thenReturn(stub);
      CompositeKey ck = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), org1UserId)).thenReturn(ck);
      when(ck.toString()).thenReturn(BALANCE_PREFIX.getValue() + org1UserId);
      when(stub.getStringState(ck.toString())).thenReturn("1000");
      long balance = contract.BalanceOf(ctx, org1UserId);
      assertThat(balance).isEqualTo(1000);
    }

    @Test
    public void invokeClientAccountBalanceTest() {
      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      ClientIdentity ci = mock(ClientIdentity.class);
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getMSPID()).thenReturn(MINTER_ORG_MSPID.getValue());
      when(ci.getId()).thenReturn(org1UserId);
      when(ctx.getStub()).thenReturn(stub);
      CompositeKey ck = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), org1UserId)).thenReturn(ck);
      when(ck.toString()).thenReturn(BALANCE_PREFIX.getValue() + org1UserId);
      when(stub.getStringState(ck.toString())).thenReturn("1000");
      long balance = contract.ClientAccountBalance(ctx);
      assertThat(balance).isEqualTo(1000);
    }

    @Test
    public void invokeMintTokenTest() {

      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      ClientIdentity ci = mock(ClientIdentity.class);
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(ci.getMSPID()).thenReturn(MINTER_ORG_MSPID.getValue());
      when(ci.getId()).thenReturn(org1UserId);
      when(ctx.getStub()).thenReturn(stub);
      CompositeKey ck = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), org1UserId)).thenReturn(ck);
      when(ck.toString()).thenReturn(BALANCE_PREFIX.getValue() + org1UserId);
      when(stub.getStringState(ck.toString())).thenReturn(null);
      when(ctx.getStub()).thenReturn(stub);
      contract.Mint(ctx, 1000);
      verify(stub).putStringState(TOTAL_SUPPLY_KEY.getValue(), "1000");
      verify(stub).putStringState(ck.toString(), "1000");
    }

    @Test
    public void whenMintTokenUnAuthorized() {

      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      ClientIdentity ci = mock(ClientIdentity.class);
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(ci.getMSPID()).thenReturn("Org2MSP");
      when(ci.getId()).thenReturn(org1UserId);
      when(ctx.getStub()).thenReturn(stub);
      CompositeKey ck = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), org1UserId)).thenReturn(ck);
      when(ck.toString()).thenReturn(BALANCE_PREFIX.getValue() + org1UserId);
      when(stub.getStringState(ck.toString())).thenReturn(null);
      when(ctx.getStub()).thenReturn(stub);
      Throwable thrown = catchThrowable(() -> contract.Mint(ctx, 1000));
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("Client is not authorized to mint new tokens");
    }

    @Test
    public void invokeTokenTransferTest() {

      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      ClientIdentity ci = mock(ClientIdentity.class);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getMSPID()).thenReturn(MINTER_ORG_MSPID.getValue());
      when(ci.getId()).thenReturn(org1UserId);
      when(ctx.getStub()).thenReturn(stub);
      String to =
          "x509::CN=User2@org2.example.com, L=San Francisco, ST=California,"
              + " C=US::CN=ca.org2.example.com, O=org2.example.com, L=San Francisco, ST=California, C=US";

      CompositeKey ckFrom = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), org1UserId)).thenReturn(ckFrom);
      when(ckFrom.toString()).thenReturn(BALANCE_PREFIX.getValue() + org1UserId);
      when(stub.getStringState(ckFrom.toString())).thenReturn("1000");
      CompositeKey ckTo = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), to)).thenReturn(ckTo);
      when(ckTo.toString()).thenReturn(BALANCE_PREFIX.getValue() + to);
      when(stub.getStringState(ckTo.toString())).thenReturn(null);
      contract.Transfer(ctx, to, 100);
      verify(stub).putStringState(ckTo.toString(), "100");
      verify(stub).putStringState(ckFrom.toString(), "900");
    }

    @Test
    public void whenZeroAmountTokenTransferTest() {

      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      ClientIdentity ci = mock(ClientIdentity.class);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getMSPID()).thenReturn(MINTER_ORG_MSPID.getValue());
      when(ci.getId()).thenReturn(org1UserId);
      when(ctx.getStub()).thenReturn(stub);
      String to =
          "x509::CN=User2@org2.example.com, L=San Francisco, ST=California,"
              + " C=US::CN=ca.org2.example.com, O=org2.example.com, L=San Francisco, ST=California, C=US";

      CompositeKey ckFrom = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), org1UserId)).thenReturn(ckFrom);
      when(ckFrom.toString()).thenReturn(BALANCE_PREFIX.getValue() + org1UserId);
      when(stub.getStringState(ckFrom.toString())).thenReturn("1000");
      CompositeKey ckTo = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), to)).thenReturn(ckTo);
      when(ckTo.toString()).thenReturn(BALANCE_PREFIX.getValue() + to);
      when(stub.getStringState(ckTo.toString())).thenReturn(null);
      contract.Transfer(ctx, to, 0);
      verify(stub).putStringState(ckTo.toString(), "0");
      verify(stub).putStringState(ckFrom.toString(), "1000");
    }

    @Test
    public void whenTokenTransferNegativeAmount() {

      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      ClientIdentity ci = mock(ClientIdentity.class);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getMSPID()).thenReturn(MINTER_ORG_MSPID.getValue());
      when(ci.getId()).thenReturn(org1UserId);
      when(ctx.getStub()).thenReturn(stub);
      String to =
          "x509::CN=User2@org2.example.com, L=San Francisco, ST=California,"
              + " C=US::CN=ca.org2.example.com, O=org2.example.com, L=San Francisco, ST=California, C=US";

      CompositeKey ckFrom = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), org1UserId)).thenReturn(ckFrom);
      when(ckFrom.toString()).thenReturn(BALANCE_PREFIX.getValue() + org1UserId);
      when(stub.getStringState(ckFrom.toString())).thenReturn("1000");
      CompositeKey ckTo = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), to)).thenReturn(ckTo);
      when(ckTo.toString()).thenReturn(BALANCE_PREFIX.getValue() + to);
      when(stub.getStringState(ckTo.toString())).thenReturn(null);

      Throwable thrown = catchThrowable(() -> contract.Transfer(ctx, to, -1));
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("Transfer amount cannot be negative");
    }

    @Test
    public void whenTokenTransferSameId() {

      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      ClientIdentity ci = mock(ClientIdentity.class);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getMSPID()).thenReturn(MINTER_ORG_MSPID.getValue());
      when(ci.getId()).thenReturn(org1UserId);
      when(ctx.getStub()).thenReturn(stub);
      String to =
          "x509::CN=User2@org2.example.com, L=San Francisco, ST=California,"
              + " C=US::CN=ca.org2.example.com, O=org2.example.com, L=San Francisco, ST=California, C=US";

      CompositeKey ckFrom = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), org1UserId)).thenReturn(ckFrom);
      when(ckFrom.toString()).thenReturn(BALANCE_PREFIX.getValue() + org1UserId);
      when(stub.getStringState(ckFrom.toString())).thenReturn("1000");
      CompositeKey ckTo = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), to)).thenReturn(ckTo);
      when(ckTo.toString()).thenReturn(BALANCE_PREFIX.getValue() + to);
      when(stub.getStringState(ckTo.toString())).thenReturn(null);

      Throwable thrown = catchThrowable(() -> contract.Transfer(ctx, org1UserId, 10));
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("Cannot transfer to and from same client account");
    }

    @Test
    public void invokeTokenBurnTest() {

      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      ClientIdentity ci = mock(ClientIdentity.class);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getMSPID()).thenReturn(MINTER_ORG_MSPID.getValue());
      when(ci.getId()).thenReturn(org1UserId);
      CompositeKey ck = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), org1UserId)).thenReturn(ck);
      when(ck.toString()).thenReturn(BALANCE_PREFIX.getValue() + org1UserId);
      when(stub.getStringState(ck.toString())).thenReturn(null);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(TOTAL_SUPPLY_KEY.getValue())).thenReturn("1000");
      when(stub.getStringState(ck.toString())).thenReturn("1000");
      contract.Burn(ctx, 100);
      verify(stub).putStringState(TOTAL_SUPPLY_KEY.getValue(), "900");
    }

    @Test
    public void whenTokenBurnUnAuthorizedTest() {
      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      ClientIdentity ci = mock(ClientIdentity.class);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getMSPID()).thenReturn("Org2MSP");
      when(ci.getId()).thenReturn(spender);
      CompositeKey ck = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), spender)).thenReturn(ck);
      when(ck.toString()).thenReturn(BALANCE_PREFIX.getValue() + spender);
      when(stub.getStringState(ck.toString())).thenReturn(null);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(TOTAL_SUPPLY_KEY.getValue())).thenReturn("1000");
      when(stub.getStringState(ck.toString())).thenReturn("1000");

      Throwable thrown = catchThrowable(() -> contract.Burn(ctx, 100));
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("Client is not authorized to burn tokens");
    }

    @Test
    public void whenTokenBurnNegativeAmountTest() {
      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      ClientIdentity ci = mock(ClientIdentity.class);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getMSPID()).thenReturn("Org1MSP");
      when(ci.getId()).thenReturn(org1UserId);
      CompositeKey ck = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), org1UserId)).thenReturn(ck);
      when(ck.toString()).thenReturn(BALANCE_PREFIX.getValue() + org1UserId);
      when(stub.getStringState(ck.toString())).thenReturn(null);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(TOTAL_SUPPLY_KEY.getValue())).thenReturn("1000");
      when(stub.getStringState(ck.toString())).thenReturn("1000");

      Throwable thrown = catchThrowable(() -> contract.Burn(ctx, -100));
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("Burn amount must be a positive integer");
    }
  }

  @Nested
  class InvokeERC20AllowanceTransactions {

    @Test
    public void invokeAllowanceTest() {
      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      ClientIdentity ci = mock(ClientIdentity.class);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getMSPID()).thenReturn(MINTER_ORG_MSPID.getValue());
      when(ci.getId()).thenReturn(org1UserId);
      when(ctx.getStub()).thenReturn(stub);
      CompositeKey ck = mock(CompositeKey.class);
      when(stub.createCompositeKey(ALLOWANCE_PREFIX.getValue(), org1UserId, spender))
          .thenReturn(ck);
      when(ck.toString()).thenReturn(ALLOWANCE_PREFIX.getValue() + org1UserId + spender);
      when(stub.getStringState(ck.toString())).thenReturn("100");
      long allowance = contract.Allowance(ctx, org1UserId, spender);
      assertThat(allowance).isEqualTo(100);
    }

    @Test
    public void invokeApproveForTokenAllowanceTest() {
      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      ClientIdentity ci = mock(ClientIdentity.class);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getMSPID()).thenReturn(MINTER_ORG_MSPID.getValue());
      when(ci.getId()).thenReturn(org1UserId);
      when(ctx.getStub()).thenReturn(stub);
      CompositeKey ck = mock(CompositeKey.class);
      when(stub.createCompositeKey(ALLOWANCE_PREFIX.getValue(), org1UserId, spender))
          .thenReturn(ck);
      when(ck.toString()).thenReturn(ALLOWANCE_PREFIX.getValue() + org1UserId + spender);
      contract.Approve(ctx, spender, 200);
      verify(stub).putStringState(ck.toString(), String.valueOf(200));
    }

    @Test
    public void invokeAllowanceTransferFromTest() {
      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      ClientIdentity ci;

      String to =
          "x509::CN=User3@org2.example.com, L=San Francisco, ST=California,"
              + " C=US::CN=ca.org2.example.com, O=org2.example.com, L=San Francisco, ST=California, C=US";

      CompositeKey ckFromBalance = mock(CompositeKey.class);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(stub.createCompositeKey(BALANCE_PREFIX.toString(), org1UserId))
          .thenReturn(ckFromBalance);
      when(ckFromBalance.toString()).thenReturn(BALANCE_PREFIX.getValue() + org1UserId);
      CompositeKey ckTOBalance = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.toString(), to)).thenReturn(ckTOBalance);
      when(ckFromBalance.toString()).thenReturn(BALANCE_PREFIX.getValue() + to);
      when(ctx.getStub()).thenReturn(stub);
      ci = mock(ClientIdentity.class);
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getMSPID()).thenReturn("Org2MSP");
      when(ci.getId()).thenReturn(spender);
      CompositeKey ckAllowance = mock(CompositeKey.class);
      when(stub.createCompositeKey(ALLOWANCE_PREFIX.getValue(), org1UserId, spender))
          .thenReturn(ckAllowance);
      when(ckAllowance.toString()).thenReturn(ALLOWANCE_PREFIX.getValue() + org1UserId + spender);
      when(stub.getStringState(ckAllowance.toString())).thenReturn("200");
      CompositeKey ckFrom = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), org1UserId)).thenReturn(ckFrom);
      when(ckFrom.toString()).thenReturn(BALANCE_PREFIX.getValue() + org1UserId);
      when(stub.getStringState(ckFrom.toString())).thenReturn("1000");
      CompositeKey ckTo = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), to)).thenReturn(ckTo);
      when(ckTo.toString()).thenReturn(BALANCE_PREFIX.getValue() + to);
      when(stub.getStringState(ckTo.toString())).thenReturn(null);
      contract.TransferFrom(ctx, org1UserId, to, 100);
      verify(stub).putStringState(ckTo.toString(), String.valueOf(100));
      verify(stub).putStringState(ckFrom.toString(), String.valueOf(900));
    }

    @Test
    public void whenClientSameAllowanceTransferFrom() {
      ERC20TokenContract contract = new ERC20TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      ClientIdentity ci;
      String to =
          "x509::CN=User3@org2.example.com, L=San Francisco, ST=California,"
              + " C=US::CN=ca.org2.example.com, O=org2.example.com, L=San Francisco, ST=California, C=US";

      CompositeKey ckFromBalance = mock(CompositeKey.class);
      when(stub.getStringState(NAME_KEY.getValue())).thenReturn("ARBTToken");
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), org1UserId))
          .thenReturn(ckFromBalance);
      when(ckFromBalance.toString()).thenReturn(BALANCE_PREFIX.getValue() + org1UserId);
      CompositeKey ckTOBalance = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), to)).thenReturn(ckTOBalance);
      when(ckFromBalance.toString()).thenReturn(BALANCE_PREFIX.getValue() + to);
      when(ctx.getStub()).thenReturn(stub);
      ci = mock(ClientIdentity.class);
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getMSPID()).thenReturn("Org2MSP");
      when(ci.getId()).thenReturn(spender);
      CompositeKey ckAllowance = mock(CompositeKey.class);
      when(stub.createCompositeKey(ALLOWANCE_PREFIX.getValue(), org1UserId, spender))
          .thenReturn(ckAllowance);
      when(ckAllowance.toString()).thenReturn(ALLOWANCE_PREFIX.getValue() + org1UserId + spender);
      when(stub.getStringState(ckAllowance.toString())).thenReturn("200");
      CompositeKey ckFrom = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), org1UserId)).thenReturn(ckFrom);
      when(ckFrom.toString()).thenReturn(BALANCE_PREFIX.getValue() + org1UserId);
      when(stub.getStringState(ckFrom.toString())).thenReturn("1000");
      CompositeKey ckTo = mock(CompositeKey.class);
      when(stub.createCompositeKey(BALANCE_PREFIX.getValue(), to)).thenReturn(ckTo);
      when(ckTo.toString()).thenReturn(BALANCE_PREFIX.getValue() + to);
      when(stub.getStringState(ckTo.toString())).thenReturn(null);

      Throwable thrown =
          catchThrowable(() -> contract.TransferFrom(ctx, org1UserId, org1UserId, 100));
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("Cannot transfer to and from same client account");
    }
  }
}
