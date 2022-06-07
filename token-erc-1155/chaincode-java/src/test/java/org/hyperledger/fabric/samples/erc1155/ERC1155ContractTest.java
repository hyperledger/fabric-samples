/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.erc1155;

import com.owlike.genson.Genson;
import org.hyperledger.fabric.contract.ClientIdentity;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.samples.erc1155.models.ApprovalForAll;
import org.hyperledger.fabric.samples.erc1155.models.TransferBatch;
import org.hyperledger.fabric.samples.erc1155.models.TransferBatchMultiRecipient;
import org.hyperledger.fabric.samples.erc1155.models.TransferSingle;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;
import static org.hyperledger.fabric.samples.erc1155.Constants.ORG1_USER_ID;
import static org.hyperledger.fabric.samples.erc1155.Constants.ORG2_USER_ID;
import static org.hyperledger.fabric.samples.erc1155.Constants.ORG3_USER_ID;
import static org.hyperledger.fabric.samples.erc1155.Constants.TOKEN_NAME;
import static org.hyperledger.fabric.samples.erc1155.Constants.TOKEN_SYMBOL;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ERC1155ContractTest extends CommonUtils {

  @Nested
  class ContractInitalizeFunctionsTest {

    private ERC1155Contract contract;
    private Context ctx;
    private ChaincodeStub stub;
    private ClientIdentity ci = null;

    @BeforeEach
    public void initialize() {
      this.contract = new ERC1155Contract();
      this.ctx = mock(Context.class);
      ci = mock(ClientIdentity.class);
      this.stub = mock(ChaincodeStub.class);
      when(this.ctx.getStub()).thenReturn(this.stub);
      when(this.ctx.getClientIdentity()).thenReturn(ci);
    }

    @Test
    public void invokeInitializeTest() {
      setOrg1MspId(this.ci);
      this.contract.Initialize(this.ctx, TOKEN_NAME, TOKEN_SYMBOL);
      verify(this.stub).putStringState(ContractConstants.NAME_KEY.getValue(), TOKEN_NAME);
      verify(this.stub).putStringState(ContractConstants.SYMBOL_KEY.getValue(), TOKEN_SYMBOL);
    }

    @Test
    public void unAuthorizedOrgInitializeTest() {
      setOrg2MspId(this.ci);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.Initialize(ctx, TOKEN_NAME, TOKEN_SYMBOL);
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("Client is not authorized to initialize the contract");
    }

    @Test
    public void queryNameTest() {
      setOrg1MspId(this.ci);
      setTokenNameAndSysmbol(stub);
      String name = this.contract.Name(ctx);
      assertThat(name).isEqualTo(TOKEN_NAME);
    }

    @Test
    public void querySymbolTest() {
      setOrg1MspId(this.ci);
      setTokenNameAndSysmbol(stub);
      String name = this.contract.Symbol(this.ctx);
      assertThat(name).isEqualTo(TOKEN_SYMBOL);
    }
  }

  @Nested
  class ContractMintFunctionsTest {

    private ERC1155Contract contract;
    private Context ctx;
    private ChaincodeStub stub;
    private ClientIdentity ci = null;

    @BeforeEach
    public void initialize() {
      this.contract = new ERC1155Contract();
      this.ctx = mock(Context.class);
      this.stub = mock(ChaincodeStub.class);
      this.ci = mock(ClientIdentity.class);
      when(this.ctx.getStub()).thenReturn(this.stub);
      when(this.ctx.getClientIdentity()).thenReturn(this.ci);
    }

    @Test
    public void invokeMintWithoutInitializeTest() {
      setOrg1MspId(this.ci);
      when(this.ci.getId()).thenReturn(ORG1_USER_ID);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.Mint(this.ctx, ORG1_USER_ID, 1, 1);
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage(
              "Contract options need to be set before calling any function, call Initialize() to initialize contract");
    }

    @Test
    public void unAuthorizedInvokeMintTest() {
      setOrg2MspId(this.ci);
      when(ci.getId()).thenReturn(ORG2_USER_ID);
      setTokenNameAndSysmbol(stub);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.Mint(this.ctx, ORG2_USER_ID, 1, 1);
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("Client is not authorized to set the name and symbol of the token");
    }

    @Test
    public void invalidAmountInvokeMintTest() {
      setOrg1MspId(ci);
      when(this.ci.getId()).thenReturn(ORG1_USER_ID);
      setTokenNameAndSysmbol(stub);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.Mint(this.ctx, ORG2_USER_ID, 1, 0);
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("Mint amount must be a positive integer");
    }

    @Test
    public void invalidAccountInvokeMintTest() {
      setOrg1MspId(this.ci);
      when(this.ci.getId()).thenReturn(ORG1_USER_ID);
      setTokenNameAndSysmbol(stub);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.Mint(ctx, "0x0", 1, 1);
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("mint to the zero address");
    }

    @Test
    public void invokeMintTest() {

      setOrg1MspId(ci);
      when(this.ci.getId()).thenReturn(ORG1_USER_ID);
      setTokenNameAndSysmbol(stub);
      CompositeKey ck =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "1", ORG1_USER_ID);
      this.contract.Mint(this.ctx, ORG1_USER_ID, 1, 1);
      verify(this.stub).putStringState(ck.toString(), "1");
      TransferSingle transferSingleEvent =
          new TransferSingle(ORG1_USER_ID, "0x0", ORG1_USER_ID, 1, 1);
      verify(this.stub)
          .setEvent("TransferSingle", new Genson().serialize(transferSingleEvent).getBytes(UTF_8));
    }

    @Test
    public void invokeBatchMintWithoutInitializeTest() {
      setOrg1MspId(this.ci);
      when(ci.getId()).thenReturn(ORG1_USER_ID);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.MintBatch(this.ctx, ORG1_USER_ID, new long[] {1}, new long[] {1});
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage(
              "Contract options need to be set before calling any function, call Initialize() to initialize contract");
    }

    @Test
    public void misMatchedTokenIdsAndAmountsInputBatchMintTest() {
      setOrg1MspId(this.ci);
      when(ci.getId()).thenReturn(ORG1_USER_ID);
      setTokenNameAndSysmbol(stub);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.MintBatch(ctx, ORG1_USER_ID, new long[] {1, 2}, new long[] {1});
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("ids and amounts must have the same length");
    }

    @Test
    public void unAuthorizedBatchMintTest() {
      setOrg2MspId(this.ci);
      when(ci.getId()).thenReturn(ORG2_USER_ID);
      setTokenNameAndSysmbol(stub);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.MintBatch(ctx, ORG2_USER_ID, new long[] {1}, new long[] {1});
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("Client is not authorized to set the name and symbol of the token");
    }

    @Test
    public void invalidAmountInvokeMintBatchTest() {
      setOrg1MspId(this.ci);
      when(ci.getId()).thenReturn(ORG1_USER_ID);
      setTokenNameAndSysmbol(stub);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.MintBatch(this.ctx, ORG2_USER_ID, new long[] {1}, new long[] {0});
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("Mint amount must be a positive integer");
    }

    @Test
    public void invalidAccountInvokeMintBatchTest() {
      setOrg1MspId(this.ci);
      when(ci.getId()).thenReturn(ORG1_USER_ID);
      setTokenNameAndSysmbol(stub);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.MintBatch(this.ctx, "0x0", new long[] {1}, new long[] {0});
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("mint to the zero address");
    }

    @Test
    public void invokeBatchMintTest() {
      setOrg1MspId(this.ci);
      when(ci.getId()).thenReturn(ORG1_USER_ID);
      CompositeKey ck1 =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "1", ORG1_USER_ID);
      CompositeKey ck2 =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "2", ORG1_USER_ID);
      setTokenNameAndSysmbol(stub);
      this.contract.MintBatch(ctx, ORG1_USER_ID, new long[] {1, 2}, new long[] {1, 1});
      verify(this.stub).putStringState(ck1.toString(), "1");
      verify(this.stub).putStringState(ck2.toString(), "1");
      TransferBatch transferBatchEvent =
          new TransferBatch(
              ORG1_USER_ID, "0x0", ORG1_USER_ID, new long[] {1, 2}, new long[] {1, 1});
      verify(this.stub)
          .setEvent("TransferBatch", new Genson().serialize(transferBatchEvent).getBytes(UTF_8));
      verify(this.stub)
          .putStringState(
              ContractConstants.BALANCE_PREFIX.getValue() + ORG1_USER_ID + "2" + ORG1_USER_ID, "1");
    }
  }

  @Nested
  class ContractBurnFunctionsTest {

    private ERC1155Contract contract;
    private Context ctx;
    private ChaincodeStub stub;
    private CompositeKey ck1;
    private ClientIdentity ci = null;

    @BeforeEach
    public void initialize() {
      this.contract = new ERC1155Contract();
      this.ctx = mock(Context.class);
      this.stub = mock(ChaincodeStub.class);
      when(this.ctx.getStub()).thenReturn(this.stub);
      ci = mock(ClientIdentity.class);
      ck1 = mock(CompositeKey.class);
      when(this.ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getId()).thenReturn(ORG1_USER_ID);
      when(ck1.toString())
          .thenReturn(
              ContractConstants.BALANCE_PREFIX.getValue() + ORG1_USER_ID + "1" + ORG1_USER_ID);
      when(this.stub.createCompositeKey(
              ContractConstants.BALANCE_PREFIX.getValue(), ORG1_USER_ID, "1", ORG1_USER_ID))
          .thenReturn(ck1);
      when(stub.getStringState(ck1.toString())).thenReturn("");
    }

    @Test
    public void invokeTokenBurnTest() {

      setOrg1MspId(this.ci);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "2", ORG1_USER_ID);
      setTokenNameAndSysmbol(stub);
      List<KeyValue> list = new ArrayList<KeyValue>();
      list.add(new MockKeyValue("1", "10"));
      list.add(new MockKeyValue("2", "10"));
      when(stub.getStateByPartialCompositeKey(
              ContractConstants.BALANCE_PREFIX.getValue(), ORG1_USER_ID, "1"))
          .thenReturn(new MockAssetResultsIterator(list));
      when(stub.splitCompositeKey("1")).thenReturn(ck1);
      this.contract.Burn(ctx, ORG1_USER_ID, 1, 1);
      verify(this.stub).putStringState(ck1.toString(), "9");
    }

    @Test
    public void invokeBurnWithoutInitializeTest() {
      setOrg1MspId(this.ci);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.Burn(ctx, ORG1_USER_ID, 1, 1);
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage(
              "Contract options need to be set before calling any function, call Initialize() to initialize contract");
    }

    @Test
    public void invokeWithInSufficientFundTokenBurnTest() {

      setOrg1MspId(this.ci);
      setTokenNameAndSysmbol(stub);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "2", ORG1_USER_ID);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"));
      when(stub.splitCompositeKey("1")).thenReturn(ck1);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.Burn(ctx, ORG1_USER_ID, 1, 12);
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage(
              String.format(
                  "sender has insufficient funds for token %s, needed funds: %d, available fund: %d",
                  "1", 12, 10));
    }

    @Test
    public void invokeWithZeroAddressTokenBurnTest() {

      setOrg1MspId(this.ci);
      setTokenNameAndSysmbol(stub);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "2", ORG1_USER_ID);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"));
      when(stub.splitCompositeKey("1")).thenReturn(ck1);

      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.Burn(ctx, "0x0", 1, 12);
              });

      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("burn to the zero address");
    }

    @Test
    public void invokeTokenBatchBurnTest() {

      setTokenNameAndSysmbol(stub);
      setOrg1MspId(this.ci);
      CompositeKey ck2 =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "2", ORG1_USER_ID);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"));
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "2"},
          new MockKeyValue("2", "10"));
      when(stub.splitCompositeKey("1")).thenReturn(ck1);
      when(stub.splitCompositeKey("2")).thenReturn(ck2);
      this.contract.BurnBatch(ctx, ORG1_USER_ID, new long[] {1, 2}, new long[] {1, 1});
      verify(this.stub).putStringState(ck1.toString(), "9");
      verify(this.stub).putStringState(ck2.toString(), "9");
    }

    @Test
    public void invokeBurnBatchWithoutInitializeTest() {
      setOrg1MspId(this.ci);
      when(this.ctx.getClientIdentity()).thenReturn(ci);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.BurnBatch(ctx, ORG1_USER_ID, new long[] {1, 2}, new long[] {1, 1});
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage(
              "Contract options need to be set before calling any function, call Initialize() to initialize contract");
    }

    @Test
    public void invokeInsufficientFundTokenBatchBurnTest() {
      setTokenNameAndSysmbol(this.stub);
      setOrg1MspId(this.ci);
      CompositeKey ck2 =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "2", ORG1_USER_ID);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"));
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "2"},
          new MockKeyValue("2", "10"));
      when(stub.splitCompositeKey("1")).thenReturn(ck1);
      when(stub.splitCompositeKey("2")).thenReturn(ck2);

      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.BurnBatch(ctx, ORG1_USER_ID, new long[] {1, 2}, new long[] {12, 12});
              });

      String error =
          String.format(
              "sender has insufficient funds for token %s, needed funds: %d, available fund: %d",
              "1", 12, 10);
      assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause().hasMessage(error);
    }
  }

  @Nested
  class ContractTransferFunctionsTest {

    private ERC1155Contract contract;
    private Context ctx;
    private ChaincodeStub stub;
    private CompositeKey ck1;
    private ClientIdentity ci = null;

    @BeforeEach
    public void initialize() {
      this.contract = new ERC1155Contract();
      this.ctx = mock(Context.class);
      this.stub = mock(ChaincodeStub.class);
      when(this.ctx.getStub()).thenReturn(this.stub);
      ck1 =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "1", ORG1_USER_ID);
      ci = mock(ClientIdentity.class);
      when(ci.getMSPID()).thenReturn("Org1MSP");
      when(ci.getId()).thenReturn(ORG1_USER_ID);
      when(this.ctx.getClientIdentity()).thenReturn(ci);
      when(stub.splitCompositeKey("1")).thenReturn(ck1);
    }

    @Test
    public void invokeTokenTransferTest() {
      CompositeKey org2Balance =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "1", ORG1_USER_ID);
      setTokenNameAndSysmbol(this.stub);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"));
      this.contract.TransferFrom(ctx, ORG1_USER_ID, ORG2_USER_ID, 1, 2);
      verify(this.stub).putStringState(ck1.toString(), "8");
      verify(this.stub).putStringState(org2Balance.toString(), "2");
      TransferSingle transferSingleEvent =
          new TransferSingle(ORG1_USER_ID, ORG1_USER_ID, ORG2_USER_ID, 1, 2);
      verify(this.stub)
          .setEvent("TransferSingle", new Genson().serialize(transferSingleEvent).getBytes(UTF_8));
    }

    @Test
    public void invokeTokenTransferByOperatorTest() {
      when(this.ci.getMSPID()).thenReturn("Org3MSP");
      when(this.ci.getId()).thenReturn(ORG3_USER_ID);
      CompositeKey org2Balance =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "1", ORG1_USER_ID);
      createCompositeKey(
          stub, "true", ContractConstants.APPROVAL_PREFIX, ORG1_USER_ID, ORG3_USER_ID);
      setTokenNameAndSysmbol(this.stub);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"));
      this.contract.TransferFrom(ctx, ORG1_USER_ID, ORG2_USER_ID, 1, 2);
      verify(this.stub).putStringState(ck1.toString(), "8");
      verify(this.stub).putStringState(org2Balance.toString(), "2");
      TransferSingle transferSingleEvent =
          new TransferSingle(ORG3_USER_ID, ORG1_USER_ID, ORG2_USER_ID, 1, 2);
      verify(this.stub)
          .setEvent("TransferSingle", new Genson().serialize(transferSingleEvent).getBytes(UTF_8));
    }

    @Test
    public void invokeTokenTransferWithUnApprovedOperatorTest() {

      setTokenNameAndSysmbol(this.stub);
      when(ci.getMSPID()).thenReturn("Org3MSP");
      when(ci.getId()).thenReturn(ORG3_USER_ID);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "1", ORG1_USER_ID);
      createCompositeKey(stub, "", ContractConstants.APPROVAL_PREFIX, ORG1_USER_ID, ORG3_USER_ID);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"));
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.TransferFrom(ctx, ORG1_USER_ID, ORG2_USER_ID, 1, 2);
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("caller is not owner nor is approved");
    }

    @Test
    public void invokeTokenTransferWithZeroAddressReceiverTest() {
      setTokenNameAndSysmbol(this.stub);
      when(ci.getMSPID()).thenReturn("Org3MSP");
      when(ci.getId()).thenReturn(ORG3_USER_ID);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "1", ORG1_USER_ID);
      createCompositeKey(stub, "", ContractConstants.APPROVAL_PREFIX, ORG1_USER_ID, ORG3_USER_ID);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"));

      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.TransferFrom(ctx, ORG1_USER_ID, "0x0", 1, 2);
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("transfer to the zero address");
    }

    @Test
    public void invokeTokenTransferWithInsufficientBalanceTest() {

      setTokenNameAndSysmbol(this.stub);
      when(ci.getMSPID()).thenReturn("Org3MSP");
      when(ci.getId()).thenReturn(ORG3_USER_ID);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "1", ORG1_USER_ID);
      createCompositeKey(
          stub, "true", ContractConstants.APPROVAL_PREFIX, ORG1_USER_ID, ORG3_USER_ID);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"));

      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.TransferFrom(ctx, ORG1_USER_ID, ORG2_USER_ID, 1, 12);
              });
      String error =
          String.format(
              "sender has insufficient funds for token %s, needed funds: %d, available fund: %d",
              "1", 12, 10);
      assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause().hasMessage(error);
    }

    @Test
    public void invokeTokenTransferBySelfTest() {
      setTokenNameAndSysmbol(this.stub);

      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "1", ORG1_USER_ID);
      createCompositeKey(
          stub, "true", ContractConstants.APPROVAL_PREFIX, ORG1_USER_ID, ORG3_USER_ID);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"));

      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.TransferFrom(ctx, ORG1_USER_ID, ORG1_USER_ID, 1, 12);
              });
      String error = "transfer to self";
      assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause().hasMessage(error);
    }

    @Test
    public void invokeTokenBatchTransferTest() {
      setTokenNameAndSysmbol(this.stub);

      CompositeKey org1BalancePrefix =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "2", ORG1_USER_ID);

      CompositeKey org2Token1BalancePrefix =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "1", ORG1_USER_ID);

      CompositeKey org2Token2BalancePrefix =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "2", ORG1_USER_ID);

      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"),
          new MockKeyValue("1", "10"));
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "2"},
          new MockKeyValue("2", "10"),
          new MockKeyValue("2", "10"));

      when(stub.splitCompositeKey("1")).thenReturn(org1BalancePrefix);
      when(stub.splitCompositeKey("2")).thenReturn(org2Token1BalancePrefix);
      this.contract.BatchTransferFrom(
          ctx, ORG1_USER_ID, ORG2_USER_ID, new long[] {1, 2}, new long[] {1, 1});
      verify(this.stub).putStringState(ck1.toString(), "9");
      verify(this.stub).putStringState(org2Token1BalancePrefix.toString(), "1");
      verify(this.stub).putStringState(ck1.toString(), "9");
      verify(this.stub).putStringState(org2Token2BalancePrefix.toString(), "1");

      TransferBatch transferBatchEvent =
          new TransferBatch(
              ORG1_USER_ID, ORG1_USER_ID, ORG2_USER_ID, new long[] {1, 2}, new long[] {1, 1});
      verify(this.stub)
          .setEvent("TransferBatch", new Genson().serialize(transferBatchEvent).getBytes(UTF_8));
    }

    @Test
    public void invokeTokenBatchTransferByOperatorTest() {

      setTokenNameAndSysmbol(this.stub);
      when(ci.getMSPID()).thenReturn("Org3MSP");
      when(ci.getId()).thenReturn(ORG3_USER_ID);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "2", ORG1_USER_ID);
      CompositeKey org2Token1BalancePrefix =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "1", ORG1_USER_ID);
      CompositeKey org2Token2BalancePrefix =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "2", ORG1_USER_ID);
      createCompositeKey(
          stub, "true", ContractConstants.APPROVAL_PREFIX, ORG1_USER_ID, ORG3_USER_ID);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"),
          new MockKeyValue("1", "10"));
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "2"},
          new MockKeyValue("2", "10"),
          new MockKeyValue("2", "10"));
      when(stub.splitCompositeKey("1")).thenReturn(ck1);
      when(stub.splitCompositeKey("2")).thenReturn(org2Token1BalancePrefix);
      this.contract.BatchTransferFrom(
          ctx, ORG1_USER_ID, ORG2_USER_ID, new long[] {1, 2}, new long[] {1, 1});
      verify(this.stub).putStringState(ck1.toString(), "9");
      verify(this.stub).putStringState(org2Token1BalancePrefix.toString(), "1");
      verify(this.stub).putStringState(ck1.toString(), "9");
      verify(this.stub).putStringState(org2Token2BalancePrefix.toString(), "1");
      TransferBatch transferBatchEvent =
          new TransferBatch(
              ORG3_USER_ID, ORG1_USER_ID, ORG2_USER_ID, new long[] {1, 2}, new long[] {1, 1});
      verify(this.stub)
          .setEvent("TransferBatch", new Genson().serialize(transferBatchEvent).getBytes(UTF_8));
    }

    @Test
    public void invokeTokenBatchTransferByUnApprovedOperatorTest() {
      setTokenNameAndSysmbol(this.stub);
      when(ci.getMSPID()).thenReturn("Org3MSP");
      when(ci.getId()).thenReturn(ORG3_USER_ID);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "1", ORG1_USER_ID);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "2", ORG1_USER_ID);
      createCompositeKey(stub, "", ContractConstants.APPROVAL_PREFIX, ORG1_USER_ID, ORG3_USER_ID);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"),
          new MockKeyValue("1", "10"));
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "2"},
          new MockKeyValue("2", "10"),
          new MockKeyValue("2", "10"));
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.BatchTransferFrom(
                    ctx, ORG1_USER_ID, ORG2_USER_ID, new long[] {1, 2}, new long[] {1, 1});
              });
      String error = "caller is not owner nor is approved";
      assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause().hasMessage(error);
    }

    @Test
    public void invokeTokenBatchTransferWithMismatchedIdsAndAmountInputTest() {
      setTokenNameAndSysmbol(this.stub);
      when(ci.getMSPID()).thenReturn("Org3MSP");
      when(ci.getId()).thenReturn(ORG3_USER_ID);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "1", ORG1_USER_ID);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "2", ORG1_USER_ID);
      createCompositeKey(stub, "", ContractConstants.APPROVAL_PREFIX, ORG1_USER_ID, ORG3_USER_ID);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"),
          new MockKeyValue("1", "10"));
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "2"},
          new MockKeyValue("2", "10"),
          new MockKeyValue("2", "10"));
      when(stub.splitCompositeKey("1")).thenReturn(ck1);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.BatchTransferFrom(
                    ctx, ORG1_USER_ID, ORG2_USER_ID, new long[] {1}, new long[] {1, 1});
              });
      String error = "ids and amounts must have the same length";
      assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause().hasMessage(error);
    }

    @Test
    public void invokeTokenBatchTransferMultiReceipentByOperatorTest() {

      setTokenNameAndSysmbol(this.stub);
      when(ci.getMSPID()).thenReturn("Org3MSP");
      when(ci.getId()).thenReturn(ORG3_USER_ID);
      CompositeKey ck2 =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "2", ORG1_USER_ID);
      CompositeKey ck3 =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "1", ORG1_USER_ID);
      createCompositeKey(
          stub, "true", ContractConstants.APPROVAL_PREFIX, ORG1_USER_ID, ORG3_USER_ID);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "2", ORG1_USER_ID);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"),
          new MockKeyValue("1", "10"));
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "2"},
          new MockKeyValue("2", "10"),
          new MockKeyValue("2", "10"));
      when(stub.splitCompositeKey("1")).thenReturn(ck1);
      when(stub.splitCompositeKey("2")).thenReturn(ck2);
      this.contract.BatchTransferFromMultiRecipient(
          ctx,
          ORG1_USER_ID,
          new String[] {ORG2_USER_ID, ORG2_USER_ID},
          new long[] {1, 2},
          new long[] {1, 1});
      verify(this.stub).putStringState(ck1.toString(), "9");
      verify(this.stub).putStringState(ck3.toString(), "1");

      verify(this.stub).putStringState(ck1.toString(), "9");
      verify(this.stub).putStringState(ck3.toString(), "1");
      TransferBatchMultiRecipient transferBatchMultiRecipientEvent =
          new TransferBatchMultiRecipient(
              ORG3_USER_ID,
              ORG1_USER_ID,
              new String[] {ORG2_USER_ID, ORG2_USER_ID},
              new long[] {1, 2},
              new long[] {1, 1});

      verify(this.stub)
          .setEvent(
              "TransferBatchMultiRecipient",
              new Genson().serialize(transferBatchMultiRecipientEvent).getBytes(UTF_8));
    }

    @Test
    public void invokeTokenBatchTransferMultiReceipentTest() {

      setTokenNameAndSysmbol(this.stub);
      CompositeKey ck2 =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "2", ORG1_USER_ID);
      CompositeKey ck3 =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "1", ORG1_USER_ID);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG3_USER_ID, "2", ORG1_USER_ID);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"),
          new MockKeyValue("1", "10"));
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "2"},
          new MockKeyValue("2", "10"),
          new MockKeyValue("2", "10"));
      when(stub.splitCompositeKey("1")).thenReturn(ck1);
      when(stub.splitCompositeKey("2")).thenReturn(ck2);
      this.contract.BatchTransferFromMultiRecipient(
          ctx,
          ORG1_USER_ID,
          new String[] {ORG2_USER_ID, ORG3_USER_ID},
          new long[] {1, 2},
          new long[] {1, 1});
      verify(this.stub).putStringState(ck1.toString(), "9");
      verify(this.stub).putStringState(ck3.toString(), "1");
      verify(this.stub).putStringState(ck1.toString(), "9");
      verify(this.stub).putStringState(ck3.toString(), "1");
      TransferBatchMultiRecipient transferBatchMultiRecipientEvent =
          new TransferBatchMultiRecipient(
              ORG1_USER_ID,
              ORG1_USER_ID,
              new String[] {ORG2_USER_ID, ORG3_USER_ID},
              new long[] {1, 2},
              new long[] {1, 1});
      verify(this.stub)
          .setEvent(
              "TransferBatchMultiRecipient",
              new Genson().serialize(transferBatchMultiRecipientEvent).getBytes(UTF_8));
    }

    @Test
    public void invokeTokenBatchTransferMultiReceipentByUnAutorizedOperatorTest() {

      setTokenNameAndSysmbol(this.stub);
      when(ci.getMSPID()).thenReturn("Org3MSP");
      when(ci.getId()).thenReturn(ORG3_USER_ID);
      CompositeKey ck2 =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "2", ORG1_USER_ID);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "1", ORG1_USER_ID);
      createCompositeKey(
          stub, "false", ContractConstants.APPROVAL_PREFIX, ORG1_USER_ID, ORG3_USER_ID);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "2", ORG1_USER_ID);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"),
          new MockKeyValue("1", "10"));
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "2"},
          new MockKeyValue("2", "10"),
          new MockKeyValue("2", "10"));
      when(stub.splitCompositeKey("1")).thenReturn(ck1);
      when(stub.splitCompositeKey("2")).thenReturn(ck2);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.BatchTransferFromMultiRecipient(
                    ctx,
                    ORG1_USER_ID,
                    new String[] {ORG2_USER_ID, ORG2_USER_ID},
                    new long[] {1, 2},
                    new long[] {1, 1});
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("caller is not owner nor is approved");
    }

    @Test
    public void invokeTokenBatchTransferMultiReceipentWithMisMatchedRecipientsAndIdsTest() {

      setTokenNameAndSysmbol(this.stub);
      when(ci.getMSPID()).thenReturn("Org3MSP");
      when(ci.getId()).thenReturn(ORG3_USER_ID);
      CompositeKey ck2 =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "2", ORG1_USER_ID);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "1", ORG1_USER_ID);
      createCompositeKey(
          stub, "false", ContractConstants.APPROVAL_PREFIX, ORG1_USER_ID, ORG3_USER_ID);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "2", ORG1_USER_ID);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"),
          new MockKeyValue("1", "10"));
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "2"},
          new MockKeyValue("2", "10"),
          new MockKeyValue("2", "10"));
      when(stub.splitCompositeKey("1")).thenReturn(ck1);
      when(stub.splitCompositeKey("2")).thenReturn(ck2);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.BatchTransferFromMultiRecipient(
                    ctx,
                    ORG1_USER_ID,
                    new String[] {ORG2_USER_ID},
                    new long[] {1, 2},
                    new long[] {1, 1});
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("recipients, ids, and amounts must have the same length");
    }

    @Test
    public void invokeTokenBatchTransferMultiReceipentWithMisMatchedAmountsAndIdsTest() {

      setTokenNameAndSysmbol(this.stub);
      when(ci.getMSPID()).thenReturn("Org3MSP");
      when(ci.getId()).thenReturn(ORG3_USER_ID);
      CompositeKey ck2 =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "2", ORG1_USER_ID);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "1", ORG1_USER_ID);
      createCompositeKey(
          stub, "false", ContractConstants.APPROVAL_PREFIX, ORG1_USER_ID, ORG3_USER_ID);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "2", ORG1_USER_ID);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"),
          new MockKeyValue("1", "10"));
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "2"},
          new MockKeyValue("2", "10"),
          new MockKeyValue("2", "10"));
      when(stub.splitCompositeKey("1")).thenReturn(ck1);
      when(stub.splitCompositeKey("2")).thenReturn(ck2);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.BatchTransferFromMultiRecipient(
                    ctx,
                    ORG1_USER_ID,
                    new String[] {ORG2_USER_ID},
                    new long[] {1, 2},
                    new long[] {1, 1});
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("recipients, ids, and amounts must have the same length");
    }

    @Test
    public void invokeTokenBatchTransferMultiReceipentWithInsufficientFundTest() {

      setTokenNameAndSysmbol(this.stub);
      CompositeKey ck2 =
          createCompositeKey(
              stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "2", ORG1_USER_ID);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "1", ORG1_USER_ID);
      createCompositeKey(
          stub, "", ContractConstants.BALANCE_PREFIX, ORG3_USER_ID, "2", ORG1_USER_ID);
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"),
          new MockKeyValue("1", "10"));
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "2"},
          new MockKeyValue("2", "10"),
          new MockKeyValue("2", "10"));

      when(stub.splitCompositeKey("1")).thenReturn(ck1);
      when(stub.splitCompositeKey("2")).thenReturn(ck2);

      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.BatchTransferFromMultiRecipient(
                    ctx,
                    ORG1_USER_ID,
                    new String[] {ORG2_USER_ID, ORG3_USER_ID},
                    new long[] {1, 2},
                    new long[] {31, 9});
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage(
              "sender has insufficient funds for token 1, needed funds: 31, available fund: 20");
    }
  }

  @Nested
  class ContractUriFunctionsTest {

    private ERC1155Contract contract;
    private Context ctx;
    private ChaincodeStub stub;
    private ClientIdentity ci = null;

    @BeforeEach
    public void initialize() {
      this.contract = new ERC1155Contract();
      this.ctx = mock(Context.class);
      ci = mock(ClientIdentity.class);
      this.stub = mock(ChaincodeStub.class);
      when(this.ctx.getStub()).thenReturn(this.stub);
      when(this.ctx.getClientIdentity()).thenReturn(ci);
    }

    @Test
    public void setUriTest() {
      setTokenNameAndSysmbol(this.stub);
      setOrg1MspId(this.ci);
      when(ci.getId()).thenReturn(ORG1_USER_ID);
      this.contract.SetURI(ctx, "http://ree/{id}.json");
      verify(this.stub)
          .putStringState(ContractConstants.URI_KEY.getValue(), "http://ree/{id}.json");
    }

    @Test
    public void setUriWithoutIdTest() {
      setTokenNameAndSysmbol(this.stub);
      setOrg1MspId(this.ci);
      when(ci.getId()).thenReturn(ORG1_USER_ID);
      this.contract.SetURI(ctx, "http://ree/{id}.json");
      verify(this.stub)
          .putStringState(ContractConstants.URI_KEY.getValue(), "http://ree/{id}.json");
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.SetURI(ctx, "http://ree/2.json");
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage("failed to set uri, uri should contain '{id}'");
    }

    @Test
    public void invokeGetApproveAllWithoutInitializeTest() {
      setOrg1MspId(this.ci);
      when(ci.getId()).thenReturn(ORG1_USER_ID);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.SetURI(ctx, "http://ree/2.json");
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage(
              "Contract options need to be set before calling any function, call Initialize() to initialize contract");
    }

    @Test
    public void getUriTest() {
      setTokenNameAndSysmbol(this.stub);
      setOrg1MspId(this.ci);
      when(ci.getId()).thenReturn(ORG1_USER_ID);
      when(stub.getStringState(ContractConstants.URI_KEY.getValue()))
          .thenReturn("http://ree/{id}.json");
      String value = this.contract.URI(ctx, 1);
      assertThat(value).isEqualTo("http://ree/{id}.json");
    }
  }

  @Nested
  class ContractApproveAndBalanceFunctionsTest {

    private ERC1155Contract contract;
    private Context ctx;
    private ChaincodeStub stub;
    private ClientIdentity ci = null;

    @BeforeEach
    public void initialize() {
      this.contract = new ERC1155Contract();
      this.ctx = mock(Context.class);
      ci = mock(ClientIdentity.class);
      this.stub = mock(ChaincodeStub.class);
      when(this.ctx.getStub()).thenReturn(this.stub);
      when(this.ctx.getClientIdentity()).thenReturn(ci);
    }

    @Test
    public void setApproveForAllTest() {
      setTokenNameAndSysmbol(this.stub);
      setOrg1MspId(this.ci);
      when(ci.getId()).thenReturn(ORG1_USER_ID);
      CompositeKey ck =
          createCompositeKey(
              stub, "", ContractConstants.APPROVAL_PREFIX, ORG1_USER_ID, ORG2_USER_ID);
      this.contract.SetApprovalForAll(ctx, ORG2_USER_ID, true);
      verify(this.stub).putStringState(ck.toString(), "true");
      ApprovalForAll approvalForAllEvent = new ApprovalForAll(ORG1_USER_ID, ORG2_USER_ID, true);
      verify(this.stub)
          .setEvent("ApprovalForAll", new Genson().serialize(approvalForAllEvent).getBytes(UTF_8));
    }

    @Test
    public void invokeSetApproveAllWithoutInitializeTest() {
      setOrg1MspId(this.ci);
      when(this.ci.getId()).thenReturn(ORG1_USER_ID);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.SetApprovalForAll(ctx, ORG2_USER_ID, true);
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage(
              "Contract options need to be set before calling any function, call Initialize() to initialize contract");
    }

    @Test
    public void isApproveForAllTest() {
      setTokenNameAndSysmbol(this.stub);
      setOrg1MspId(this.ci);
      when(ci.getId()).thenReturn(ORG1_USER_ID);
      CompositeKey ck =
          createCompositeKey(
              stub, "", ContractConstants.APPROVAL_PREFIX, ORG1_USER_ID, ORG2_USER_ID);
      when(stub.getStringState(ck.toString())).thenReturn("true");
      boolean value = this.contract.IsApprovedForAll(ctx, ORG1_USER_ID, ORG2_USER_ID);
      assertThat(value).isEqualTo(true);
    }

    @Test
    public void invokeGetApproveAllWithoutInitializeTest() {
      setOrg1MspId(this.ci);
      when(this.ci.getId()).thenReturn(ORG1_USER_ID);
      Throwable thrown =
          catchThrowable(
              () -> {
                this.contract.IsApprovedForAll(ctx, ORG1_USER_ID, ORG2_USER_ID);
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasNoCause()
          .hasMessage(
              "Contract options need to be set before calling any function, call Initialize() to initialize contract");
    }

    @Test
    public void invokeBroadcastTokenExistanceTest() {
      setTokenNameAndSysmbol(this.stub);
      setOrg1MspId(this.ci);
      when(ci.getId()).thenReturn(ORG1_USER_ID);
      CompositeKey ck =
          createCompositeKey(
              stub, "", ContractConstants.APPROVAL_PREFIX, ORG1_USER_ID, ORG2_USER_ID);
      when(stub.getStringState(ck.toString())).thenReturn("true");
      this.contract.BroadcastTokenExistance(ctx, 1);
      TransferSingle transferSingleEvent = new TransferSingle(ORG1_USER_ID, "0x0", "0x0", 1, 0);
      verify(this.stub)
          .setEvent("TransferSingle", new Genson().serialize(transferSingleEvent).getBytes(UTF_8));
    }

    @Test
    public void getBalanceOfTest() {
      setTokenNameAndSysmbol(this.stub);
      setOrg1MspId(this.ci);
      when(ci.getId()).thenReturn(ORG1_USER_ID);
      createCompositeKey(stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "1");
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"));
      long balance = this.contract.BalanceOf(ctx, ORG1_USER_ID, 1);
      assertThat(balance).isEqualTo(10);
    }

    @Test
    public void getClientAccountBalanceTest() {
      setTokenNameAndSysmbol(this.stub);
      setOrg1MspId(this.ci);
      when(ci.getId()).thenReturn(ORG1_USER_ID);
      createCompositeKey(stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "1");
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"));
      long balance = this.contract.ClientAccountBalance(ctx, 1);
      assertThat(balance).isEqualTo(10);
    }

    @Test
    public void getBalanceOfBatchTest() {

      setTokenNameAndSysmbol(this.stub);
      setOrg1MspId(this.ci);
      when(ci.getId()).thenReturn(ORG1_USER_ID);
      createCompositeKey(stub, "", ContractConstants.BALANCE_PREFIX, ORG1_USER_ID, "1");
      createCompositeKey(stub, "", ContractConstants.BALANCE_PREFIX, ORG2_USER_ID, "2");
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG1_USER_ID, "1"},
          new MockKeyValue("1", "10"));
      setResultsIterator(
          stub,
          ContractConstants.BALANCE_PREFIX,
          new String[] {ORG2_USER_ID, "2"},
          new MockKeyValue("2", "20"));
      long[] balance =
          this.contract.BalanceOfBatch(
              ctx, new String[] {ORG1_USER_ID, ORG2_USER_ID}, new long[] {1, 2});
      assertThat(balance.length).isEqualTo(2);
      assertThat(balance[0]).isEqualTo(10);
      assertThat(balance[1]).isEqualTo(20);
    }
  }
}
