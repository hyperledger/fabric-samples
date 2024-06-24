/*
 * SPDX-License-Identifier: Apache-2.0
 */
// Deterministic JSON.stringify()
import {Context, Contract, Info, Returns, Transaction} from 'fabric-contract-api';
import stringify from 'json-stringify-deterministic';
import sortKeysRecursive from 'sort-keys-recursive';
import {Farmer} from './farmer';

@Info({title: 'AssetTransfer', description: 'Smart contract for trading assets'})
export class AssetTransferContract extends Contract {

    @Transaction()
    public async InitLedger(ctx: Context): Promise<void> {
        const assets: Farmer[] = [
            {
                FarmerID: 'asset1',
                FarmerName: 'farmer 1',
                PoNumber: 'PO0001',
                ItemType: 'coffee',
                Qty: 3,
                Location: 'Location 1',
                PurchasePrice: 100000,
                PurchaseDate: "2024-05-24 10:00:00"
            },{
                FarmerID: 'asset2',
                FarmerName: 'farmer 2',
                PoNumber: 'PO0002',
                ItemType: 'arabika coffee',
                Qty: 3,
                Location: 'Location 2',
                PurchasePrice: 100000,
                PurchaseDate: "2024-05-24 10:00:00"
            },
        ];

        for (const asset of assets) {
            // example of how to write to world state deterministically
            // use convetion of alphabetic order
            // we insert data in alphabetic order using 'json-stringify-deterministic' and 'sort-keys-recursive'
            // when retrieving data, in any lang, the order of data will be the same and consequently also the corresonding hash
            await ctx.stub.putState(asset.FarmerID, Buffer.from(stringify(sortKeysRecursive(asset))));
            console.info(`Asset ${asset.FarmerID} initialized`);
        }
    }

    // CreateAsset issues a new asset to the world state with given details.
    @Transaction()
    public async CreateAsset(ctx: Context, farmerId: string, farmerName: string, poNumber: string, itemType: string, qty: number, location: string, purchasePrice: number, purchaseDate: string): Promise<void> {
        const exists = await this.AssetExists(ctx, farmerId);
        if (exists) {
            throw new Error(`The asset ${farmerId} already exists`);
        }

        const asset = {
            FarmerID: farmerId,
            FarmerName: farmerName,
            PoNumber: poNumber,
            ItemType: itemType,
            Qty: qty,
            Location: location,
            PurchasePrice: purchasePrice,
            PurchaseDate: purchaseDate,
        };
        // we insert data in alphabetic order using 'json-stringify-deterministic' and 'sort-keys-recursive'
        await ctx.stub.putState(farmerId, Buffer.from(stringify(sortKeysRecursive(asset))));
    }

    // ReadAsset returns the asset stored in the world state with given id.
    @Transaction(false)
    public async ReadAsset(ctx: Context, farmerId: string): Promise<string> {
        const assetJSON = await ctx.stub.getState(farmerId); // get the asset from chaincode state
        if (!assetJSON || assetJSON.length === 0) {
            throw new Error(`The asset ${farmerId} does not exist`);
        }
        return assetJSON.toString();
    }

    // UpdateAsset updates an existing asset in the world state with provided parameters.
    @Transaction()
    public async UpdateAsset(ctx: Context, farmerId: string, farmerName: string, poNumber: string, itemType: string, qty: number, location: string, purchasePrice: number, purchaseDate: Date): Promise<void> {
        const exists = await this.AssetExists(ctx, farmerId);
        if (!exists) {
            throw new Error(`The asset ${farmerId} does not exist`);
        }

        // overwriting original asset with new asset
        const updatedAsset = {
            FarmerID: farmerId,
            FarmerName: farmerName,
            PoNumber: poNumber,
            ItemType: itemType,
            Qty: qty,
            Location: location,
            PurchasePrice: purchasePrice,
            PurchaseDate: purchaseDate,
        };
        // we insert data in alphabetic order using 'json-stringify-deterministic' and 'sort-keys-recursive'
        return ctx.stub.putState(farmerId, Buffer.from(stringify(sortKeysRecursive(updatedAsset))));
    }

    // DeleteAsset deletes an given asset from the world state.
    @Transaction()
    public async DeleteAsset(ctx: Context, farmerId: string): Promise<void> {
        const exists = await this.AssetExists(ctx, farmerId);
        if (!exists) {
            throw new Error(`The asset ${farmerId} does not exist`);
        }
        return ctx.stub.deleteState(farmerId);
    }

    // AssetExists returns true when asset with given ID exists in world state.
    @Transaction(false)
    @Returns('boolean')
    public async AssetExists(ctx: Context, farmerId: string): Promise<boolean> {
        const assetJSON = await ctx.stub.getState(farmerId);
        return assetJSON && assetJSON.length > 0;
    }

    // TransferAsset updates the owner field of asset with given id in the world state, and returns the old owner.
    @Transaction()
    public async TransferAsset(ctx: Context, farmerId: string, purchasePrice: number): Promise<string> {
        const assetString = await this.ReadAsset(ctx, farmerId);
        const asset = JSON.parse(assetString);
        const oldOwner = asset.PurchasePrice;
        asset.PurchasePrice = purchasePrice;
        // we insert data in alphabetic order using 'json-stringify-deterministic' and 'sort-keys-recursive'
        await ctx.stub.putState(farmerId, Buffer.from(stringify(sortKeysRecursive(asset))));
        return oldOwner;
    }

    // GetAllAssets returns all assets found in the world state.
    @Transaction(false)
    @Returns('string')
    public async GetAllAssets(ctx: Context): Promise<string> {
        const allResults = [];
        // range query with empty string for startKey and endKey does an open-ended query of all assets in the chaincode namespace.
        const iterator = await ctx.stub.getStateByRange('', '');
        let result = await iterator.next();
        while (!result.done) {
            const strValue = Buffer.from(result.value.value.toString()).toString('utf8');
            let record;
            try {
                record = JSON.parse(strValue);
            } catch (err) {
                console.log(err);
                record = strValue;
            }
            allResults.push(record);
            result = await iterator.next();
        }
        return JSON.stringify(allResults);
    }

}
