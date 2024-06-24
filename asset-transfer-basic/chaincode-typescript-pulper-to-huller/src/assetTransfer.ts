/*
 * SPDX-License-Identifier: Apache-2.0
 */
// Deterministic JSON.stringify()
import {Context, Contract, Info, Returns, Transaction} from 'fabric-contract-api';
import stringify from 'json-stringify-deterministic';
import sortKeysRecursive from 'sort-keys-recursive';
import {Pulper} from './pulper';

@Info({title: 'AssetTransfer', description: 'Smart contract for trading assets'})
export class AssetTransferContract extends Contract {

    @Transaction()
    public async Init(ctx: Context): Promise<void> {
        const assets: Pulper[] = [
            {
                ID: 'PO/EVC03-VCH07B/0823/001'+'EVC01-VCH07C-VCP01'+'1',
                PoNumber: 'PO/EVC03-VCH07B/0823/001',
                VcpCode: 'EVC01-VCH07C-VCP01',
                BatchNumber: '1',
                VcpFinishProcessDate: '23-Aug-2023',
                VcpItemQty: '155.69',
                VchCode: 'EVC01-VCH07C',
                VchDriedParchmentDate: "24-Aug-2023",
                VchDriedParchmentQty: "125.00",
                VchDeliveryDate: "28-Aug-2023",
                VchItemQty: "75.29",
                VchDeliveryNumber: "ACEHVCH07C/0823/001",
                Actor: 'Pulper',
            },
        ];

        for (const asset of assets) {
            // example of how to write to world state deterministically
            // use convetion of alphabetic order
            // we insert data in alphabetic order using 'json-stringify-deterministic' and 'sort-keys-recursive'
            // when retrieving data, in any lang, the order of data will be the same and consequently also the corresonding hash
            await ctx.stub.putState(asset.ID, Buffer.from(stringify(sortKeysRecursive(asset))));
            console.info(`PO Number ${asset.PoNumber} initialized`);
        }
    }

    // CreateAsset issues a new asset to the world state with given details.
    @Transaction()
    public async CreateAsset(ctx: Context, poNumber: string, vcpCode: string, batchNumber: string, 
        vcpFinishProcessDate: string, vcpItemQty: string, vchCode: string, vchDriedParchmentDate: string, 
        vchDriedParchmentQty: string, vchDeliveryDate: string, vchItemQty: string, vchDeliveryNumber: string
    ): Promise<void> {
        
        const id = poNumber+vcpCode+batchNumber;
        const exists = await this.AssetExists(ctx, id);
        if (exists) {
            throw new Error(`PO Number ${poNumber} with ${vcpCode} and batch ${batchNumber} already exists`);
        }
        
        const asset = {
            ID: id,
            PoNumber: poNumber,
            VcpCode: vcpCode,
            BatchNumber: batchNumber,
            VcpFinishProcessDate: vcpFinishProcessDate,
            VcpItemQty: vcpItemQty,
            VchCode: vchCode,
            VchDriedParchmentDate: vchDriedParchmentDate,
            VchDriedParchmentQty: vchDriedParchmentQty,
            VchDeliveryDate: vchDeliveryDate,
            VchItemQty: vchItemQty,
            VchDeliveryNumber: vchDeliveryNumber,
            Actor: 'Pulper',
        };
        // we insert data in alphabetic order using 'json-stringify-deterministic' and 'sort-keys-recursive'
        await ctx.stub.putState(id, Buffer.from(stringify(sortKeysRecursive(asset))));
    }

    // ReadAsset returns the asset stored in the world state with given id.
    @Transaction(false)
    public async ReadAsset(ctx: Context, id: string): Promise<string> {
        const assetJSON = await ctx.stub.getState(id); // get the asset from chaincode state
        if (!assetJSON || assetJSON.length === 0) {
            throw new Error(`ID ${id} does not exist`);
        }
        return assetJSON.toString();
    }

    // UpdateAsset updates an existing asset in the world state with provided parameters.
    @Transaction()
    public async UpdateAsset(ctx: Context, poNumber: string, vcpCode: string, batchNumber: string, 
        vcpFinishProcessDate: string, vcpItemQty: string, vchCode: string, vchDriedParchmentDate: string, 
        vchDriedParchmentQty: string, vchDeliveryDate: string, vchItemQty: string, vchDeliveryNumber: string
    ): Promise<void> {
        const id = poNumber+vcpCode+batchNumber;
        const exists = await this.AssetExists(ctx, id);
        if (!exists) {
            throw new Error(`PO Number ${poNumber} with ${vcpCode} and batch ${batchNumber} doesn't exists`);
        }

        // overwriting original asset with new asset
        const updatedAsset = {
            PoNumber: poNumber,
            VcpCode: vcpCode,
            BatchNumber: batchNumber,
            VcpFinishProcessDate: vcpFinishProcessDate,
            VcpItemQty: vcpItemQty,
            VchCode: vchCode,
            VchDriedParchmentDate: vchDriedParchmentDate,
            VchDriedParchmentQty: vchDriedParchmentQty,
            VchDeliveryDate: vchDeliveryDate,
            VchItemQty: vchItemQty,
            VchDeliveryNumber: vchDeliveryNumber,
        };
        // we insert data in alphabetic order using 'json-stringify-deterministic' and 'sort-keys-recursive'
        return ctx.stub.putState(id, Buffer.from(stringify(sortKeysRecursive(updatedAsset))));
    }

    // DeleteAsset deletes an given asset from the world state.
    @Transaction()
    public async DeleteAsset(ctx: Context, poNumber: string, vcpCode: string, batchNumber: string): Promise<void> {
        const id = poNumber+vcpCode+batchNumber;
        const exists = await this.AssetExists(ctx, id);
        if (!exists) {
            throw new Error(`PO Number ${poNumber} with ${vcpCode} and batch ${batchNumber} doesn't exists`);
        }
        return ctx.stub.deleteState(id);
    }

    // AssetExists returns true when asset with given ID exists in world state.
    @Transaction(false)
    public async AssetExists(ctx: Context, id: string): Promise<boolean> {
        const assetJSON = await ctx.stub.getState(id);
        return assetJSON && assetJSON.length > 0;
    }

    // TransferAsset updates the owner field of asset with given id in the world state, and returns the old owner.
    @Transaction()
    public async TransferAsset(ctx: Context,  poNumber: string, vcpCode: string, batchNumber: string, newActor: string): Promise<string> {
        const id = poNumber+vcpCode+batchNumber;
        const assetString = await this.ReadAsset(ctx, id);
        const asset = JSON.parse(assetString);
        const oldActor = asset.Actor;
        asset.Actor = newActor;
        // we insert data in alphabetic order using 'json-stringify-deterministic' and 'sort-keys-recursive'
        await ctx.stub.putState(id, Buffer.from(stringify(sortKeysRecursive(asset))));
        return oldActor;
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
