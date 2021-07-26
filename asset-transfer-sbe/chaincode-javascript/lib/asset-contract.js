/*
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

const { Context } = require('fabric-contract-api');
const { KeyEndorsementPolicy } = require('fabric-shim');
import * as fabprotos from 'fabric-shim/bundle';

class AssetContract extends Contract {

    // CreateAsset creates a new asset
    // CreateAsset sets the endorsement policy of the assetId Key, such that current owner Org Peer is required to endorse future updates
    async CreateAsset(ctx, assetId, value, owner) {
        const exists = await this.AssetExists(ctx, assetId);
        if (exists) {
            throw new Error(`The asset ${assetId} already exists`);
        }
        const ownerOrg = AssetContract.getClientOrgId(ctx);
        const asset = new Asset();
        asset.ID = assetId;
        asset.Value = value;
        asset.Owner = owner;
        asset.OwnerOrg = ownerOrg;
        const buffer = Buffer.from(JSON.stringify(asset));
        // Create the asset
        await ctx.stub.putState(assetId, buffer);

        // Set the endorsement policy of the assetId Key, such that current owner Org is required to endorse future updates
        await AssetContract.setStateBasedEndorsement(ctx, assetId, [ownerOrg]);

        // Optionally, set the endorsement policy of the assetId Key, such that any 1 Org (N) out of the specified Orgs can endorse future updates
        // await AssetContract.setStateBasedEndorsementNOutOf(ctx, assetId, 1, ["Org1MSP", "Org2MSP"]);
    }

    // ReadAsset returns asset with given assetId
    async ReadAsset(ctx, assetId) {
        const exists = await this.AssetExists(ctx, assetId);
        if (!exists) {
            throw new Error(`The asset ${assetId} does not exist`);
        }
        // Read the asset
        const assetJSON = await ctx.stub.getState(assetId);
        return assetJSON.toString();
    }

    // UpdateAsset updates an existing asset
    // UpdateAsset needs an endorsement of current owner Org Peer
    async UpdateAsset(ctx, assetId, newValue) {
        const assetString = await this.ReadAsset(ctx, assetId);
        const asset = JSON.parse(assetString);
        asset.Value = newValue;
        const buffer = Buffer.from(JSON.stringify(asset));
        // Update the asset
        await ctx.stub.putState(assetId, buffer);
    }

    // DeleteAsset deletes an given asset
    // DeleteAsset needs an endorsement of current owner Org Peer
    async DeleteAsset(ctx, assetId) {
        const exists = await this.AssetExists(ctx, assetId);
        if (!exists) {
            throw new Error(`The asset ${assetId} does not exist`);
        }
        // Delete the asset
        await ctx.stub.deleteState(assetId);
    }

    // TransferAsset updates the Owner & OwnerOrg field of asset with given assetId, OwnerOrg must be a valid Org MSP Id
    // TransferAsset needs an endorsement of current owner Org Peer
    // TransferAsset re-sets the endorsement policy of the assetId Key, such that new owner Org Peer is required to endorse future updates
    async TransferAsset(ctx, assetId, newOwner, newOwnerOrg) {
        const assetString = await this.ReadAsset(ctx, assetId);
        const asset = JSON.parse(assetString);
        asset.Owner = newOwner;
        asset.OwnerOrg = newOwnerOrg;
        // Update the asset
        await ctx.stub.putState(assetId, Buffer.from(JSON.stringify(asset)));
        // Re-Set the endorsement policy of the assetId Key, such that a new owner Org Peer is required to endorse future updates
        await AssetContract.setStateBasedEndorsement(ctx, asset.ID, [newOwnerOrg]);

        // Optionally, set the endorsement policy of the assetId Key, such that any 1 Org (N) out of the specified Orgs can endorse future updates
        // await AssetContract.setStateBasedEndorsementNOutOf(ctx, assetId, 1, ["Org1MSP", "Org2MSP"]);
    }

    // AssetExists returns true when asset with given ID exists
    async AssetExists(ctx, assetId){
        const buffer = await ctx.stub.getState(assetId);
        return (!!buffer && buffer.length > 0);
    }

    // getClientOrgId gets the client's OrgId (MSPID)
    static getClientOrgId(ctx){
        return ctx.clientIdentity.getMSPID();
    }

    // setStateBasedEndorsement sets an endorsement policy to the assetId Key
    // setStateBasedEndorsement enforces that the owner Org must endorse future update transactions for the specified assetId Key
    static async setStateBasedEndorsement(ctx, assetId, ownerOrgs){
        const ep = new KeyEndorsementPolicy();
        ep.addOrgs('MEMBER', ...ownerOrgs);
        await ctx.stub.setStateValidationParameter(assetId, ep.getPolicy());
    }

    // setStateBasedEndorsementNOutOf sets an endorsement policy to the assetId Key
    // setStateBasedEndorsementNOutOf enforces that a given number of Orgs (N) out of the specified Orgs must endorse future update transactions for the specified assetId Key.
    async setStateBasedEndorsementNOutOf(ctx, assetId, nOrgs, ownerOrgs) {
        await ctx.stub.setStateValidationParameter(assetId, AssetContract.policy(nOrgs, ownerOrgs));
    }

    // Create a policy that requires a given number (N) of Org principals signatures out of the provided list of Orgs
    static policy(nOrgs, mspIds) {
        const principals = [];
        const sigsPolicies = [];
        mspIds.forEach((mspId, i) => {
            const mspRole = {
                role: fabprotos.common.MSPRole.MSPRoleType.MEMBER,
                mspIdentifier: mspId
            };
            const principal = {
                principalClassification: fabprotos.common.MSPPrincipal.Classification.ROLE,
                principal: fabprotos.common.MSPRole.encode(mspRole).finish()
            };
            principals.push(principal);
            const signedBy = {
                signedBy: i,
            };
            sigsPolicies.push(signedBy);
        });

        // create the policy such that it requires any N signature's from all of the principals provided
        const allOf = {
            n: nOrgs,
            rules: sigsPolicies
        };
        const noutof = {
            nOutOf: allOf
        };
        const spe = {
            version: 0,
            rule: noutof,
            identities: principals
        };
        return fabprotos.common.SignaturePolicyEnvelope.encode(spe).finish();
    }
}

module.exports = AssetContract;
