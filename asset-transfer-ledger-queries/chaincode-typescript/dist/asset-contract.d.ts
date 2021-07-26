import { Context, Contract } from 'fabric-contract-api';
import { Iterators } from 'fabric-shim-api';
export declare class AssetContract extends Contract {
    CreateAsset(ctx: Context, assetID: string, color: string, size: number, owner: string, appraisedValue: number): Promise<void>;
    ReadAsset(ctx: Context, id: string): Promise<string>;
    DeleteAsset(ctx: Context, id: string): Promise<void>;
    TransferAsset(ctx: Context, assetName: string, newOwner: string): Promise<void>;
    GetAssetsByRange(ctx: Context, startKey: string, endKey: string): Promise<string>;
    TransferAssetByColor(ctx: Context, color: string, newOwner: string): Promise<void>;
    QueryAssetsByOwner(ctx: Context, owner: string): Promise<string>;
    QueryAssets(ctx: Context, queryString: string): Promise<string>;
    GetQueryResultForQueryString(ctx: Context, queryString: string): Promise<string>;
    GetAssetsByRangeWithPagination(ctx: Context, startKey: string, endKey: string, pageSize: number, bookmark: string): Promise<string>;
    QueryAssetsWithPagination(ctx: Context, queryString: string, pageSize: number, bookmark: string): Promise<string>;
    GetAssetHistory(ctx: Context, assetName: string): Promise<string>;
    AssetExists(ctx: Context, assetName: string): Promise<boolean>;
    GetAllResults(iterator: Iterators.CommonIterator<any>, isHistory: boolean): Promise<any[]>;
    InitLedger(ctx: Context): Promise<void>;
}
