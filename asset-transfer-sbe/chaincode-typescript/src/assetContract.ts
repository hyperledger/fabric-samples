/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { Context, Contract, Info, Transaction } from 'fabric-contract-api';
import { Asset } from './asset';
import { KeyEndorsementPolicy } from 'fabric-shim';

@Info({title: 'AssetContract', description: 'Asset Transfer Smart Contract, using State Based Endorsement(SBE), implemented in TypeScript' })
export class AssetContract extends Contract {

    // CreateAsset creates a new asset
    // CreateAsset sets the endorsement policy of the assetId Key, such that current owner Org Peer is required to endorse future updates
    @Transaction()
    public async CreateAsset(ctx: Context, assetId: string, value: number, owner: string): Promise<void> {
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
    @Transaction(false)
    public async ReadAsset(ctx: Context, assetId: string): Promise<string> {
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
    @Transaction()
    public async UpdateAsset(ctx: Context, assetId: string, newValue: number): Promise<void> {
        const assetString = await this.ReadAsset(ctx, assetId);
        const asset = JSON.parse(assetString) as Asset;
        asset.Value = newValue;
        const buffer = Buffer.from(JSON.stringify(asset));
        // Update the asset
        await ctx.stub.putState(assetId, buffer);
    }

    // DeleteAsset deletes an given asset
    // DeleteAsset needs an endorsement of current owner Org Peer
    @Transaction()
    public async DeleteAsset(ctx: Context, assetId: string): Promise<void> {
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
    @Transaction()
    public async TransferAsset(ctx: Context, assetId: string, newOwner: string, newOwnerOrg: string): Promise<void> {
        const assetString = await this.ReadAsset(ctx, assetId);
        const asset = JSON.parse(assetString) as Asset;
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
    public async AssetExists(ctx: Context, assetId: string): Promise<boolean> {
        const buffer = await ctx.stub.getState(assetId);
        return (!!buffer && buffer.length > 0);
    }

    // getClientOrgId gets the client's OrgId (MSPID)
    private static getClientOrgId(ctx: Context): string {
        return ctx.clientIdentity.getMSPID();
    }

    // setStateBasedEndorsement sets an endorsement policy to the assetId Key
    // setStateBasedEndorsement enforces that the owner Org must endorse future update transactions for the specified assetId Key
    private static async setStateBasedEndorsement(ctx: Context, assetId: string, ownerOrgs: string[]): Promise<void> {
        const ep = new KeyEndorsementPolicy();
        ep.addOrgs('MEMBER', ...ownerOrgs);
        await ctx.stub.setStateValidationParameter(assetId, ep.getPolicy());
    }

    // setStateBasedEndorsementNOutOf sets an endorsement policy to the assetId Key
    // setStateBasedEndorsementNOutOf enforces that a given number of Orgs (N) out of the specified Orgs must endorse future update transactions for the specified assetId Key.
    private static async setStateBasedEndorsementNOutOf(ctx: Context, assetId: string, nOrgs:number, ownerOrgs: string[]): Promise<void> {
        const ROLE_TYPE_MEMBER = 'MEMBER';

        // Use the KeyEndorsementPolicy helper form the chaincode libarries
        // If you need more advanced policies, please use that helper as a reference point.
        const keyEndorsementPolicy = new KeyEndorsementPolicy();
        keyEndorsementPolicy.addOrgs(ROLE_TYPE_MEMBER,...ownerOrgs);

        await ctx.stub.setStateValidationParameter(assetId, keyEndorsementPolicy.getPolicy());
    }

}
