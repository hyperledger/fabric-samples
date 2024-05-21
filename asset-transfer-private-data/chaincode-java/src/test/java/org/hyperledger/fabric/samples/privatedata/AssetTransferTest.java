/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.privatedata;

import org.hyperledger.fabric.contract.ClientIdentity;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;
import static org.hyperledger.fabric.samples.privatedata.AssetTransfer.AGREEMENT_KEYPREFIX;
import static org.hyperledger.fabric.samples.privatedata.AssetTransfer.ASSET_COLLECTION_NAME;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public final class AssetTransferTest {

    @Nested
    class InvokeWriteTransaction {

        @Test
        public void createAssetWhenAssetExists() {
            AssetTransfer contract = new AssetTransfer();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            Map<String, byte[]> m = new HashMap<>();
            m.put("asset_properties", DATA_ASSET_1_BYTES);
            when(ctx.getStub().getTransient()).thenReturn(m);
            when(stub.getPrivateData(ASSET_COLLECTION_NAME, TEST_ASSET_1_ID))
                    .thenReturn(DATA_ASSET_1_BYTES);

            Throwable thrown = catchThrowable(() -> {
                contract.CreateAsset(ctx);
            });

            assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause()
                    .hasMessage("Asset asset1 already exists");
            assertThat(((ChaincodeException) thrown).getPayload()).isEqualTo("ASSET_ALREADY_EXISTS".getBytes());
        }

        @Test
        public void createAssetWhenNewAssetIsCreated() {
             AssetTransfer contract = new AssetTransfer();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getMspId()).thenReturn(TEST_ORG_1_MSP);
            ClientIdentity ci = mock(ClientIdentity.class);
            when(ci.getId()).thenReturn(TEST_ORG_1_USER);
            when(ci.getMSPID()).thenReturn(TEST_ORG_1_MSP);
            when(ctx.getClientIdentity()).thenReturn(ci);

            Map<String, byte[]> m = new HashMap<>();
            m.put("asset_properties", DATA_ASSET_1_BYTES);
            when(ctx.getStub().getTransient()).thenReturn(m);

            when(stub.getPrivateData(ASSET_COLLECTION_NAME, TEST_ASSET_1_ID))
                    .thenReturn(new byte[0]);

            Asset created = contract.CreateAsset(ctx);
            assertThat(created).isEqualTo(TEST_ASSET_1);

            verify(stub).putPrivateData(ASSET_COLLECTION_NAME, TEST_ASSET_1_ID, created.serialize());
        }

        @Test
        public void transferAssetWhenExistingAssetIsTransferred() {
            AssetTransfer contract = new AssetTransfer();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getMspId()).thenReturn(TEST_ORG_1_MSP);
            ClientIdentity ci = mock(ClientIdentity.class);
            when(ci.getId()).thenReturn(TEST_ORG_1_USER);
            when(ctx.getClientIdentity()).thenReturn(ci);
            when(ci.getMSPID()).thenReturn(TEST_ORG_1_MSP);
            final String recipientOrgMsp = "TestOrg2";
            final String buyerIdentity = "TestOrg2User";
            Map<String, byte[]> m = new HashMap<>();
            m.put("asset_owner", ("{ \"buyerMSP\": \"" + recipientOrgMsp + "\", \"assetID\": \"" + TEST_ASSET_1_ID + "\" }").getBytes());
            when(ctx.getStub().getTransient()).thenReturn(m);

            when(stub.getPrivateDataHash(anyString(), anyString())).thenReturn("TestHashValue".getBytes());
            when(stub.getPrivateData(ASSET_COLLECTION_NAME, TEST_ASSET_1_ID))
                    .thenReturn(DATA_ASSET_1_BYTES);
            CompositeKey ck = mock(CompositeKey.class);
            when(ck.toString()).thenReturn(AGREEMENT_KEYPREFIX + TEST_ASSET_1_ID);
            when(stub.createCompositeKey(AGREEMENT_KEYPREFIX, TEST_ASSET_1_ID)).thenReturn(ck);
            when(stub.getPrivateData(ASSET_COLLECTION_NAME, AGREEMENT_KEYPREFIX + TEST_ASSET_1_ID)).thenReturn(buyerIdentity.getBytes(UTF_8));
            contract.TransferAsset(ctx);

            Asset exptectedAfterTransfer  = Asset.deserialize("{ \"objectType\": \"testasset\", \"assetID\": \"asset1\", \"color\": \"blue\", \"size\": 5, \"owner\": \"" +  buyerIdentity + "\", \"appraisedValue\": 300 }");

            verify(stub).putPrivateData(ASSET_COLLECTION_NAME, TEST_ASSET_1_ID, exptectedAfterTransfer.serialize());
            String collectionOwner = TEST_ORG_1_MSP + "PrivateCollection";
            verify(stub).delPrivateData(collectionOwner, TEST_ASSET_1_ID);
            verify(stub).delPrivateData(ASSET_COLLECTION_NAME, AGREEMENT_KEYPREFIX + TEST_ASSET_1_ID);
        }
    }

    @Nested
    class QueryReadAssetTransaction {

        @Test
        public void whenAssetExists() {
            AssetTransfer contract = new AssetTransfer();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getPrivateData(ASSET_COLLECTION_NAME, TEST_ASSET_1_ID))
                    .thenReturn(DATA_ASSET_1_BYTES);

            Asset asset = contract.ReadAsset(ctx, TEST_ASSET_1_ID);

            assertThat(asset).isEqualTo(TEST_ASSET_1);
        }

        @Test
        public void whenAssetDoesNotExist() {
            AssetTransfer contract = new AssetTransfer();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getStringState(TEST_ASSET_1_ID)).thenReturn(null);

            Asset asset = contract.ReadAsset(ctx, TEST_ASSET_1_ID);
            assertThat(asset).isNull();
        }

        @Test
        public void invokeUnknownTransaction() {
            AssetTransfer contract = new AssetTransfer();
            Context ctx = mock(Context.class);

            Throwable thrown = catchThrowable(() -> {
                contract.unknownTransaction(ctx);
            });

            assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause()
                    .hasMessage("Undefined contract method called");
            assertThat(((ChaincodeException) thrown).getPayload()).isEqualTo(null);

            verifyNoInteractions(ctx);
        }

    }

    private static final String TEST_ORG_1_MSP = "TestOrg1";
    private static final String TEST_ORG_1_USER = "testOrg1User";

    private static final String TEST_ASSET_1_ID = "asset1";
    private static final Asset TEST_ASSET_1 = new Asset("testasset", "asset1", "blue", 5, TEST_ORG_1_USER);
    private static final byte[] DATA_ASSET_1_BYTES = "{ \"objectType\": \"testasset\", \"assetID\": \"asset1\", \"color\": \"blue\", \"size\": 5, \"owner\": \"testOrg1User\", \"appraisedValue\": 300 }".getBytes();
}
