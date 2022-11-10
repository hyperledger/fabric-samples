/*
 * SPDX-License-Identifier: Apache-2.0
 */

import { X509Certificate } from 'crypto';
import { Context, Contract, Info, Param, Returns, Transaction } from 'fabric-contract-api';
import { KeyEndorsementPolicy } from 'fabric-shim';
import stringify from 'json-stringify-deterministic'; // Deterministic JSON.stringify()
import sortKeysRecursive from 'sort-keys-recursive';
import { TextDecoder } from 'util';
import { Asset } from './asset';

const utf8Decoder = new TextDecoder();

@Info({title: 'AssetTransfer', description: 'Smart contract for trading assets'})
export class AssetTransferContract extends Contract {
    /**
     * CreateAsset issues a new asset to the world state with given details.
     */
    @Transaction()
    @Param('assetObj', 'Asset', 'Part formed JSON of Asset')
    async CreateAsset(ctx: Context, state: Asset): Promise<void> {
        state.Owner = toJSON(clientIdentifier(ctx, state.Owner));
        const asset = Asset.newInstance(state);

        const exists = await this.AssetExists(ctx, asset.ID);
        if (exists) {
            throw new Error(`The asset ${asset.ID} already exists`);
        }

        const assetBytes = marshal(asset);
        await ctx.stub.putState(asset.ID, assetBytes);

        await setEndorsingOrgs(ctx, asset.ID, ctx.clientIdentity.getMSPID());

        ctx.stub.setEvent('CreateAsset', assetBytes);
    }

    /**
     * ReadAsset returns an existing asset stored in the world state.
     */
    @Transaction(false)
    @Returns('Asset')
    async ReadAsset(ctx: Context, id: string): Promise<Asset> {
        const existingAssetBytes = await this.#readAsset(ctx, id);
        const existingAsset = Asset.newInstance(unmarshal(existingAssetBytes));

        return existingAsset;
    }

    async #readAsset(ctx: Context, id: string): Promise<Uint8Array> {
        const assetBytes = await ctx.stub.getState(id); // get the asset from chaincode state
        if (!assetBytes || assetBytes.length === 0) {
            throw new Error(`Sorry, asset ${id} has not been created`);
        }

