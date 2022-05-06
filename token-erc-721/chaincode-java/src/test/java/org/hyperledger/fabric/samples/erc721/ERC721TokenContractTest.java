package org.hyperledger.fabric.samples.erc721;

import org.hyperledger.fabric.contract.ClientIdentity;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.samples.erc721.models.Approval;
import org.hyperledger.fabric.samples.erc721.models.NFT;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ERC721TokenContractTest {

  private final class MockKeyValue implements KeyValue {

    private final String key;
    private final String value;

    MockKeyValue(final String key, final String value) {
      super();
      this.key = key;
      this.value = value;
    }

    @Override
    public String getKey() {
      return this.key;
    }

    @Override
    public String getStringValue() {
      return this.value;
    }

    @Override
    public byte[] getValue() {
      return this.value.getBytes();
    }
  }

  private final class MockAssetResultsIterator implements QueryResultsIterator<KeyValue> {

    private final List<KeyValue> assetList;

    MockAssetResultsIterator(final List<KeyValue> list) {
      super();
      this.assetList = list;
    }

    @Override
    public Iterator<KeyValue> iterator() {
      return assetList.iterator();
    }

    @Override
    public void close() throws Exception {
      // do nothing
    }
  }

  @Nested
  class InvokeERC721TokenBalanceOf {

    @Test
    public void invokeToGetTokenBalance() {
      ERC721TokenContract contract = new ERC721TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      List<KeyValue> list = new ArrayList<>();
      when(stub.getStringState(ContractConstants.NAMEKEY.getValue())).thenReturn("ARBTToken");
      list.add(new MockKeyValue("balance_Alice_101", "\u0000"));
      list.add(new MockKeyValue("balance_Alice_101", "\u0000"));
      when(ctx.getStub()).thenReturn(stub);
      CompositeKey balanceKey =
          stub.createCompositeKey(ContractConstants.BALANCE.getValue(), "Alice");
      when(stub.getStateByPartialCompositeKey(balanceKey))
          .thenReturn(new MockAssetResultsIterator(list));
      long balance = contract.BalanceOf(ctx, "Alice");
      assertThat(balance).isEqualTo(2);
    }

    @Test
    public void invokeToGetOwnerOf() {
      ERC721TokenContract contract = new ERC721TokenContract();
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      NFT nft = new NFT("101", "Alicd", "http://test.com", "");
      when(ctx.getStub()).thenReturn(stub);
      CompositeKey ck = mock(CompositeKey.class);
      when(stub.getStringState(ContractConstants.NAMEKEY.getValue())).thenReturn("ARBTToken");
      when(ck.toString()).thenReturn(ContractConstants.NFT.getValue() + "101");
      when(stub.createCompositeKey(ContractConstants.NFT.getValue(), "101")).thenReturn(ck);
      when(stub.getStringState(ck.toString())).thenReturn(nft.toJSONString());
      String owner = contract.OwnerOf(ctx, "101");
      assertThat(owner).isEqualTo(nft.getOwner());
    }
  }

  @Nested
  class ERC721TransferFromFunctionTest {

    private Context ctx = null;
    private ChaincodeStub stub = null;
    private ERC721TokenContract contract = null;
    private NFT currentNFT = null;
    private NFT updatedNFT = null;

    @BeforeEach
    public void initialize() {

      this.currentNFT = new NFT("101", "Alice", "http://test.com", "Charlie");
      this.updatedNFT = new NFT("101", "Bob", "http://test.com", "");
      this.ctx = mock(Context.class);
      this.stub = mock(ChaincodeStub.class);
      when(this.ctx.getStub()).thenReturn(stub);
      contract = new ERC721TokenContract();
      when(stub.getStringState(ContractConstants.NAMEKEY.getValue())).thenReturn("ARBTToken");
      CompositeKey ck1 = mock(CompositeKey.class);
      when(ck1.toString()).thenReturn(ContractConstants.NFT.getValue() + "101");
      when(this.stub.createCompositeKey(ContractConstants.NFT.getValue(), "101")).thenReturn(ck1);
      when(stub.getStringState(ck1.toString())).thenReturn(currentNFT.toJSONString());
      CompositeKey ck2 = mock(CompositeKey.class);
      when(ck2.toString()).thenReturn(ContractConstants.BALANCE.getValue() + "Alice" + "101");
      when(stub.createCompositeKey(ContractConstants.BALANCE.getValue(), "Alice", "101"))
          .thenReturn(ck2);

      CompositeKey ck3 = mock(CompositeKey.class);
      when(ck3.toString()).thenReturn(ContractConstants.BALANCE.getValue() + "Bob" + "101");
      when(stub.createCompositeKey(ContractConstants.BALANCE.getValue(), "Bob", "101"))
          .thenReturn(ck3);
    }

    @Test
    public void whenSenderIsCurrentTokenOwner()
        throws CertificateException, JSONException, IOException {
      Approval approval = new Approval("Alice", "Alice", false);
      CompositeKey ck = mock(CompositeKey.class);
      when(ck.toString()).thenReturn(ContractConstants.APPROVAL.getValue() + "Alice" + "Alice");
      when(this.stub.createCompositeKey(ContractConstants.APPROVAL.getValue(), "Alice", "Alice"))
          .thenReturn(ck);
      when(this.stub.getStringState(ck.toString())).thenReturn(approval.toJSONString());
      ClientIdentity ci = null;
      ci = mock(ClientIdentity.class);
      when(ci.getId()).thenReturn("Alice");
      when(this.ctx.getClientIdentity()).thenReturn(ci);
      contract.TransferFrom(this.ctx, "Alice", "Bob", "101");
      verify(stub)
          .putStringState(ContractConstants.NFT.getValue() + "101", this.updatedNFT.toJSONString());
    }

    @Test
    public void whenSenderisApprovedClientOfToken()
        throws CertificateException, JSONException, IOException {
      Approval approval = new Approval("Alice", "Charlie", false);
      CompositeKey ck = mock(CompositeKey.class);
      when(ck.toString()).thenReturn(ContractConstants.APPROVAL.getValue() + "Alice" + "Charlie");
      when(this.stub.createCompositeKey(ContractConstants.APPROVAL.getValue(), "Alice", "Charlie"))
          .thenReturn(ck);
      when(this.stub.getStringState(ck.toString())).thenReturn(approval.toJSONString());
      ClientIdentity ci = null;
      ci = mock(ClientIdentity.class);
      when(ci.getId()).thenReturn("Charlie");
      when(this.ctx.getClientIdentity()).thenReturn(ci);
      contract.TransferFrom(this.ctx, "Alice", "Bob", "101");
      verify(stub)
          .putStringState(ContractConstants.NFT.getValue() + "101", this.updatedNFT.toJSONString());
    }

    @Test
    public void whenSenderisAuthorizedOperatorOfToken()
        throws CertificateException, JSONException, IOException {
      Approval approval = new Approval("Alice", "Dave", true);
      CompositeKey ck = mock(CompositeKey.class);
      when(ck.toString()).thenReturn(ContractConstants.APPROVAL.getValue() + "Alice" + "Dave");
      when(this.stub.createCompositeKey(ContractConstants.APPROVAL.getValue(), "Alice", "Dave"))
          .thenReturn(ck);
      when(this.stub.getStringState(ck.toString())).thenReturn(approval.toJSONString());
      ClientIdentity ci = null;
      ci = mock(ClientIdentity.class);
      when(ci.getId()).thenReturn("Dave");
      when(this.ctx.getClientIdentity()).thenReturn(ci);
      contract.TransferFrom(this.ctx, "Alice", "Bob", "101");
      verify(stub)
          .putStringState(ContractConstants.NFT.getValue() + "101", this.updatedNFT.toJSONString());
    }

    @Test
    public void whenSenderIsInvalid() throws CertificateException, JSONException, IOException {
      Approval approval = new Approval("Alice", "Alice", false);
      CompositeKey ck = mock(CompositeKey.class);
      when(ck.toString()).thenReturn(ContractConstants.APPROVAL.getValue() + "Alice" + "Dev");
      when(this.stub.createCompositeKey(ContractConstants.APPROVAL.getValue(), "Alice", "Dev"))
          .thenReturn(ck);
      when(this.stub.getStringState(ck.toString())).thenReturn(approval.toJSONString());
      ClientIdentity ci = null;
      ci = mock(ClientIdentity.class);
      when(ci.getId()).thenReturn("Dev");
      when(this.ctx.getClientIdentity()).thenReturn(ci);
      Throwable thrown =
          catchThrowable(
              () -> {
                contract.TransferFrom(this.ctx, "Alice", "Bob", "101");
              });
      String message =
          String.format(
              "The sender %s is not the current owner nor an authorized operator of the token %s.",
              "Dev", "101");
      assertThat(thrown).isInstanceOf(ChaincodeException.class).hasMessage(message);
    }

    @Test
    public void whenCurrentOwnerDoesNotMatch()
        throws CertificateException, JSONException, IOException {
      Approval approval = new Approval("Alice", "Alice", false);
      CompositeKey ck = mock(CompositeKey.class);
      when(ck.toString()).thenReturn(ContractConstants.APPROVAL.getValue() + "Alice" + "Alice");
      when(this.stub.createCompositeKey(ContractConstants.APPROVAL.getValue(), "Alice", "Alice"))
          .thenReturn(ck);
      when(this.stub.getStringState(ck.toString())).thenReturn(approval.toJSONString());
      ClientIdentity ci = null;
      ci = mock(ClientIdentity.class);
      when(ci.getId()).thenReturn("Alice");
      when(this.ctx.getClientIdentity()).thenReturn(ci);
      Throwable thrown =
          catchThrowable(
              () -> {
                contract.TransferFrom(this.ctx, "Charlie", "Bob", "101");
              });
      System.out.println(thrown);
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasMessage("The from Charlie is not the current owner of the token 101.");
    }
  }

  @Nested
  class ERC721ApproveFunctionalitiesTest {

    @Test
    public void invokeAprrove() throws CertificateException, JSONException, IOException {
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      NFT nft = new NFT("101", "Alice", "http://test.com", "");
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(ContractConstants.NAMEKEY.getValue())).thenReturn("ARBTToken");
      CompositeKey ck = mock(CompositeKey.class);
      when(ck.toString()).thenReturn(ContractConstants.NFT.getValue() + "101");
      when(stub.createCompositeKey(ContractConstants.NFT.getValue(), "101")).thenReturn(ck);
      when(stub.getStringState(ck.toString())).thenReturn(nft.toJSONString());
      ClientIdentity ci = null;
      ci = mock(ClientIdentity.class);
      when(ci.getId()).thenReturn("Alice");
      when(ctx.getClientIdentity()).thenReturn(ci);
      CompositeKey ck1 = mock(CompositeKey.class);
      when(ck1.toString()).thenReturn(ContractConstants.APPROVAL.getValue() + "Alice" + "Alice");
      when(stub.createCompositeKey(ContractConstants.APPROVAL.getValue(), "Alice", "Alice"))
          .thenReturn(ck1);
      ERC721TokenContract contract = new ERC721TokenContract();
      contract.Approve(ctx, "Bob", "101");
      verify(stub)
          .putStringState(
              ContractConstants.NFT.getValue() + "101",
              new NFT("101", "Alice", "http://test.com", "Bob").toJSONString());
    }

    @Test
    public void invokeSetApproveForAll() {
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(ContractConstants.NAMEKEY.getValue())).thenReturn("ARBTToken");
      ClientIdentity ci = null;
      ci = mock(ClientIdentity.class);
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getId()).thenReturn("Alice");
      CompositeKey ck1 = mock(CompositeKey.class);
      when(stub.createCompositeKey(ContractConstants.APPROVAL.getValue(), "Alice", "Bob"))
          .thenReturn(ck1);
      when(ck1.toString()).thenReturn(ContractConstants.APPROVAL.getValue() + "Alice" + "Bob");
      ERC721TokenContract contract = new ERC721TokenContract();
      NFT nft = new NFT("101", "Alice", "http://test.com", "");
      CompositeKey ck = mock(CompositeKey.class);
      when(ck.toString()).thenReturn(ContractConstants.NFT.getValue() + "101");
      when(stub.createCompositeKey(ContractConstants.NFT.getValue(), "101")).thenReturn(ck);
      when(stub.getStringState(ck.toString())).thenReturn(nft.toJSONString());
      contract.SetApprovalForAll(ctx, "Bob", true);

      verify(stub)
          .putStringState(ck1.toString(), new Approval("Alice", "Bob", true).toJSONString());
    }

    @Test
    public void invokeGetApproved() {
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(ContractConstants.NAMEKEY.getValue())).thenReturn("ARBTToken");
      ClientIdentity ci = null;
      ci = mock(ClientIdentity.class);
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getId()).thenReturn("Alice");

      ERC721TokenContract contract = new ERC721TokenContract();
      NFT nft = new NFT("101", "Alice", "http://test.com", "Bob");

      CompositeKey ck = mock(CompositeKey.class);
      when(ck.toString()).thenReturn(ContractConstants.NFT.getValue() + "101");
      when(stub.createCompositeKey(ContractConstants.NFT.getValue(), "101")).thenReturn(ck);
      when(stub.getStringState(ck.toString())).thenReturn(nft.toJSONString());
      String approved = contract.GetApproved(ctx, "101");
      assertThat(approved).isEqualTo("Bob");
    }

    @Test
    public void invokeIsApprovedForAll() {
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(ContractConstants.NAMEKEY.getValue())).thenReturn("ARBTToken");
      ClientIdentity ci = null;
      ci = mock(ClientIdentity.class);
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getId()).thenReturn("Alice");
      ERC721TokenContract contract = new ERC721TokenContract();
      Approval approval = new Approval("Alice", "Bob", true);
      CompositeKey ck = mock(CompositeKey.class);
      when(ck.toString()).thenReturn(ContractConstants.APPROVAL.getValue() + "Alice" + "Bob");
      when(stub.createCompositeKey(ContractConstants.APPROVAL.getValue(), "Alice", "Bob"))
          .thenReturn(ck);
      when(stub.getStringState(ck.toString())).thenReturn(approval.toJSONString());
      boolean response = contract.IsApprovedForAll(ctx, "Alice", "Bob");
      assertThat(response).isEqualTo(true);
    }
  }

  @Nested
  class ERC721OptionsTest {

    @Test
    public void invokeGetName() {
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(ContractConstants.NAMEKEY.getValue())).thenReturn("AmadoueNFT");
      ERC721TokenContract contract = new ERC721TokenContract();
      String name = contract.Name(ctx);
      assertThat(name).isEqualTo("AmadoueNFT");
    }

    @Test
    public void invokeGetSysmbol() {
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(ContractConstants.NAMEKEY.getValue())).thenReturn("ARBTToken");
      when(stub.getStringState(ContractConstants.NAMEKEY.getValue())).thenReturn("ANFT");
      ERC721TokenContract contract = new ERC721TokenContract();
      final String name = contract.Name(ctx);
      assertThat(name).isEqualTo("ANFT");
    }

    @Test
    public void invokeTokenURI() {
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(ContractConstants.NAMEKEY.getValue())).thenReturn("ARBTToken");
      ERC721TokenContract contract = new ERC721TokenContract();
      final NFT nft = new NFT("101", "Alice", "http://test.com", "Bob");
      CompositeKey ck = mock(CompositeKey.class);
      when(ck.toString()).thenReturn(ContractConstants.NFT.getValue() + "101");
      when(stub.createCompositeKey(ContractConstants.NFT.getValue(), "101")).thenReturn(ck);
      when(stub.getStringState(ck.toString())).thenReturn(nft.toJSONString());
      String response = contract.TokenURI(ctx, "101");
      assertThat(response).isEqualTo("http://test.com");
    }

    @Test
    public void getTokenTotalSupply() {
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(ContractConstants.NAMEKEY.getValue())).thenReturn("ARBTToken");
      List<KeyValue> list = new ArrayList<>();
      list.add(
          new MockKeyValue(
              "101", new NFT("101", "Alice", "http://test.com", "Bob").toJSONString()));
      list.add(
          new MockKeyValue(
              "balance_Alice_101",
              new NFT("102", "Alice", "http://test.com", "Bob").toJSONString()));

      CompositeKey ck = mock(CompositeKey.class);
      when(ck.toString()).thenReturn(ContractConstants.NFT.getValue());
      when(stub.createCompositeKey(ContractConstants.NFT.getValue())).thenReturn(ck);

      when(stub.getStateByPartialCompositeKey(ck)).thenReturn(new MockAssetResultsIterator(list));
      ERC721TokenContract contract = new ERC721TokenContract();

      final long total = contract.TotalSupply(ctx);
      assertThat(total).isEqualTo(2L);
    }
  }

  @Nested
  class ERC721MintFunctionTest {

    @Test
    public void whenInvokeMintForNewToken() {
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(ContractConstants.NAMEKEY.getValue())).thenReturn("ARBTToken");
      final NFT nft = new NFT("101", "Alice", "DummyURI", "");
      CompositeKey ck = mock(CompositeKey.class);
      when(ck.toString()).thenReturn(ContractConstants.NFT.getValue() + "101");
      when(stub.createCompositeKey(ContractConstants.NFT.getValue(), "101")).thenReturn(ck);
      when(stub.getStringState(ck.toString())).thenReturn(null);

      CompositeKey ck2 = mock(CompositeKey.class);
      when(ck2.toString()).thenReturn(ContractConstants.BALANCE.getValue() + "Alice" + "101");
      when(stub.createCompositeKey(ContractConstants.BALANCE.getValue(), "Alice", "101"))
          .thenReturn(ck2);
      ClientIdentity ci = null;
      ci = mock(ClientIdentity.class);
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getMSPID()).thenReturn("Org1MSP");
      when(ci.getId()).thenReturn("Alice");
      ERC721TokenContract contract = new ERC721TokenContract();
      final NFT response = contract.MintWithTokenURI(ctx, "101", "DummyURI");

      verify(stub).putStringState(ck.toString(), nft.toJSONString());
      verify(stub).putStringState(ck2.toString(), "\u0000");
      assertThat(response.toJSONString()).isEqualTo(nft.toJSONString());
    }

    @Test
    public void whenInvokeMintForAlreadyExistingToken() {
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(ContractConstants.NAMEKEY.getValue())).thenReturn("ARBTToken");
      final NFT nft = new NFT("101", "Alice", "DummyURI", "");
      CompositeKey ck = mock(CompositeKey.class);
      when(ck.toString()).thenReturn(ContractConstants.NFT.getValue() + "101");
      when(stub.createCompositeKey(ContractConstants.NFT.getValue(), "101")).thenReturn(ck);
      when(stub.getStringState(ck.toString())).thenReturn(nft.toJSONString());
      ClientIdentity ci = null;
      ci = mock(ClientIdentity.class);
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getMSPID()).thenReturn("Org1MSP");
      when(ci.getId()).thenReturn("Alice");
      ERC721TokenContract contract = new ERC721TokenContract();
      Throwable thrown =
          catchThrowable(
              () -> {
                contract.MintWithTokenURI(ctx, "101", "DummyURI");
              });
      assertThat(thrown)
          .isInstanceOf(ChaincodeException.class)
          .hasMessage("The token 101 is already minted.");
    }

    @Test
    public void whenInvokeBurnToken() {
      Context ctx = mock(Context.class);
      ChaincodeStub stub = mock(ChaincodeStub.class);
      when(ctx.getStub()).thenReturn(stub);
      when(stub.getStringState(ContractConstants.NAMEKEY.getValue())).thenReturn("ARBTToken");
      final NFT nft = new NFT("101", "Alice", "DummyURI", "");
      CompositeKey ck = mock(CompositeKey.class);
      when(ck.toString()).thenReturn(ContractConstants.NFT.getValue() + "101");
      when(stub.createCompositeKey(ContractConstants.NFT.getValue(), "101")).thenReturn(ck);
      when(stub.getStringState(ck.toString())).thenReturn(nft.toJSONString());
      CompositeKey ck2 = mock(CompositeKey.class);
      when(ck2.toString()).thenReturn(ContractConstants.BALANCE.getValue() + "Alice" + "101");
      when(stub.createCompositeKey(ContractConstants.BALANCE.getValue(), "Alice", "101"))
          .thenReturn(ck2);
      ClientIdentity ci = null;
      ci = mock(ClientIdentity.class);
      when(ctx.getClientIdentity()).thenReturn(ci);
      when(ci.getMSPID()).thenReturn("Org1MSP");
      when(ci.getId()).thenReturn("Alice");
      ERC721TokenContract contract = new ERC721TokenContract();
      contract.Burn(ctx, "101");
      verify(stub).delState(ck.toString());
      verify(stub).delState(ck2.toString());
    }
  }
}
