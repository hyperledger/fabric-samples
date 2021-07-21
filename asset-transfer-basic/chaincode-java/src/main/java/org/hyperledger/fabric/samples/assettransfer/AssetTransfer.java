/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.assettransfer;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.Map;
import java.util.HashMap;


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
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

@Contract(
        name = "basic",
        info = @Info(
                title = "Asset Transfer",
                description = "The hyperlegendary asset transfer",
                version = "0.0.1-SNAPSHOT",
                license = @License(
                        name = "Apache 2.0 License",
                        url = "http://www.apache.org/licenses/LICENSE-2.0.html"),
                contact = @Contact(
                        email = "a.transfer@example.com",
                        name = "Adrian Transfer",
                        url = "https://hyperledger.example.com")))
@Default
public final class AssetTransfer implements ContractInterface {

    private Gson gson = new Gson();
    private ObjectMapper om = new ObjectMapper();

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
        ChaincodeStub stub = ctx.getStub();

        CreateAsset(ctx, "asset1", "blue", 5, "Tomoko", 300);
        CreateAsset(ctx, "asset2", "red", 5, "Brad", 400);
        CreateAsset(ctx, "asset3", "green", 10, "Jin Soo", 500);
        CreateAsset(ctx, "asset4", "yellow", 10, "Max", 600);
        CreateAsset(ctx, "asset5", "black", 15, "Adrian", 700);
        CreateAsset(ctx, "asset6", "white", 15, "Michel", 700);

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
        ChaincodeStub stub = ctx.getStub();

