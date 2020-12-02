/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.privatedata;

import static java.nio.charset.StandardCharsets.UTF_8;

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
import org.hyperledger.fabric.shim.ledger.CompositeKey;

import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Main Chaincode class. A ContractInterface gets converted to Chaincode internally.
 * @see org.hyperledger.fabric.shim.Chaincode
 *
 * Each chaincode transaction function must take, Context as first parameter.
 * Unless specified otherwise via annotation (@Contract or @Transaction), the contract name
 * is the class name (without package)
 * and the transaction name is the method name.
 *
 * To create fabric test-network
 *   cd fabric-samples/test-network
 *   ./network.sh up createChannel -ca -s couchdb
 * To deploy this chaincode to test-network, use the collection config as described in
 * See <a href="https://hyperledger-fabric.readthedocs.io/en/latest/private_data_tutorial.html</a>
 * Change both -ccs sequence & -ccv version args for iterative deployment
 *  ./network.sh deployCC -ccn private -ccp ../asset-transfer-private-data/chaincode-java/ -ccl java -ccep "OR('Org1MSP.peer','Org2MSP.peer')" -cccg ../asset-transfer-private-data/chaincode-go/collections_config.json -ccs 1 -ccv 1
 */
@Contract(
        name = "private",
        info = @Info(
                title = "Asset Transfer Private Data",
                description = "The hyperlegendary asset transfer private data",
                version = "0.0.1-SNAPSHOT",
                license = @License(
                        name = "Apache 2.0 License",
                        url = "http://www.apache.org/licenses/LICENSE-2.0.html"),
                contact = @Contact(
                        email = "a.transfer@example.com",
                        name = "Private Transfer",
                        url = "https://hyperledger.example.com")))
@Default
public final class AssetTransfer implements ContractInterface {

    static final String ASSET_COLLECTION_NAME = "assetCollection";
    static final String AGREEMENT_KEYPREFIX = "transferAgreement";

    private enum AssetTransferErrors {
        INCOMPLETE_INPUT,
        INVALID_ACCESS,
        ASSET_NOT_FOUND,
        ASSET_ALREADY_EXISTS
    }

