/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.sbe;

import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ext.sbe.StateBasedEndorsement;
import org.hyperledger.fabric.shim.ext.sbe.impl.StateBasedEndorsementFactory;

import com.owlike.genson.Genson;

@Contract(
        name = "sbe",
        info = @Info(
                title = "Asset Contract",
                description = "Asset Transfer Smart Contract, using State Based Endorsement(SBE), implemented in Java",
                version = "0.0.1-SNAPSHOT",
                license = @License(
                        name = "Apache 2.0 License",
                        url = "http://www.apache.org/licenses/LICENSE-2.0.html")))
@Default
public final class AssetContract implements ContractInterface {

    private final Genson genson = new Genson();

    private enum AssetTransferErrors {
        ASSET_NOT_FOUND,
        ASSET_ALREADY_EXISTS
    }

    /**
     * Creates a new asset.
     * Sets the endorsement policy of the assetId Key, such that current owner Org Peer is required to endorse future updates.
     *
     * @param ctx the transaction context
     * @param assetId the id of the new asset
     * @param value the value of the new asset
     * @param owner the owner of the new asset
     * @return the created asset
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public Asset CreateAsset(final Context ctx, final String assetId, final int value, final String owner) {
        ChaincodeStub stub = ctx.getStub();

        if (AssetExists(ctx, assetId)) {
            String errorMessage = String.format("Asset %s already exists", assetId);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_ALREADY_EXISTS.toString());
        }

        final String ownerOrg = getClientOrgId(ctx);
        Asset asset = new Asset(assetId, value, owner, ownerOrg);
        String assetJSON = genson.serialize(asset);
        stub.putStringState(assetId, assetJSON);

        setAssetStateBasedEndorsement(ctx, assetId, new String[]{ownerOrg});

        return asset;
    }

    /**
     * Retrieves an asset with the given assetId.
     *
     * @param ctx the transaction context
     * @param assetId the id of the asset
     * @return the asset found on the ledger if there was one
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public Asset ReadAsset(final Context ctx, final String assetId) {
        ChaincodeStub stub = ctx.getStub();
        String assetJSON = stub.getStringState(assetId);

        if (assetJSON == null || assetJSON.isEmpty()) {
            String errorMessage = String.format("Asset %s does not exist", assetId);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
        }

        Asset asset = genson.deserialize(assetJSON, Asset.class);
        return asset;
    }

    /**
     * Updates the properties of an existing asset.
     * Needs an endorsement of current owner Org Peer.
     *
     * @param ctx the transaction context
     * @param assetId the id of the asset being updated
     * @param newValue the value of the asset being updated
     * @return the updated asset
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public Asset UpdateAsset(final Context ctx, final String assetId, final int newValue) {
        ChaincodeStub stub = ctx.getStub();

        Asset asset = ReadAsset(ctx, assetId);
        asset.setValue(newValue);
        String updatedAssetJSON = genson.serialize(asset);
        stub.putStringState(assetId, updatedAssetJSON);

        return asset;
    }

    /**
     * Deletes the given asset.
     * Needs an endorsement of current owner Org Peer.
     *
     * @param ctx the transaction context
     * @param assetId the id of the asset being deleted
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void DeleteAsset(final Context ctx, final String assetId) {
        ChaincodeStub stub = ctx.getStub();

        if (!AssetExists(ctx, assetId)) {
            String errorMessage = String.format("Asset %s does not exist", assetId);
            System.out.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
        }

        stub.delState(assetId);
    }

    /**
     * Updates the owner & ownerOrg field of asset with given assetId, ownerOrg must be a valid Org MSP Id.
     * Needs an endorsement of current owner Org Peer.
     * Re-sets the endorsement policy of the assetId Key, such that new owner Org Peer is required to endorse future updates.
     *
     * @param ctx the transaction context
     * @param assetId the id of the asset being transferred
     * @param newOwner the new owner
     * @param newOwnerOrg the new owner Org MSPID
     * @return the updated asset
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public Asset TransferAsset(final Context ctx, final String assetId, final String newOwner, final String newOwnerOrg) {
        ChaincodeStub stub = ctx.getStub();

        Asset asset = ReadAsset(ctx, assetId);
        asset.setOwner(newOwner);
        asset.setOwnerOrg(newOwnerOrg);
        String updatedAssetJSON = genson.serialize(asset);
        stub.putStringState(assetId, updatedAssetJSON);

        setAssetStateBasedEndorsement(ctx, assetId, new String[]{newOwnerOrg});

        return asset;
    }

    /**
     * Checks the existence of the asset.
     *
     * @param ctx the transaction context
     * @param assetId the id of the asset
     * @return boolean indicating the existence of the asset
     */
    private boolean AssetExists(final Context ctx, final String assetId) {
        ChaincodeStub stub = ctx.getStub();
        String assetJSON = stub.getStringState(assetId);

        return (assetJSON != null && !assetJSON.isEmpty());
    }

    /**
     * Sets an endorsement policy to the assetId Key.
     * Enforces that the owner Org Peers must endorse future update transactions for the specified assetId Key.
     *
     * @param ctx the transaction context
     * @param assetId the id of the asset
     * @param ownerOrgs the list of Owner Org MSPID's
     */
    private void setAssetStateBasedEndorsement(final Context ctx, final String assetId, final String[] ownerOrgs) {
        StateBasedEndorsement stateBasedEndorsement = StateBasedEndorsementFactory.getInstance().newStateBasedEndorsement(null);
        stateBasedEndorsement.addOrgs(StateBasedEndorsement.RoleType.RoleTypeMember, ownerOrgs);
        ctx.getStub().setStateValidationParameter(assetId, stateBasedEndorsement.policy());
    }

    /**
     * Retrieves the client's OrgId (MSPID)
     *
     * @param ctx the transaction context
     * @return String value of the Org MSPID
     */
    private String getClientOrgId(final Context ctx) {
        return ctx.getClientIdentity().getMSPID();
    }
}
