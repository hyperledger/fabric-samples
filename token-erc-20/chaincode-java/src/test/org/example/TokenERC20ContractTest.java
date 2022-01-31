
/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.example;

import org.hyperledger.fabric.contract.Context;

import org.hyperledger.fabric.contract.ClientIdentity;

import org.example.TokenERC20Contract;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.hyperledger.fabric.shim.ChaincodeException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TokenERC20ContractTest {

  final private String balancePrefix = "balance";
  final private String nameKey = "name";
  final private String symbolKey = "symbol";
  final private String decimalsKey = "decimals";
  final private String totalSupplyKey = "totalSupply";

  @Nested
  class InvokeQueryERC20TokenOPtionsTransaction {

    @Test
    public void whenTokenNameExists() {
      TokenERC20Contract contract = new TokenERC20Contract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(nameKey)).thenReturn("ARBTToken");

      String toknName = contract.tokenName(ctx);

      assertThat(toknName).isEqualTo("ARBTToken");

    }

    @Test
    public void whenTokenNameDoesNotExist() {
      TokenERC20Contract contract = new TokenERC20Contract();
      final Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(nameKey)).thenReturn("");

      Throwable thrown = catchThrowable(() -> {
        contract.tokenName(ctx);
      });

      assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause()
          .hasMessage("Sorry ! Token name not found.");
    }

    @Test
    public void whenTokenSymbolExists() {
      TokenERC20Contract contract = new TokenERC20Contract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(symbolKey)).thenReturn("ARBT");

      String toknName = contract.tokenSymbol(ctx);

      assertThat(toknName).isEqualTo("ARBT");

    }

    @Test
    public void whenTokenSymbolDoesNotExist() {
      TokenERC20Contract contract = new TokenERC20Contract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(symbolKey)).thenReturn("");

      Throwable thrown = catchThrowable(() -> {
        contract.tokenSymbol(ctx);
      });

      assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause()
          .hasMessage("Sorry ! Token symbol not found.");
    }

    @Test
    public void whenTokenDecimalExists() {
      TokenERC20Contract contract = new TokenERC20Contract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(decimalsKey)).thenReturn("18");

      long decimal = contract.decimals(ctx);

      assertThat(decimal).isEqualTo(18);

    }

    @Test
    public void whenTokenDecimalNotExists() {
      TokenERC20Contract contract = new TokenERC20Contract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(decimalsKey)).thenReturn("");

      Throwable thrown = catchThrowable(() -> {
        contract.decimals(ctx);
      });

      assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause()
          .hasMessage("Sorry ! Decimal not found.");
    }

    @Test
    public void whenTokenTotalSupplyExists() {
      TokenERC20Contract contract = new TokenERC20Contract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(totalSupplyKey)).thenReturn("222222222222");

      long totalSupply = contract.totalSupply(ctx);

      assertThat(totalSupply).isEqualTo(222222222222L);

    }

    @Test
    public void whenTokenTotalSupplyNotExists() {
      TokenERC20Contract contract = new TokenERC20Contract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(totalSupplyKey)).thenReturn("");

      Throwable thrown = catchThrowable(() -> {
        contract.totalSupply(ctx);
      });

      assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause()
          .hasMessage("Sorry ! Total Supply  not found.");
    }

    @Test
    public void whenClientAccountIDTest() throws Exception {

      TokenERC20Contract contract = new TokenERC20Contract();
      Context ctx = mock(Context.class);
      final ChaincodeStub stub = new ChaincodeStubNaiveImpl();
      final ClientIdentity identity = new ClientIdentity(stub);
      assertThat(identity.getMSPID()).isEqualTo("Org1MSP");
      when(ctx.getClientIdentity()).thenReturn(identity);
      String id = contract.getClientAccountID(ctx);
      String actualId = "x509::CN=admin, OU=Fabric, O=Hyperledger, ST=North Carolina, C=US::CN=example.com,"
          + " OU=WWW, O=Internet Widgets, L=San Francisco, ST=California, C=US";
      assertThat(id).isEqualTo(actualId);

    }

  }

  @Nested
  class TokenOperationsInvoke {

    @Test
    public void setOptionsTest() {
      TokenERC20Contract contract = new TokenERC20Contract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      contract.setOptions(ctx, "ARBTToken", "ARBT", "18");
      verify(stub).putStringState(nameKey, "ARBTToken");
      verify(stub).putStringState(symbolKey, "ARBT");
      verify(stub).putStringState(decimalsKey, "18");
    }

    @Test
    public void whenOrgMintTokensTest() throws Exception {

      TokenERC20Contract contract = new TokenERC20Contract();
      Context ctx = mock(Context.class);
      final ChaincodeStub stub = new ChaincodeStubNaiveImpl();
      final ClientIdentity identity = new ClientIdentity(stub);
      when(ctx.getClientIdentity()).thenReturn(identity);

      when(ctx.getStub()).thenReturn(stub);
      contract.mint(ctx, "1000");
      String totalSupply = stub.getStringState(totalSupplyKey);
      assertThat(totalSupply).isEqualTo("1000");
      String minter = ctx.getClientIdentity().getId();
      CompositeKey balanceKey = stub.createCompositeKey(balancePrefix, minter);
      String updatedBalance = stub.getStringState(balanceKey.toString());
      assertThat(updatedBalance).isEqualTo("1000");

    }

    @Test
    public void whenUserTransferTokenTest() throws Exception {

      TokenERC20Contract contract = new TokenERC20Contract();
      Context ctx = mock(Context.class);
      final ChaincodeStub stub = new ChaincodeStubNaiveImpl();
      final ClientIdentity identity = new ClientIdentity(stub);
      when(ctx.getClientIdentity()).thenReturn(identity);
      when(ctx.getStub()).thenReturn(stub);
      contract.mint(ctx, "1000");
      String minter = ctx.getClientIdentity().getId();
      String _to = "x509::CN=User1@org2.example.com, L=San Francisco, ST=California,"
          + " C=US::CN=ca.org2.example.com, O=org2.example.com, L=San Francisco, ST=California, C=US";
      contract.transfer(ctx, _to, 100);
      CompositeKey toBalanceKey = stub.createCompositeKey(balancePrefix, _to);
      String _toCurrentBalance = stub.getStringState(toBalanceKey.toString());
      Long totalSupply = contract.totalSupply(ctx);
      
      Long fromBalance = contract.balanceOf(ctx, minter);
      
      ((ChaincodeStubNaiveImpl) stub).setCertificate(ChaincodeStubNaiveImpl.CERT_WITH_DNS);
      Long _toBalance = contract.balanceOf(ctx, _to);

      assertThat(totalSupply).isEqualTo(1000);
      assertThat(_toCurrentBalance).isEqualTo("100");
      assertThat(fromBalance).isEqualTo(900);
      assertThat(_toBalance).isEqualTo(100);

    }

    @Test
    public void whenOrgBurnsTokenTest() throws Exception {

      TokenERC20Contract contract = new TokenERC20Contract();
      Context ctx = mock(Context.class);
      final ChaincodeStub stub = new ChaincodeStubNaiveImpl();
      final ClientIdentity identity = new ClientIdentity(stub);
      when(ctx.getClientIdentity()).thenReturn(identity);
      when(ctx.getStub()).thenReturn(stub);
      contract.mint(ctx, "1000");
      String minter = ctx.getClientIdentity().getId();
      contract.burn(ctx, "100");
      Long totalSupply = contract.totalSupply(ctx);
      Long fromBalance = contract.balanceOf(ctx, minter);
      assertThat(totalSupply).isEqualTo(900);
      assertThat(fromBalance).isEqualTo(900);

    }

  }

  @Nested
  class InvokeERC20AllowanceTransactions {

    private Context ctx = null;
    private ChaincodeStub stub = null;
    private ClientIdentity identity = null;
    private TokenERC20Contract contract = null;

    @BeforeEach
    public void initialize() {
      try {

        this.ctx = mock(Context.class);
        this.stub = new ChaincodeStubNaiveImpl();
        this.identity = new ClientIdentity(stub);
        when(ctx.getClientIdentity()).thenReturn(identity);
        when(ctx.getStub()).thenReturn(stub);
        contract = new TokenERC20Contract();
        contract.mint(ctx, "1000");

      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    @Test
    public void approveForTokenAllowanceTest() {

      String spender = "x509::CN=User1@org2.example.com, L=San Francisco, ST=California,"
          + " C=US::CN=ca.org2.example.com, O=org2.example.com, L=San Francisco, ST=California, C=US";
      contract.approve(ctx, spender, "200");
      String owner = ctx.getClientIdentity().getId();
      long allowance = contract.allowance(ctx, owner, spender);
      assertThat(allowance).isEqualTo(200);

    }

    @Test
    public void allowanceTransferFromTest() throws Exception {

      /*
       * ChaincodeStub localStub = new ChaincodeStubNaiveImpl(); ((ChaincodeStubNaiveImpl)
       * localStub).setCertificate(ChaincodeStubNaiveImpl.CERT_WITH_DNS); Context localCtx =
       * mock(Context.class); ClientIdentity localidentity = new ClientIdentity(localStub);
       * when(localCtx.getClientIdentity()).thenReturn(localidentity);
       * when(localCtx.getStub()).thenReturn(localStub);
       */

      String spender = "x509::CN=User1@org2.example.com, L=San Francisco, ST=California,"
          + " C=US::CN=ca.org2.example.com, O=org2.example.com, L=San Francisco, ST=California, C=US";
     
      contract.approve(ctx, spender, "200");
      String owner = ctx.getClientIdentity().getId();
          
      ((ChaincodeStubNaiveImpl) stub).setCertificate(ChaincodeStubNaiveImpl.CERT_WITH_DNS);
      identity = new ClientIdentity(stub);
      when(ctx.getClientIdentity()).thenReturn(identity);
      when(ctx.getStub()).thenReturn(stub);
      contract.transferFrom(ctx, owner, spender, "100");
      long allowance = contract.allowance(ctx, owner, spender);
      assertThat(allowance).isEqualTo(100);

    }

  }

}
