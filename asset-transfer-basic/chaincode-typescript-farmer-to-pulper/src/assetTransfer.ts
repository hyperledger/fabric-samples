/*
 * SPDX-License-Identifier: Apache-2.0
 */
// Deterministic JSON.stringify()
import {Context, Contract, Info, Returns, Transaction} from 'fabric-contract-api';
import stringify from 'json-stringify-deterministic';
import sortKeysRecursive from 'sort-keys-recursive';
import {Farmer} from './farmer';

@Info({title: 'FarmerTransfer', description: 'Smart contract for trading assets'})
export class AssetTransferContract extends Contract {

    @Transaction()
    public async Init(ctx: Context): Promise<void> {
        const assets: Farmer[] = [
            {
                ID: 'PO/EVC03-VCH07B/0823/001'+'EVC01-VCH07C-VCP01'+'F01-01187'+'03-23/000151',
                PoNumber: 'PO/EVC03-VCH07B/0823/001',
                VcpCode: 'EVC01-VCH07C-VCP01',
                Location: 'Merah Muyang',
                FarmerID: 'F01-01187',
                FarmerName: 'Masmuda',
                PurchaseDate: "22-Aug-2023",
                ReceiptNo: '03-23/000151',
                ItemType: 'Cherry SW',
                ProcessType: 'Semi Washed',
                Qty: "75.00",
                PurchasePrice: "Rp 13,700",
                Floating: "7.76",
                BatchNumber: "1",
                Actor: 'Farmer'
            },
        ];

        for (const asset of assets) {
            // example of how to write to world state deterministically
            // use convetion of alphabetic order
            // we insert data in alphabetic order using 'json-stringify-deterministic' and 'sort-keys-recursive'
            // when retrieving data, in any lang, the order of data will be the same and consequently also the corresonding hash
            await ctx.stub.putState(asset.ID, Buffer.from(stringify(sortKeysRecursive(asset))));
            console.info(`Asset ${asset.FarmerID} initialized`);
        }
    }

    // CreateAsset issues a new asset to the world state with given details.
    @Transaction()
    public async CreateAsset(ctx: Context, farmerId: string, farmerName: string, poNumber: string, 
        itemType: string, qty: string, location: string, purchasePrice: string, purchaseDate: string, 
        processType: string, vcpCode: string, receiptNo: string, floating: string, batchNumber: string): Promise<void> {
        
        const id = poNumber+vcpCode+farmerId+receiptNo;
        const exist = await this.AssetExists(ctx, id);
        if (exist) {
            throw new Error(`The farmer ID ${farmerId} with ${receiptNo}, ${vcpCode}, and ${poNumber} already exists`);
        }

        const asset = {
            ID: id,
            VcpCode: vcpCode,
            ReceiptNo: receiptNo,
            ProcessType: processType,
            Floating: floating,
            BatchNumber: batchNumber,
            FarmerID: farmerId,
            FarmerName: farmerName,
            PoNumber: poNumber,
            ItemType: itemType,
            Qty: qty,
            Location: location,
            PurchasePrice: purchasePrice,
            PurchaseDate: purchaseDate,
            Actor: 'Farmer'
        };
        // we insert data in alphabetic order using 'json-stringify-deterministic' and 'sort-keys-recursive'
        await ctx.stub.putState(id, Buffer.from(stringify(sortKeysRecursive(asset))));
    }

    // ReadAsset returns the asset stored in the world state with given id.
    @Transaction(false)
    public async ReadAsset(ctx: Context, id: string): Promise<string> {
        const assetJSON = await ctx.stub.getState(id); // get the asset from chaincode state
        if (!assetJSON || assetJSON.length === 0) {
            throw new Error(`The farmer id ${id} does not exist`);
        }
        return assetJSON.toString();
    }

    // UpdateAsset updates an existing asset in the world state with provided parameters.
    @Transaction()
    public async UpdateAsset(ctx: Context, farmerId: string, farmerName: string, poNumber: string, 
        itemType: string, qty: string, location: string, purchasePrice: string, purchaseDate: string, 
        processType: string, vcpCode: string, receiptNo: string, floating: string, batchNumber: string): Promise<void> {
        const id = poNumber+vcpCode+farmerId+receiptNo;
        const exist = await this.AssetExists(ctx, id);
        if (!exist) {
            throw new Error(`The farmer ID ${farmerId} with ${receiptNo}, ${vcpCode}, and ${poNumber} does not exist`);
        }

        // overwriting original asset with new asset
        const updatedAsset = {
            VcpCode: vcpCode,
            ReceiptNo: receiptNo,
            ProcessType: processType,
            Floating: floating,
            BatchNumber: batchNumber,
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
        return ctx.stub.putState(id, Buffer.from(stringify(sortKeysRecursive(updatedAsset))));
    }

    // DeleteAsset deletes an given asset from the world state.
    @Transaction()
    public async DeleteAsset(ctx: Context, farmerId: string, receiptNo: string, vcpCode: string, poNumber: string): Promise<void> {
        const id = poNumber+vcpCode+farmerId+receiptNo;
        const exist = await this.AssetExists(ctx, id);
        if (!exist) {
            throw new Error(`The farmer ID ${farmerId} with ${receiptNo}, ${vcpCode}, and ${poNumber} does not exist`);
        }
        return ctx.stub.deleteState(id);
    }

    // AssetExists returns true when asset with given ID exists in world state.
    @Transaction(false)
    @Returns('boolean')
    public async AssetExists(ctx: Context, id: string): Promise<boolean> {
        const assetJSON = await ctx.stub.getState(id);
        return assetJSON && assetJSON.length > 0;
    }

    // TransferAsset updates the owner field of asset with given id in the world state, and returns the old owner.
    @Transaction()
    public async TransferAsset(ctx: Context, farmerId: string, receiptNo: string, vcpCode: string, poNumber: string, newActor: string): Promise<string> {
        const id = poNumber+vcpCode+farmerId+receiptNo;
        const assetString = await this.ReadAsset(ctx, id);
        const asset = JSON.parse(assetString);
        const oldActor = asset.Actor;
        asset.Actor = newActor;
        // we insert data in alphabetic order using 'json-stringify-deterministic' and 'sort-keys-recursive'
        await ctx.stub.putState(id, Buffer.from(stringify(sortKeysRecursive(asset))));
        return oldActor;
    }
/*
    // TransferAsset updates the owner field of asset with given id in the world state, and returns the old owner.
    @Transaction()
    public async TransferAsset(ctx: Context, farmerId: string, newOwner: string): Promise<string> {
        const assetString = await this.ReadAsset(ctx, farmerId);
        const asset = JSON.parse(assetString);
        const oldOwner = asset.Owner;
        asset.Owner = newOwner;
        // we insert data in alphabetic order using 'json-stringify-deterministic' and 'sort-keys-recursive'
        await ctx.stub.putState(farmerId, Buffer.from(stringify(sortKeysRecursive(asset))));
        return oldOwner;
    }
*/
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
