/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.events;

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

import java.util.Map;

/**
 * Main Chaincode class.
 *
 * @see org.hyperledger.fabric.shim.Chaincode
 * <p>
 * Each chaincode transaction function must take, Context as first parameter.
 * Unless specified otherwise via annotation (@Contract or @Transaction), the contract name
 * is the class name (without package)
 * and the transaction name is the method name.
 */
@Contract(
        name = "asset-transfer-events-java",
        info = @Info(
                title = "Asset Transfer Events",
                description = "The hyperlegendary asset transfer events sample",
                version = "0.0.1-SNAPSHOT",
                license = @License(
                        name = "Apache 2.0 License",
                        url = "http://www.apache.org/licenses/LICENSE-2.0.html"),
                contact = @Contact(
                        email = "a.transfer@example.com",
                        name = "Fabric Development Team",
                        url = "https://hyperledger.example.com")))
@Default
public final class AssetTransfer implements ContractInterface {

    static final String IMPLICIT_COLLECTION_NAME_PREFIX = "_implicit_org_";
    static final String PRIVATE_PROPS_KEY = "asset_properties";

    /**
     * Retrieves the asset details with the specified ID
     *
     * @param ctx     the transaction context
     * @param assetID the ID of the asset
     * @return the asset found on the ledger. Returns error if asset is not found
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String ReadAsset(final Context ctx, final String assetID) {
        System.out.printf("ReadAsset: ID %s\n", assetID);

        Asset asset = getState(ctx, assetID);
        String privData = readPrivateData(ctx, assetID);
        return asset.serialize(privData);
    }

    /**
     * Creates a new asset on the ledger. Saves the passed private data (asset properties) from transient map input.
     *
     * @param ctx            the transaction context
     *                       Transient map with asset_properties key with asset json as value
     * @param assetID
     * @param color
     * @param size
     * @param owner
     * @param appraisedValue
     * @return the created asset
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public Asset CreateAsset(final Context ctx, final String assetID, final String color, final int size, final String owner, final int appraisedValue) {
        ChaincodeStub stub = ctx.getStub();
        // input validations
        String errorMessage = null;
        if (assetID == null || assetID.equals("")) {
            errorMessage = String.format("Empty input: assetID");
        }
        if (owner == null || owner.equals("")) {
            errorMessage = String.format("Empty input: owner");
        }

        if (errorMessage != null) {
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }
        // Check if asset already exists
        byte[] assetJSON = ctx.getStub().getState(assetID);
        if (assetJSON != null && assetJSON.length > 0) {
            errorMessage = String.format("Asset %s already exists", assetID);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_ALREADY_EXISTS.toString());
        }

        Asset asset = new Asset(assetID, color, size, owner, appraisedValue);

        savePrivateData(ctx, assetID);
        assetJSON = asset.serialize();
        System.out.printf("CreateAsset Put: ID %s Data %s\n", assetID, new String(assetJSON));

        stub.putState(assetID, assetJSON);
        // add Event data to the transaction data. Event will be published after the block containing
        // this transaction is committed
        stub.setEvent("CreateAsset", assetJSON);
        return asset;
    }


    /**
     * TransferAsset transfers the asset to the new owner
     *   Save any private data, if provided in transient map
     *
     * @param ctx the transaction context
     * @param assetID asset to delete
     * @param newOwner new owner for the asset
     * @return none
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void TransferAsset(final Context ctx, final String assetID, final String newOwner) {
        ChaincodeStub stub = ctx.getStub();
        String errorMessage = null;

        if (assetID == null || assetID.equals("")) {
            errorMessage = "Empty input: assetID";
        }
        if (newOwner == null || newOwner.equals("")) {
            errorMessage = "Empty input: newOwner";
        }
        if (errorMessage != null) {
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }
        System.out.printf("TransferAsset: verify asset %s exists\n", assetID);
        Asset thisAsset = getState(ctx, assetID);
        // Transfer asset to new owner
        thisAsset.setOwner(newOwner);

        System.out.printf(" Transfer Asset: ID %s to owner %s\n", assetID, newOwner);
        savePrivateData(ctx, assetID); // save private data if any
        byte[] assetJSON = thisAsset.serialize();

        stub.putState(assetID, assetJSON);
        stub.setEvent("TransferAsset", assetJSON); //publish Event
    }

    /**
     * Update existing asset on the ledger with provided parameters.
     * Saves the passed private data (asset properties) from transient map input.
     *
     * @param ctx            the transaction context
     *                       Transient map with asset_properties key with asset json as value
     * @param assetID
     * @param color
     * @param size
     * @param owner
     * @param appraisedValue
     * @return the created asset
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public Asset UpdateAsset(final Context ctx, final String assetID, final String color, final int size, final String owner, final int appraisedValue) {
        ChaincodeStub stub = ctx.getStub();
        // input validations
        String errorMessage = null;
        if (assetID == null || assetID.equals("")) {
            errorMessage = String.format("Empty input: assetID");
        }

        if (errorMessage != null) {
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }
        // reads from the Statedb. Check if asset already exists
        Asset asset = getState(ctx, assetID);

        if (owner != null) {
            asset.setOwner(owner);
        }
        if (color != null) {
            asset.setColor(color);
        }
        if (size > 0) {
            asset.setSize(size);
        }
        if (appraisedValue > 0) {
            asset.setAppraisedValue(appraisedValue);
        }

        savePrivateData(ctx, assetID);
        byte[] assetJSON = asset.serialize();
        System.out.printf("UpdateAsset Put: ID %s Data %s\n", assetID, new String(assetJSON));
        stub.putState(assetID, assetJSON);
        stub.setEvent("UpdateAsset", assetJSON); //publish Event
        return asset;
    }

    /**
     * Deletes a asset & related details from the ledger.
     *
     * @param ctx the transaction context
     * @param assetID asset to delete
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void DeleteAsset(final Context ctx, final String assetID) {
        ChaincodeStub stub = ctx.getStub();
        System.out.printf("DeleteAsset: verify asset %s exists\n", assetID);
        Asset asset = getState(ctx, assetID);

        System.out.printf(" DeleteAsset:  ID %s\n", assetID);
        // delete private details of asset
        removePrivateData(ctx, assetID);
        stub.delState(assetID);         // delete the key from Statedb
        stub.setEvent("DeleteAsset", asset.serialize()); // publish Event
    }

    private Asset getState(final Context ctx, final String assetID) {
        byte[] assetJSON = ctx.getStub().getState(assetID);
        if (assetJSON == null || assetJSON.length == 0) {
            String errorMessage = String.format("Asset %s does not exist", assetID);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
        }

        try {
            Asset asset = Asset.deserialize(assetJSON);
            return asset;
        } catch (Exception e) {
            throw new ChaincodeException("Deserialize error: " + e.getMessage(), AssetTransferErrors.DATA_ERROR.toString());
        }
    }

    private String readPrivateData(final Context ctx, final String assetKey) {
        String peerMSPID = ctx.getStub().getMspId();
        String clientMSPID = ctx.getClientIdentity().getMSPID();
        String implicitCollectionName = getCollectionName(ctx);
        String privData = null;
        // only if ClientOrgMatchesPeerOrg
        if (peerMSPID.equals(clientMSPID)) {
            System.out.printf(" ReadPrivateData from collection %s, ID %s\n", implicitCollectionName, assetKey);
            byte[] propJSON = ctx.getStub().getPrivateData(implicitCollectionName, assetKey);

            if (propJSON != null && propJSON.length > 0) {
                privData = new String(propJSON, UTF_8);
            }
        }
        return privData;
    }

    private void savePrivateData(final Context ctx, final String assetKey) {
        String peerMSPID = ctx.getStub().getMspId();
        String clientMSPID = ctx.getClientIdentity().getMSPID();
        String implicitCollectionName = getCollectionName(ctx);

        if (peerMSPID.equals(clientMSPID)) {
            Map<String, byte[]> transientMap = ctx.getStub().getTransient();
            if (transientMap != null && transientMap.containsKey(PRIVATE_PROPS_KEY)) {
                byte[] transientAssetJSON = transientMap.get(PRIVATE_PROPS_KEY);

                System.out.printf("Asset's PrivateData Put in collection %s, ID %s\n", implicitCollectionName, assetKey);
                ctx.getStub().putPrivateData(implicitCollectionName, assetKey, transientAssetJSON);
            }
        }
    }

    private void removePrivateData(final Context ctx, final String assetKey) {
        String peerMSPID = ctx.getStub().getMspId();
        String clientMSPID = ctx.getClientIdentity().getMSPID();
        String implicitCollectionName = getCollectionName(ctx);

        if (peerMSPID.equals(clientMSPID)) {
            System.out.printf("PrivateData Delete from collection %s, ID %s\n", implicitCollectionName, assetKey);
            ctx.getStub().delPrivateData(implicitCollectionName, assetKey);
        }
    }

    // Return the implicit collection name, to use for private property persistance
    private String getCollectionName(final Context ctx) {
        // Get the MSP ID of submitting client identity
        String clientMSPID = ctx.getClientIdentity().getMSPID();
        String collectionName = IMPLICIT_COLLECTION_NAME_PREFIX + clientMSPID;
        return collectionName;
    }

    private enum AssetTransferErrors {
        INCOMPLETE_INPUT,
        INVALID_ACCESS,
        ASSET_NOT_FOUND,
        ASSET_ALREADY_EXISTS,
        DATA_ERROR
    }

}