        return assetBytes;
    }

    /**
     * UpdateAsset updates an existing asset in the world state with provided partial asset data, which must include
     * the asset ID.
     */
    @Transaction()
    @Param('assetObj', 'Asset', 'Part formed JSON of Asset')
    async UpdateAsset(ctx: Context, assetUpdate: Asset): Promise<void> {
        if (assetUpdate.ID === undefined) {
            throw new Error('No asset ID specified');
        }

        const existingAssetBytes = await this.#readAsset(ctx, assetUpdate.ID);
        const existingAsset = Asset.newInstance(unmarshal(existingAssetBytes));

        if (!hasWritePermission(ctx, existingAsset)) {
            throw new Error('Only owner can update assets');
        }

        const updatedState = Object.assign({}, existingAsset, assetUpdate, {
            Owner: existingAsset.Owner, // Must transfer to change owner
        });
        const updatedAsset = Asset.newInstance(updatedState);

        // overwriting original asset with new asset
        const updatedAssetBytes = marshal(updatedAsset);
        await ctx.stub.putState(updatedAsset.ID, updatedAssetBytes);

        await setEndorsingOrgs(ctx, updatedAsset.ID, ctx.clientIdentity.getMSPID());

        ctx.stub.setEvent('UpdateAsset', updatedAssetBytes);
    }

    /**
     * DeleteAsset deletes an asset from the world state.
     */
    @Transaction()
    async DeleteAsset(ctx: Context, id: string): Promise<void> {
        const assetBytes = await this.#readAsset(ctx, id); // Throws if asset does not exist
        const asset = Asset.newInstance(unmarshal(assetBytes));

        if (!hasWritePermission(ctx, asset)) {
            throw new Error('Only owner can delete assets');
        }

        await ctx.stub.deleteState(id);

        ctx.stub.setEvent('DeletaAsset', assetBytes);
    }

    /**
     * AssetExists returns true when asset with the specified ID exists in world state; otherwise false.
     */
    @Transaction(false)
    @Returns('boolean')
    async AssetExists(ctx: Context, id: string): Promise<boolean> {
        const assetJson = await ctx.stub.getState(id);
        return assetJson?.length > 0;
    }

    /**
     * TransferAsset updates the owner field of asset with the specified ID in the world state.
     */
    @Transaction()
    async TransferAsset(ctx: Context, id: string, newOwner: string, newOwnerOrg: string): Promise<void> {
        const assetString = await this.#readAsset(ctx, id);
        const asset = Asset.newInstance(unmarshal(assetString));

        if (!hasWritePermission(ctx, asset)) {
            throw new Error('Only owner can transfer assets');
        }

        asset.Owner = toJSON(ownerIdentifier(newOwner, newOwnerOrg));

        const assetBytes = marshal(asset);
        await ctx.stub.putState(id, assetBytes);

        await setEndorsingOrgs(ctx, id, newOwnerOrg); // Subsequent updates must be endorsed by the new owning org

        ctx.stub.setEvent('TransferAsset', assetBytes);
    }

    /**
     * GetAllAssets returns a list of all assets found in the world state.
     */
    @Transaction(false)
    @Returns('string')
    async GetAllAssets(ctx: Context): Promise<string> {
        // range query with empty string for startKey and endKey does an open-ended query of all assets in the chaincode namespace.
        const iterator = await ctx.stub.getStateByRange('', '');

        const assets: Asset[] = [];
        for (let result = await iterator.next(); !result.done; result = await iterator.next()) {
            const assetBytes = result.value.value;
            try {
                const asset = Asset.newInstance(unmarshal(assetBytes));
                assets.push(asset);
            } catch (err) {
                console.log(err);
            }
        }

        return marshal(assets).toString();
    }
}

function unmarshal(bytes: Uint8Array | string): object {
    const json = typeof bytes === 'string' ? bytes : utf8Decoder.decode(bytes);
    const parsed: unknown = JSON.parse(json);
    if (parsed === null || typeof parsed !== 'object') {
        throw new Error(`Invalid JSON type (${typeof parsed}): ${json}`);
    }

    return parsed;
}

function marshal(o: object): Buffer {
    return Buffer.from(toJSON(o));
}

function toJSON(o: object): string {
    // Insert data in alphabetic order using 'json-stringify-deterministic' and 'sort-keys-recursive'
    return stringify(sortKeysRecursive(o));
}

interface OwnerIdentifier {
    org: string;
    user: string;
}

function hasWritePermission(ctx: Context, asset: Asset): boolean {
    const clientId = clientIdentifier(ctx);
    const ownerId = unmarshal(asset.Owner) as OwnerIdentifier;
    return clientId.org === ownerId.org;
}

function clientIdentifier(ctx: Context, user?: string): OwnerIdentifier {
    return {
        org: ctx.clientIdentity.getMSPID(),
        user: user ?? clientCommonName(ctx),
    };
}

function clientCommonName(ctx: Context): string {
    const clientCert = new X509Certificate(ctx.clientIdentity.getIDBytes());
    const matches = clientCert.subject.match(/^CN=(.*)$/m); // [0] Matching string; [1] capture group
    if (matches?.length !== 2) {
        throw new Error(`Unable to identify client identity common name: ${clientCert.subject}`);
    }

    return matches[1];
}

function ownerIdentifier(user: string, org: string): OwnerIdentifier {
    return { org, user };
}

async function setEndorsingOrgs(ctx: Context, ledgerKey: string, ...orgs: string[]): Promise<void> {
    const policy = newMemberPolicy(...orgs);
    await ctx.stub.setStateValidationParameter(ledgerKey, policy.getPolicy());
}

function newMemberPolicy(...orgs: string[]): KeyEndorsementPolicy {
    const policy = new KeyEndorsementPolicy();
    policy.addOrgs('MEMBER', ...orgs);
    return policy;
}
