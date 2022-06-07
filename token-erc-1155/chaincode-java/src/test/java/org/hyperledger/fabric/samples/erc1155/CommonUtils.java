/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.erc1155;

import org.hyperledger.fabric.contract.ClientIdentity;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.hyperledger.fabric.samples.erc1155.Constants.TOKEN_NAME;
import static org.hyperledger.fabric.samples.erc1155.Constants.TOKEN_SYMBOL;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CommonUtils {

  /** Default constructor */
  public CommonUtils() {
    super();
    // TODO Auto-generated constructor stub
  }

  /**
   * Set token name and symbol
   *
   * @param stub
   */
  protected void setTokenNameAndSysmbol(final ChaincodeStub stub) {
    when(stub.getStringState(ContractConstants.NAME_KEY.getValue())).thenReturn(TOKEN_NAME);
    when(stub.getStringState(ContractConstants.SYMBOL_KEY.getValue())).thenReturn(TOKEN_SYMBOL);
  }

  /**
   * Set MSPID
   *
   * @param ci
   */
  protected void setOrg1MspId(final ClientIdentity ci) {
    when(ci.getMSPID()).thenReturn("Org1MSP");
  }

  /**
   * Set org2 msp id
   *
   * @param ci
   */
  protected void setOrg2MspId(final ClientIdentity ci) {
    when(ci.getMSPID()).thenReturn("Org2MSP");
  }

  /**
   * Create composite key
   *
   * @param stub
   * @param returnValue
   * @param firstKey
   * @param keys
   * @return
   */
  protected CompositeKey createCompositeKey(
      final ChaincodeStub stub,
      final String returnValue,
      final ContractConstants firstKey,
      final String... keys) {
    CompositeKey ck = mock(CompositeKey.class);
    StringBuilder keyCombined = new StringBuilder();
    keyCombined.append(ContractConstants.BALANCE_PREFIX.getValue());
    for (String key : keys) {
      keyCombined.append(key);
    }
    when(stub.createCompositeKey(firstKey.getValue(), keys)).thenReturn(ck);
    when(ck.toString()).thenReturn(keyCombined.toString());
    when(stub.getStringState(ck.toString())).thenReturn(returnValue);
    return ck;
  }

  /**
   * Set iterator for the key
   *
   * @param stub
   * @param firstKey
   * @param keys
   * @param values
   */
  protected void setResultsIterator(
      final ChaincodeStub stub,
      final ContractConstants firstKey,
      final String[] keys,
      final MockKeyValue... values) {
    List<KeyValue> list = new ArrayList<KeyValue>();
    for (MockKeyValue value : values) {
      list.add(value);
    }
    when(stub.getStateByPartialCompositeKey(firstKey.getValue(), keys))
        .thenReturn(new MockAssetResultsIterator(list));
  }

  final class MockKeyValue implements KeyValue {

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

  final class MockAssetResultsIterator implements QueryResultsIterator<KeyValue> {

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
}