    /**
     * Retrieves the asset public details with the specified ID from the AssetCollection.
     *
     * @param ctx     the transaction context
     * @param assetID the ID of the asset
     * @return the asset found on the ledger if there was one
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public Asset ReadAsset(final Context ctx, final String assetID) {
        ChaincodeStub stub = ctx.getStub();
        System.out.printf("ReadAsset: collection %s, ID %s\n", ASSET_COLLECTION_NAME, assetID);
        byte[] assetJSON = stub.getPrivateData(ASSET_COLLECTION_NAME, assetID);

        if (assetJSON == null || assetJSON.length == 0) {
            System.out.printf("Asset not found: ID %s\n", assetID);
            return null;
        }

        Asset asset = Asset.deserialize(assetJSON);
        return asset;
    }

    /**
     * Retrieves the asset's AssetPrivateDetails details with the specified ID from the Collection.
     *
     * @param ctx        the transaction context
     * @param collection the org's collection containing asset private details
     * @param assetID    the ID of the asset
     * @return the AssetPrivateDetails from the collection, if there was one
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public AssetPrivateDetails ReadAssetPrivateDetails(final Context ctx, final String collection, final String assetID) {
        ChaincodeStub stub = ctx.getStub();
        System.out.printf("ReadAssetPrivateDetails: collection %s, ID %s\n", collection, assetID);
        byte[] assetPrvJSON = stub.getPrivateData(collection, assetID);

        if (assetPrvJSON == null || assetPrvJSON.length == 0) {
            String errorMessage = String.format("AssetPrivateDetails %s does not exist in collection %s", assetID, collection);
            System.out.println(errorMessage);
            return null;
        }

        AssetPrivateDetails assetpd = AssetPrivateDetails.deserialize(assetPrvJSON);
        return assetpd;
    }

    /**
     * ReadTransferAgreement gets the buyer's identity from the transfer agreement from collection
     *
     * @param ctx     the transaction context
     * @param assetID the ID of the asset
     * @return the AssetPrivateDetails from the collection, if there was one
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public TransferAgreement ReadTransferAgreement(final Context ctx, final String assetID) {
        ChaincodeStub stub = ctx.getStub();

        CompositeKey aggKey = stub.createCompositeKey(AGREEMENT_KEYPREFIX, assetID);
        System.out.printf("ReadTransferAgreement Get: collection %s, ID %s, Key %s\n", ASSET_COLLECTION_NAME, assetID, aggKey);
        byte[] buyerIdentity = stub.getPrivateData(ASSET_COLLECTION_NAME, aggKey.toString());

        if (buyerIdentity == null || buyerIdentity.length == 0) {
            String errorMessage = String.format("BuyerIdentity for asset %s does not exist in TransferAgreement ", assetID);
            System.out.println(errorMessage);
            return null;
        }

        return new TransferAgreement(assetID, new String(buyerIdentity, UTF_8));
    }

    /**
     * GetAssetByRange performs a range query based on the start and end keys provided. Range
     * queries can be used to read data from private data collections, but can not be used in
     * a transaction that also writes to private collection, since transaction may not get endorsed
     * on some peers that do not have the collection.
     *
     * @param ctx      the transaction context
     * @param startKey for ID range of the asset
     * @param endKey   for ID range of the asset
     * @return the asset found on the ledger if there was one
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public Asset[] GetAssetByRange(final Context ctx, final String startKey, final String endKey) throws Exception {
        ChaincodeStub stub = ctx.getStub();
        System.out.printf("GetAssetByRange: start %s, end %s\n", startKey, endKey);

        List<Asset> queryResults = new ArrayList<>();
        // retrieve asset with keys between startKey (inclusive) and endKey(exclusive) in lexical order.
        try (QueryResultsIterator<KeyValue> results = stub.getPrivateDataByRange(ASSET_COLLECTION_NAME, startKey, endKey)) {
            for (KeyValue result : results) {
                if (result.getStringValue() == null || result.getStringValue().length() == 0) {
                    System.err.printf("Invalid Asset json: %s\n", result.getStringValue());
                    continue;
                }
                Asset asset = Asset.deserialize(result.getStringValue());
                queryResults.add(asset);
                System.out.println("QueryResult: " + asset.toString());
            }
        }
        return queryResults.toArray(new Asset[0]);
    }

    // =======Rich queries =========================================================================
    // Two examples of rich queries are provided below (parameterized query and ad hoc query).
    // Rich queries pass a query string to the state database.
    // Rich queries are only supported by state database implementations
    //  that support rich query (e.g. CouchDB).
    // The query string is in the syntax of the underlying state database.
    // With rich queries there is no guarantee that the result set hasn't changed between
    //  endorsement time and commit time, aka 'phantom reads'.
    // Therefore, rich queries should not be used in update transactions, unless the
    // application handles the possibility of result set changes between endorsement and commit time.
    // Rich queries can be used for point-in-time queries against a peer.
    // ============================================================================================

    /**
     * QueryAssetByOwner queries for assets based on assetType, owner.
     * This is an example of a parameterized query where the query logic is baked into the chaincode,
     * and accepting a single query parameter (owner).
     *
     * @param ctx       the transaction context
     * @param assetType type to query for
     * @param owner     asset owner to query for
     * @return the asset found on the ledger if there was one
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public Asset[] QueryAssetByOwner(final Context ctx, final String assetType, final String owner) throws Exception {
        String queryString = String.format("{\"selector\":{\"objectType\":\"%s\",\"owner\":\"%s\"}}", assetType, owner);
        return getQueryResult(ctx, queryString);
    }

    /**
     * QueryAssets uses a query string to perform a query for assets.
     * Query string matching state database syntax is passed in and executed as is.
     * Supports ad hoc queries that can be defined at runtime by the client.
     *
     * @param ctx         the transaction context
     * @param queryString query string matching state database syntax
     * @return the asset found on the ledger if there was one
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public Asset[] QueryAssets(final Context ctx, final String queryString) throws Exception {
        return getQueryResult(ctx, queryString);
    }

    private Asset[] getQueryResult(final Context ctx, final String queryString) throws Exception {
        ChaincodeStub stub = ctx.getStub();
        System.out.printf("QueryAssets: %s\n", queryString);

        List<Asset> queryResults = new ArrayList<Asset>();
        // retrieve asset with keys between startKey (inclusive) and endKey(exclusive) in lexical order.
        try (QueryResultsIterator<KeyValue> results = stub.getPrivateDataQueryResult(ASSET_COLLECTION_NAME, queryString)) {
            for (KeyValue result : results) {
                if (result.getStringValue() == null || result.getStringValue().length() == 0) {
                    System.err.printf("Invalid Asset json: %s\n", result.getStringValue());
                    continue;
                }
                Asset asset = Asset.deserialize(result.getStringValue());
                queryResults.add(asset);
                System.out.println("QueryResult: " + asset.toString());
            }
        }
        return queryResults.toArray(new Asset[0]);
    }


    /**
     * Creates a new asset on the ledger from asset properties passed in as transient map.
     * Asset owner will be inferred from the ClientId via stub api
     *
     * @param ctx the transaction context
     *            Transient map with asset_properties key with asset json as value
     * @return the created asset
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public Asset CreateAsset(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();
        Map<String, byte[]> transientMap = ctx.getStub().getTransient();
        if (!transientMap.containsKey("asset_properties")) {
            String errorMessage = String.format("CreateAsset call must specify asset_properties in Transient map input");
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        byte[] transientAssetJSON = transientMap.get("asset_properties");
        final String assetID;
        final String type;
        final String color;
        int appraisedValue = 0;
        int size = 0;
        try {
            JSONObject json = new JSONObject(new String(transientAssetJSON, UTF_8));
            Map<String, Object> tMap = json.toMap();

            type = (String) tMap.get("objectType");
            assetID = (String) tMap.get("assetID");
            color = (String) tMap.get("color");
            if (tMap.containsKey("size")) {
                size = (Integer) tMap.get("size");
            }
            if (tMap.containsKey("appraisedValue")) {
                appraisedValue = (Integer) tMap.get("appraisedValue");
            }
        } catch (Exception err) {
            String errorMessage = String.format("TransientMap deserialized error: %s ", err);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        //input validations
        String errorMessage = null;
        if (assetID.equals("")) {
            errorMessage = String.format("Empty input in Transient map: assetID");
        }
        if (type.equals("")) {
            errorMessage = String.format("Empty input in Transient map: objectType");
        }
        if (color.equals("")) {
            errorMessage = String.format("Empty input in Transient map: color");
        }
        if (size <= 0) {
            errorMessage = String.format("Empty input in Transient map: size");
        }
        if (appraisedValue <= 0) {
            errorMessage = String.format("Empty input in Transient map: appraisedValue");
        }

        if (errorMessage != null) {
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        Asset asset = new Asset(type, assetID, color, size, "");
        // Check if asset already exists
        byte[] assetJSON = ctx.getStub().getPrivateData(ASSET_COLLECTION_NAME, assetID);
        if (assetJSON != null && assetJSON.length > 0) {
            errorMessage = String.format("Asset %s already exists", assetID);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_ALREADY_EXISTS.toString());
        }

        // Get ID of submitting client identity
        String clientID = ctx.getClientIdentity().getId();

        // Verify that the client is submitting request to peer in their organization
        // This is to ensure that a client from another org doesn't attempt to read or
        // write private data from this peer.
        verifyClientOrgMatchesPeerOrg(ctx);

        //Make submitting client the owner
        asset.setOwner(clientID);
        System.out.printf("CreateAsset Put: collection %s, ID %s\n", ASSET_COLLECTION_NAME, assetID);
        System.out.printf("Put: collection %s, ID %s\n", ASSET_COLLECTION_NAME, new String(asset.serialize()));
        stub.putPrivateData(ASSET_COLLECTION_NAME, assetID, asset.serialize());

        // Get collection name for this organization.
        String orgCollectionName = getCollectionName(ctx);

        //Save AssetPrivateDetails to org collection
        AssetPrivateDetails assetPriv = new AssetPrivateDetails(assetID, appraisedValue);
        System.out.printf("Put AssetPrivateDetails: collection %s, ID %s\n", orgCollectionName, assetID);
        stub.putPrivateData(orgCollectionName, assetID, assetPriv.serialize());

        return asset;
    }

    /**
     * AgreeToTransfer is used by the potential buyer of the asset to agree to the
     * asset value. The agreed to appraisal value is stored in the buying orgs
     * org specifc collection, while the the buyer client ID is stored in the asset collection
     * using a composite key
     * Uses transient map with key asset_value
     *
     * @param ctx the transaction context
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void AgreeToTransfer(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();
        Map<String, byte[]> transientMap = ctx.getStub().getTransient();
        if (!transientMap.containsKey("asset_value")) {
            String errorMessage = String.format("AgreeToTransfer call must specify \"asset_value\" in Transient map input");
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        byte[] transientAssetJSON = transientMap.get("asset_value");
        AssetPrivateDetails assetPriv;
        String assetID;
        try {
            JSONObject json = new JSONObject(new String(transientAssetJSON, UTF_8));
            assetID = json.getString("assetID");
            final int appraisedValue = json.getInt("appraisedValue");

            assetPriv = new AssetPrivateDetails(assetID, appraisedValue);
        } catch (Exception err) {
            String errorMessage = String.format("TransientMap deserialized error %s ", err);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        if (assetID.equals("")) {
            String errorMessage = String.format("Invalid input in Transient map: assetID");
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }
        if (assetPriv.getAppraisedValue() <= 0) { // appraisedValue field must be a positive integer
            String errorMessage = String.format("Input must be positive integer: appraisedValue");
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }
        System.out.printf("AgreeToTransfer: verify asset %s exists\n", assetID);
        Asset existing = ReadAsset(ctx, assetID);
        if (existing == null) {
            String errorMessage = String.format("Asset does not exist in the collection: ", assetID);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }
        // Get collection name for this organization.
        String orgCollectionName = getCollectionName(ctx);

        verifyClientOrgMatchesPeerOrg(ctx);

        //Save AssetPrivateDetails to org collection
        System.out.printf("Put AssetPrivateDetails: collection %s, ID %s\n", orgCollectionName, assetID);
        stub.putPrivateData(orgCollectionName, assetID, assetPriv.serialize());

        String clientID = ctx.getClientIdentity().getId();
        //Write the AgreeToTransfer key in assetCollection
        CompositeKey aggKey = stub.createCompositeKey(AGREEMENT_KEYPREFIX, assetID);
        System.out.printf("AgreeToTransfer Put: collection %s, ID %s, Key %s\n", ASSET_COLLECTION_NAME, assetID, aggKey);
        stub.putPrivateData(ASSET_COLLECTION_NAME, aggKey.toString(), clientID);
    }

    /**
     * TransferAsset transfers the asset to the new owner by setting a new owner ID based on
     * AgreeToTransfer data
     *
     * @param ctx the transaction context
     * @return none
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void TransferAsset(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();
        Map<String, byte[]> transientMap = ctx.getStub().getTransient();
        if (!transientMap.containsKey("asset_owner")) {
            String errorMessage = "TransferAsset call must specify \"asset_owner\" in Transient map input";
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        byte[] transientAssetJSON = transientMap.get("asset_owner");
        final String assetID;
        final String buyerMSP;
        try {
            JSONObject json = new JSONObject(new String(transientAssetJSON, UTF_8));
            assetID = json.getString("assetID");
            buyerMSP = json.getString("buyerMSP");

        } catch (Exception err) {
            String errorMessage = String.format("TransientMap deserialized error %s ", err);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        if (assetID.equals("")) {
            String errorMessage = String.format("Invalid input in Transient map: " + "assetID");
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }
        if (buyerMSP.equals("")) {
            String errorMessage = String.format("Invalid input in Transient map: " + "buyerMSP");
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        System.out.printf("TransferAsset: verify asset %s exists\n", assetID);
        byte[] assetJSON = stub.getPrivateData(ASSET_COLLECTION_NAME, assetID);

        if (assetJSON == null || assetJSON.length == 0) {
            String errorMessage = String.format("Asset %s does not exist in the collection", assetID);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        verifyClientOrgMatchesPeerOrg(ctx);
        Asset thisAsset = Asset.deserialize(assetJSON);
        // Verify transfer details and transfer owner
        verifyAgreement(ctx, assetID, thisAsset.getOwner(), buyerMSP);

        TransferAgreement transferAgreement = ReadTransferAgreement(ctx, assetID);
        if (transferAgreement == null) {
            String errorMessage = String.format("TransferAgreement does not exist for asset: %s", assetID);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        // Transfer asset in private data collection to new owner
        String newOwner = transferAgreement.getBuyerID();
        thisAsset.setOwner(newOwner);

        //Save updated Asset to collection
        System.out.printf("Transfer Asset: collection %s, ID %s to owner %s\n", ASSET_COLLECTION_NAME, assetID, newOwner);
        stub.putPrivateData(ASSET_COLLECTION_NAME, assetID, thisAsset.serialize());

        // delete the key from owners collection
        String ownersCollectionName = getCollectionName(ctx);
        stub.delPrivateData(ownersCollectionName, assetID);

        //Delete the transfer agreement from the asset collection
        CompositeKey aggKey = stub.createCompositeKey(AGREEMENT_KEYPREFIX, assetID);
        System.out.printf("AgreeToTransfer deleteKey: collection %s, ID %s, Key %s\n", ASSET_COLLECTION_NAME, assetID, aggKey);
        stub.delPrivateData(ASSET_COLLECTION_NAME, aggKey.toString());
    }

    /**
     * Deletes a asset & related details from the ledger.
     * Input in transient map: asset_delete
     *
     * @param ctx the transaction context
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void DeleteAsset(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();
        Map<String, byte[]> transientMap = ctx.getStub().getTransient();
        if (!transientMap.containsKey("asset_delete")) {
            String errorMessage = String.format("DeleteAsset call must specify 'asset_delete' in Transient map input");
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        byte[] transientAssetJSON = transientMap.get("asset_delete");
        final String assetID;

        try {
            JSONObject json = new JSONObject(new String(transientAssetJSON, UTF_8));
            assetID = json.getString("assetID");

        } catch (Exception err) {
            String errorMessage = String.format("TransientMap deserialized error: %s ", err);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        System.out.printf("DeleteAsset: verify asset %s exists\n", assetID);
        byte[] assetJSON = stub.getPrivateData(ASSET_COLLECTION_NAME, assetID);

        if (assetJSON == null || assetJSON.length == 0) {
            String errorMessage = String.format("Asset %s does not exist", assetID);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
        }
        String ownersCollectionName = getCollectionName(ctx);
        byte[] apdJSON = stub.getPrivateData(ownersCollectionName, assetID);

        if (apdJSON == null || apdJSON.length == 0) {
            String errorMessage = String.format("Failed to read asset from owner's Collection %s", ownersCollectionName);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
        }
        verifyClientOrgMatchesPeerOrg(ctx);

        // delete the key from asset collection
        System.out.printf("DeleteAsset: collection %s, ID %s\n", ASSET_COLLECTION_NAME, assetID);
        stub.delPrivateData(ASSET_COLLECTION_NAME, assetID);

        // Finally, delete private details of asset
        stub.delPrivateData(ownersCollectionName, assetID);
    }

    // Used by TransferAsset to verify that the transfer is being initiated by the owner and that
    // the buyer has agreed to the same appraisal value as the owner
    private void verifyAgreement(final Context ctx, final String assetID, final String owner, final String buyerMSP) {
        String clienID = ctx.getClientIdentity().getId();

        // Check 1: verify that the transfer is being initiatied by the owner
        if (!clienID.equals(owner)) {
            throw new ChaincodeException("Submitting client identity does not own the asset", AssetTransferErrors.INVALID_ACCESS.toString());
        }

        // Check 2: verify that the buyer has agreed to the appraised value
        String collectionOwner = getCollectionName(ctx); // get owner collection from caller identity
        String collectionBuyer = buyerMSP + "PrivateCollection";

        // Get hash of owners agreed to value
        byte[] ownerAppraisedValueHash = ctx.getStub().getPrivateDataHash(collectionOwner, assetID);
        if (ownerAppraisedValueHash == null) {
            throw new ChaincodeException(String.format("Hash of appraised value for %s does not exist in collection %s", assetID, collectionOwner));
        }

        // Get hash of buyers agreed to value
        byte[] buyerAppraisedValueHash = ctx.getStub().getPrivateDataHash(collectionBuyer, assetID);
        if (buyerAppraisedValueHash == null) {
            throw new ChaincodeException(String.format("Hash of appraised value for %s does not exist in collection %s. AgreeToTransfer must be called by the buyer first.", assetID, collectionBuyer));
        }

        // Verify that the two hashes match
        if (!Arrays.equals(ownerAppraisedValueHash, buyerAppraisedValueHash)) {
            throw new ChaincodeException(String.format("Hash for appraised value for owner %x does not match value for seller %x", ownerAppraisedValueHash, buyerAppraisedValueHash));
        }
    }

    private void verifyClientOrgMatchesPeerOrg(final Context ctx) {
        String clientMSPID = ctx.getClientIdentity().getMSPID();
        String peerMSPID = ctx.getStub().getMspId();

        if (!peerMSPID.equals(clientMSPID)) {
            String errorMessage = String.format("Client from org %s is not authorized to read or write private data from an org %s peer", clientMSPID, peerMSPID);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INVALID_ACCESS.toString());
        }
    }

    private String getCollectionName(final Context ctx) {

        // Get the MSP ID of submitting client identity
        String clientMSPID = ctx.getClientIdentity().getMSPID();
        // Create the collection name
        return clientMSPID + "PrivateCollection";
    }

}
