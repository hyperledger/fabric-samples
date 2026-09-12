/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.assettransfer;

import java.util.ArrayList;
import java.util.List;

import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.hyperledger.fabric.shim.ledger.QueryResultsIteratorWithMetadata;

import com.owlike.genson.Genson;

@Contract(
        name = "ledger-queries",
        info = @Info(
                title = "Asset Transfer Ledger Queries",
                description = "The Asset Transfer Ledger Queries sample chaincode",
                version = "1.0.0",
                license = @License(
                        name = "Apache 2.0 License",
                        url = "http://www.apache.org/licenses/LICENSE-2.0.html"),
                contact = @Contact(
                        email = "a.transfer@example.com",
                        name = "Adrian Transfer",
                        url = "https://hyperledger.example.com")))
@Default
public final class AssetTransfer implements ContractInterface {

    private final Genson genson = new Genson();

    private enum AssetTransferErrors {
        ASSET_NOT_FOUND,
        ASSET_ALREADY_EXISTS
    }

    /**
     * Creates some initial assets on the ledger.
     *
     * @param ctx the transaction context
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void InitLedger(final Context ctx) {
        putAsset(ctx, new Asset("asset", "asset1", "blue", 5, "Tomoko", 300));
        putAsset(ctx, new Asset("asset", "asset2", "red", 5, "Brad", 400));
        putAsset(ctx, new Asset("asset", "asset3", "green", 10, "Jin Soo", 500));
        putAsset(ctx, new Asset("asset", "asset4", "yellow", 10, "Max", 600));
        putAsset(ctx, new Asset("asset", "asset5", "black", 15, "Adrian", 700));
        putAsset(ctx, new Asset("asset", "asset6", "white", 15, "Michel", 700));

    }

    /**
     * Creates a new asset on the ledger.
     *
     * @param ctx the transaction context
     * @param assetID the ID of the new asset
     * @param color the color of the new asset
     * @param size the size for the new asset
     * @param owner the owner of the new asset
     * @param appraisedValue the appraisedValue of the new asset
     * @return the created asset
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public Asset CreateAsset(final Context ctx, final String assetID, final String color, final int size,
        final String owner, final int appraisedValue) {

        if (AssetExists(ctx, assetID)) {
            String errorMessage = String.format("Asset %s already exists", assetID);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_ALREADY_EXISTS.toString());
        }

        return putAsset(ctx, new Asset("asset", assetID, color, size, owner, appraisedValue));
    }

    private Asset putAsset(final Context ctx, final Asset asset) {
        String sortedJson = genson.serialize(asset);
        ctx.getStub().putStringState(asset.getAssetID(), sortedJson);

        // Create composite key for color~name index
        String indexKey = ctx.getStub().createCompositeKey("color~name", asset.getColor(), asset.getAssetID()).toString();
        ctx.getStub().putState(indexKey, new byte[] {0x00});

        return asset;
    }

    /**
     * Retrieves an asset with the specified ID from the ledger.
     *
     * @param ctx the transaction context
     * @param assetID the ID of the asset
     * @return the asset found on the ledger if there was one
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public Asset ReadAsset(final Context ctx, final String assetID) {
        String assetJSON = ctx.getStub().getStringState(assetID);

        if (assetJSON == null || assetJSON.isEmpty()) {
            String errorMessage = String.format("Asset %s does not exist", assetID);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
        }

        return genson.deserialize(assetJSON, Asset.class);
    }

    /**
     * Updates the properties of an asset on the ledger.
     *
     * @param ctx the transaction context
     * @param assetID the ID of the asset being updated
     * @param color the color of the asset being updated
     * @param size the size of the asset being updated
     * @param owner the owner of the asset being updated
     * @param appraisedValue the appraisedValue of the asset being updated
     * @return the transferred asset
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public Asset UpdateAsset(final Context ctx, final String assetID, final String color, final int size,
        final String owner, final int appraisedValue) {

        Asset asset = ReadAsset(ctx, assetID);
        
        // Delete old index if color changed
        if (!asset.getColor().equals(color)) {
            String oldIndexKey = ctx.getStub().createCompositeKey("color~name", asset.getColor(), asset.getAssetID()).toString();
            ctx.getStub().delState(oldIndexKey);
        }

        Asset updatedAsset = new Asset("asset", assetID, color, size, owner, appraisedValue);
        return putAsset(ctx, updatedAsset);
    }

    /**
     * Deletes asset on the ledger.
     *
     * @param ctx the transaction context
     * @param assetID the ID of the asset being deleted
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void DeleteAsset(final Context ctx, final String assetID) {
        Asset asset = ReadAsset(ctx, assetID);

        ctx.getStub().delState(assetID);
        
        // Delete composite key index
        String indexKey = ctx.getStub().createCompositeKey("color~name", asset.getColor(), asset.getAssetID()).toString();
        ctx.getStub().delState(indexKey);
    }

    /**
     * Checks the existence of the asset on the ledger
     *
     * @param ctx the transaction context
     * @param assetID the ID of the asset
     * @return boolean indicating the existence of the asset
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public boolean AssetExists(final Context ctx, final String assetID) {
        String assetJSON = ctx.getStub().getStringState(assetID);

        return (assetJSON != null && !assetJSON.isEmpty());
    }

    /**
     * Changes the owner of a asset on the ledger.
     *
     * @param ctx the transaction context
     * @param assetID the ID of the asset being transferred
     * @param newOwner the new owner
     * @return the old owner
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String TransferAsset(final Context ctx, final String assetID, final String newOwner) {
        Asset asset = ReadAsset(ctx, assetID);
        String oldOwner = asset.getOwner();

        Asset updatedAsset = new Asset(asset.getDocType(), asset.getAssetID(), asset.getColor(), asset.getSize(), newOwner, asset.getAppraisedValue());
        putAsset(ctx, updatedAsset);

        return oldOwner;
    }

    /**
     * GetAssetsByRange performs a range query based on the start and end keys provided.
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetAssetsByRange(final Context ctx, final String startKey, final String endKey) {
        QueryResultsIterator<KeyValue> results = ctx.getStub().getStateByRange(startKey, endKey);
        List<Asset> queryResults = new ArrayList<>();

        for (KeyValue res : results) {
            Asset asset = genson.deserialize(res.getStringValue(), Asset.class);
            queryResults.add(asset);
        }

        return genson.serialize(queryResults);
    }

    /**
     * TransferAssetByColor will transfer assets of a given color to a certain new owner.
     * This method demonstrates the use of composite keys for range queries.
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void TransferAssetByColor(final Context ctx, final String color, final String newOwner) {
        QueryResultsIterator<KeyValue> results = ctx.getStub().getStateByPartialCompositeKey("color~name", color);

        for (KeyValue res : results) {
            String compositeKey = res.getKey();
            String[] keyParts = ctx.getStub().splitCompositeKey(compositeKey).getAttributes().toArray(new String[0]);
            
            if (keyParts.length > 0) {
                String assetID = keyParts[0]; // The second part of the composite key is the ID
                TransferAsset(ctx, assetID, newOwner);
            }
        }
    }

    /**
     * GetQueryResultForQueryString executes the passed in query string.
     * Result set is built and returned as a list of assets.
     */
    private String getQueryResultForQueryString(final ChaincodeStub stub, final String queryString) {
        QueryResultsIterator<KeyValue> results = stub.getQueryResult(queryString);
        List<Asset> queryResults = new ArrayList<>();

        for (KeyValue res: results) {
            Asset asset = genson.deserialize(res.getStringValue(), Asset.class);
            queryResults.add(asset);
        }

        return genson.serialize(queryResults);
    }

