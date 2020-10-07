/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.privatedata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hyperledger.fabric.samples.privatedata.AssetTransfer.AGREEMENT_KEYPREFIX;
import static org.hyperledger.fabric.samples.privatedata.AssetTransfer.ASSET_COLLECTION_NAME;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import org.hyperledger.fabric.contract.ClientIdentity;
import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.CompositeKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public final class AssetTransferTest {

    @Nested
    class InvokeWriteTransaction {

        @Test
        public void createAssetWhenAssetExists() {
            AssetTransfer contract = new AssetTransfer();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            Map<String, byte[]> m = new HashMap<String, byte[]>();
            m.put("asset_properties", dataAsset1Bytes);
            when(ctx.getStub().getTransient()).thenReturn(m);
            when(stub.getPrivateData(ASSET_COLLECTION_NAME, testAsset1ID))
                    .thenReturn(dataAsset1Bytes);

            Throwable thrown = catchThrowable(() -> {
                contract.CreateAsset(ctx);
            });

            assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause()
                    .hasMessage("Asset asset1 already exists");
            assertThat(((ChaincodeException) thrown).getPayload()).isEqualTo("ASSET_ALREADY_EXISTS".getBytes());
        }

        @Test
        public void createAssetWhenNewAssetIsCreated() throws CertificateException, IOException {
             AssetTransfer contract = new AssetTransfer();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getMspId()).thenReturn(testOrgOneMSP);
            ClientIdentity ci = mock(ClientIdentity.class);
            when(ci.getId()).thenReturn(testOrg1Client);
            when(ci.getMSPID()).thenReturn(testOrgOneMSP);
            when(ctx.getClientIdentity()).thenReturn(ci);

            Map<String, byte[]> m = new HashMap<String, byte[]>();
            m.put("asset_properties", dataAsset1Bytes);
            when(ctx.getStub().getTransient()).thenReturn(m);

            when(stub.getPrivateData(ASSET_COLLECTION_NAME, testAsset1ID))
                    .thenReturn(new byte[0]);

            Asset created = contract.CreateAsset(ctx);
            assertThat(created).isEqualTo(testAsset1);

            verify(stub).putPrivateData(ASSET_COLLECTION_NAME, testAsset1ID, created.serialize());
        }

        @Test
        public void transferAssetWhenExistingAssetIsTransferred() throws CertificateException, IOException {
            AssetTransfer contract = new AssetTransfer();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getMspId()).thenReturn(testOrgOneMSP);
            ClientIdentity ci = mock(ClientIdentity.class);
            when(ci.getId()).thenReturn(testOrg1Client);
            when(ctx.getClientIdentity()).thenReturn(ci);
            when(ci.getMSPID()).thenReturn(testOrgOneMSP);
            final String recipientOrgMsp = "TestOrg2";
            final String buyerIdentity = "TestOrg2User";
            Map<String, byte[]> m = new HashMap<String, byte[]>();
            m.put("asset_owner", ("{ \"buyerMSP\": \"" + recipientOrgMsp + "\", \"assetID\": \"" + testAsset1ID + "\" }").getBytes());
            when(ctx.getStub().getTransient()).thenReturn(m);

            when(stub.getPrivateDataHash(anyString(), anyString())).thenReturn("TestHashValue".getBytes());
            when(stub.getPrivateData(ASSET_COLLECTION_NAME, testAsset1ID))
                    .thenReturn(dataAsset1Bytes);
            CompositeKey ck = mock(CompositeKey.class);
            when(ck.toString()).thenReturn(AGREEMENT_KEYPREFIX + testAsset1ID);
            when(stub.createCompositeKey(AGREEMENT_KEYPREFIX, testAsset1ID)).thenReturn(ck);
            when(stub.getPrivateData(ASSET_COLLECTION_NAME, AGREEMENT_KEYPREFIX + testAsset1ID)).thenReturn(buyerIdentity.getBytes(UTF_8));
            contract.TransferAsset(ctx);

            Asset exptectedAfterTransfer  = Asset.deserialize("{ \"objectType\": \"testasset\", \"assetID\": \"asset1\", \"color\": \"blue\", \"size\": 5, \"owner\": \"" +  buyerIdentity + "\", \"appraisedValue\": 300 }");

            verify(stub).putPrivateData(ASSET_COLLECTION_NAME, testAsset1ID, exptectedAfterTransfer.serialize());
            String collectionOwner = testOrgOneMSP + "PrivateCollection";
            verify(stub).delPrivateData(collectionOwner, testAsset1ID);
            verify(stub).delPrivateData(ASSET_COLLECTION_NAME, AGREEMENT_KEYPREFIX + testAsset1ID);
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
            when(stub.getPrivateData(ASSET_COLLECTION_NAME, testAsset1ID))
                    .thenReturn(dataAsset1Bytes);

            Asset asset = contract.ReadAsset(ctx, testAsset1ID);

            assertThat(asset).isEqualTo(testAsset1);
        }

        @Test
        public void whenAssetDoesNotExist() {
            AssetTransfer contract = new AssetTransfer();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getStringState(testAsset1ID)).thenReturn(null);

            Asset asset = contract.ReadAsset(ctx, testAsset1ID);
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

            verifyZeroInteractions(ctx);
        }

    }

    private static String testOrgOneMSP = "TestOrg1";
    private static String testOrg1Client = "testOrg1User";

    private static String testAsset1ID = "asset1";
    private static Asset testAsset1 = new Asset("testasset", "asset1", "blue", 5, testOrg1Client);
    private static byte[] dataAsset1Bytes = "{ \"objectType\": \"testasset\", \"assetID\": \"asset1\", \"color\": \"blue\", \"size\": 5, \"owner\": \"testOrg1User\", \"appraisedValue\": 300 }".getBytes();

}
