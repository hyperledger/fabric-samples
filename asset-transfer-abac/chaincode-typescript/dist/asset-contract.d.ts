import { Context, Contract } from 'fabric-contract-api';
export declare class AssetTransferContract extends Contract {
    CreateAsset(ctx: Context, id: string, color: string, size: number, appraisedValue: number): Promise<void>;
    ReadAsset(ctx: Context, id: string): Promise<string>;
    UpdateAsset(ctx: Context, id: string, color: string, size: number, appraisedValue: number): Promise<void>;
    DeleteAsset(ctx: Context, id: string): Promise<void>;
    AssetExists(ctx: Context, id: string): Promise<boolean>;
    TransferAsset(ctx: Context, id: string, newOwner: string): Promise<void>;
    GetAllAssets(ctx: Context): Promise<string>;
    GetSubmittingClientIdentity(ctx: Context): Promise<string>;
}