        if (AssetExists(ctx, assetID)) {
            String errorMessage = String.format("Asset %s already exists", assetID);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_ALREADY_EXISTS.toString());
        }

        Asset asset = new Asset(assetID, color, size, owner, appraisedValue);
        //deserializing your json into a sorted map, and then serialize the map to get the sorted-by-key json string.
        //TreeMap will enforce a sorting order for you, independent of what order they're supplied in
        String json = gson.toJson(asset);
        try {
            om.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            Map<String, Object> map = om.readValue(json, HashMap.class);
            String sortedJson = om.writeValueAsString(map);
            stub.putStringState(assetID, sortedJson);
        } catch (Exception e) {
        }
        /*String json = gson.toJson(asset);
        TreeMap<String, Object> map = gson.fromJson(json, TreeMap.class);
        String sortedJson = gson.toJson(map);
        stub.putStringState(assetID, sortedJson);*/

        //could use JSON Object approach instead
        /*Map<String, String> sortedMap = gson.fromJson(json, TreeMap.class);     //this will auto-sort by key alphabetically
        JSONObject sortedJSON = new JSONObject();     //recreate new JSON object for sorted result
        for (Map.Entry<String, String> entry : sortedMap.entrySet()) {
            sortedJSON.put(entry.getKey(), entry.getValue());
        }
        stub.putState(assetID, sortedJson);    //wuold do it if possible to write JSONObject to stste  */

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
        ChaincodeStub stub = ctx.getStub();
        String assetJSON = stub.getStringState(assetID);

        if (assetJSON == null || assetJSON.isEmpty()) {
            String errorMessage = String.format("Asset %s does not exist", assetID);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
        }

        Asset asset = gson.fromJson(assetJSON, Asset.class);
        return asset;
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
        ChaincodeStub stub = ctx.getStub();

        if (!AssetExists(ctx, assetID)) {
            String errorMessage = String.format("Asset %s does not exist", assetID);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
        }

        Asset newAsset = new Asset(assetID, color, size, owner, appraisedValue);
        String json = gson.toJson(newAsset);
        try {
            om.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            Map<String, Object> map = om.readValue(json, HashMap.class);
            String sortedJson = om.writeValueAsString(map);//could also use gson instead
            stub.putStringState(assetID, sortedJson);
        } catch (Exception e) {
        }
        /*String json = gson.toJson(asset);
        TreeMap<String, Object> map = gson.fromJson(json, TreeMap.class);
        String sortedJson = gson.toJson(map);
        stub.putStringState(assetID, sortedJson);*/

        //could use JSON Object approach instead
        /*Map<String, String> sortedMap = gson.fromJson(json, TreeMap.class);     //this will auto-sort by key alphabetically
        JSONObject sortedJSON = new JSONObject();     //recreate new JSON object for sorted result
        for (Map.Entry<String, String> entry : sortedMap.entrySet()) {
            sortedJSON.put(entry.getKey(), entry.getValue());
        }
        stub.putState(assetID, sortedJson);    //wuold do it if possible to write JSONObject to stste  */

        return newAsset;
    }

    /**
     * Deletes asset on the ledger.
     *
     * @param ctx the transaction context
     * @param assetID the ID of the asset being deleted
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void DeleteAsset(final Context ctx, final String assetID) {
        ChaincodeStub stub = ctx.getStub();

        if (!AssetExists(ctx, assetID)) {
            String errorMessage = String.format("Asset %s does not exist", assetID);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
        }

        stub.delState(assetID);
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
        ChaincodeStub stub = ctx.getStub();
        String assetJSON = stub.getStringState(assetID);

        return (assetJSON != null && !assetJSON.isEmpty());
    }

    /**
     * Changes the owner of a asset on the ledger.
     *
     * @param ctx the transaction context
     * @param assetID the ID of the asset being transferred
     * @param newOwner the new owner
     * @return the updated asset
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public Asset TransferAsset(final Context ctx, final String assetID, final String newOwner) {
        ChaincodeStub stub = ctx.getStub();
        String assetJSON = stub.getStringState(assetID);

        if (assetJSON == null || assetJSON.isEmpty()) {
            String errorMessage = String.format("Asset %s does not exist", assetID);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
        }

        Asset asset = gson.fromJson(assetJSON, Asset.class);

        Asset newAsset = new Asset(asset.getAssetID(), asset.getColor(), asset.getSize(), newOwner, asset.getAppraisedValue());
        String json = gson.toJson(newAsset);
        try {
            om.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
            Map<String, Object> map = om.readValue(json, HashMap.class);
            String sortedJson = om.writeValueAsString(map);
            stub.putStringState(assetID, sortedJson);
        } catch (Exception e) {
        }
        /*String json = gson.toJson(asset);
        TreeMap<String, Object> map = gson.fromJson(json, TreeMap.class);
        String sortedJson = gson.toJson(map);
        stub.putStringState(assetID, sortedJson);*/

        //could use JSON Object approach instead
        /*Map<String, String> sortedMap = gson.fromJson(json, TreeMap.class);     //this will auto-sort by key alphabetically
        JSONObject sortedJSON = new JSONObject();     //recreate new JSON object for sorted result
        for (Map.Entry<String, String> entry : sortedMap.entrySet()) {
            sortedJSON.put(entry.getKey(), entry.getValue());
        }
        stub.putState(assetID, sortedJson);    //wuold do it if possible to write JSONObject to stste  */

        return newAsset;
    }

    /**
     * Retrieves all assets from the ledger.
     *
     * @param ctx the transaction context
     * @return array of assets found on the ledger
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String GetAllAssets(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();

        List<Asset> queryResults = new ArrayList<Asset>();

        // To retrieve all assets from the ledger use getStateByRange with empty startKey & endKey.
        // Giving empty startKey & endKey is interpreted as all the keys from beginning to end.
        // As another example, if you use startKey = 'asset0', endKey = 'asset9' ,
        // then getStateByRange will retrieve asset with keys between asset0 (inclusive) and asset9 (exclusive) in lexical order.
        QueryResultsIterator<KeyValue> results = stub.getStateByRange("", "");

        for (KeyValue result: results) {
            Asset asset = gson.fromJson(result.getStringValue(), Asset.class);
            queryResults.add(asset);
            System.out.println(asset.toString());
        }

        final String response = gson.toJson(queryResults);

        return response;
    }
}