    /**
     * QueryAssetsByOwner queries for assets based on the passed in owner.
     * This is an example of a parameterized query where the query logic is baked into the chaincode,
     * and accepting a single query parameter (owner).
     * Only available on state databases that support rich query (e.g. CouchDB)
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String QueryAssetsByOwner(final Context ctx, final String owner) {
        String queryString = String.format("{\"selector\":{\"docType\":\"asset\",\"owner\":\"%s\"}}", owner);
        return getQueryResultForQueryString(ctx.getStub(), queryString);
    }

    /**
     * QueryAssets queries for assets based on a passed in query string.
     * This is an example of a rich query where the query string is passed in by the caller.
     * Only available on state databases that support rich query (e.g. CouchDB)
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String QueryAssets(final Context ctx, final String queryString) {
        return getQueryResultForQueryString(ctx.getStub(), queryString);
    }

    /**
     * GetAssetHistory returns the chain of custody for an asset since issuance.
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetAssetHistory(final Context ctx, final String assetID) {
        QueryResultsIterator<KeyModification> results = ctx.getStub().getHistoryForKey(assetID);
        List<KeyModification> history = new ArrayList<>();

        for (KeyModification res: results) {
            history.add(res);
        }

        return genson.serialize(history);
    }

    /**
     * QueryAssetsWithPagination executes a "rich" query, and returns a page of results.
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String QueryAssetsWithPagination(final Context ctx, final String queryString, final int pageSize, final String bookmark) {
        QueryResultsIteratorWithMetadata<KeyValue> results = ctx.getStub().getQueryResultWithPagination(queryString, pageSize, bookmark);
        
        List<Asset> queryResults = new ArrayList<>();
        for (KeyValue res: results) {
            Asset asset = genson.deserialize(res.getStringValue(), Asset.class);
            queryResults.add(asset);
        }

        return genson.serialize(queryResults);
    }

    /**
     * GetAssetsByRangeWithPagination performs a range query based on the start and end key,
     * page size and a bookmark.
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetAssetsByRangeWithPagination(final Context ctx, final String startKey, final String endKey, final int pageSize, final String bookmark) {
        QueryResultsIteratorWithMetadata<KeyValue> results = ctx.getStub().getStateByRangeWithPagination(startKey, endKey, pageSize, bookmark);
        
        List<Asset> queryResults = new ArrayList<>();
        for (KeyValue res: results) {
            Asset asset = genson.deserialize(res.getStringValue(), Asset.class);
            queryResults.add(asset);
        }

        return genson.serialize(queryResults);
    }
}
