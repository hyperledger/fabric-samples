import {Context, Contract, Returns, Transaction, Info} from 'fabric-contract-api';
import stringify from 'json-stringify-deterministic';
import sortKeysRecursive from 'sort-keys-recursive';
import { Asset } from './landAsset';

@Info({title: 'LandTransfer', description: 'Smart contract for trading land assets'})
export class LandTransferContract extends Contract {

    @Transaction()
    public async InitLedger(ctx: Context): Promise<void> {
        const assets: Asset[] = [
            {
                ID: "0",
                Size: 10000,
                Owner: "Government",
                TimeStamp: Date.now()
            }
        ];

        for (const asset of assets) {
            await ctx.stub.putState(asset.ID, Buffer.from(stringify(sortKeysRecursive(asset))));
            console.info(`Asset ${asset.ID} initialized`);
        }
    }

    @Transaction()
    private async CreateAsset(ctx: Context, id:string, size: number, owner: string): Promise<void> {
        const exists = await this.AssetExists(ctx, id);
        if (exists) {
            throw new Error(`The asset ${id} already exists`);
        }

        const asset:Asset = {
            ID: id,
            Size: size,
            Owner: owner,
            TimeStamp: Date.now()
        };
        await ctx.stub.putState(id, Buffer.from(stringify(sortKeysRecursive(asset))));
    }

    @Transaction()
    private async DeleteAsset(ctx: Context, id: string): Promise<void> {
        const exists = await this.AssetExists(ctx, id);
        if (!exists) {
            throw new Error(`The asset ${id} does not exist`);
        }
        return ctx.stub.deleteState(id);
    }

    @Transaction()
    public async TransferAsset(ctx: Context, id: string, newOwner: string): Promise<string> {
        const assetString = await this.ReadAsset(ctx, id);
        const asset = JSON.parse(assetString);
        const oldOwner = asset.Owner;
        asset.Owner = newOwner;
        // we insert data in alphabetic order using 'json-stringify-deterministic' and 'sort-keys-recursive'
        await ctx.stub.putState(id, Buffer.from(stringify(sortKeysRecursive(asset))));
        return oldOwner;
    }

    @Transaction()
    public async SplitAsset(ctx: Context, id: string, newOwner: string, transfer:string): Promise<string> {
        const transferSize = parseInt(transfer);
        const assetString = await this.ReadAsset(ctx, id);
        const asset:Asset = JSON.parse(assetString);
        if(asset.Size<transferSize) {
            throw new Error(`Cannot transfer more than the user owns`);
        }
        const oldOwner = asset.Owner;
        try {
            await this.DeleteAsset(ctx, id);
        } catch (error) {
            throw error
        }
        if(asset.Size>transferSize) await this.CreateAsset(ctx, id+"0", asset.Size-transferSize, oldOwner);
        await this.CreateAsset(ctx, id+"1", transferSize, newOwner);
        // we insert data in alphabetic order using 'json-stringify-deterministic' and 'sort-keys-recursive'
        // await ctx.stub.putState(id, Buffer.from(stringify(sortKeysRecursive(asset))));
        return oldOwner;
    }

    @Transaction(false)
    public async ReadAsset(ctx: Context, id: string): Promise<string> {
        const assetJSON = await ctx.stub.getState(id); // get the asset from chaincode state
        if (!assetJSON || assetJSON.length === 0) {
            throw new Error(`The asset ${id} does not exist`);
        }
        return assetJSON.toString();
    }


    @Transaction(false)
    @Returns('boolean')
    public async AssetExists(ctx: Context, id: string): Promise<boolean> {
        const assetJSON = await ctx.stub.getState(id);
        return assetJSON && assetJSON.length > 0;
    }

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