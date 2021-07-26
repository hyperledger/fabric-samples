import { Context, Contract } from 'fabric-contract-api';
export declare class AssetTransferEvents extends Contract {
    CreateAsset(ctx: Context, id: string, color: string, size: number, owner: string, appraisedValue: number): Promise<void>;
    TransferAsset(ctx: any, id: any, newOwner: any): Promise<any>;
    ReadAsset(ctx: Context, id: string): Promise<String>;
    UpdateAsset(ctx: Context, id: string, color: string, size: number, owner: string, appraisedValue: number): Promise<void>;
    DeleteAsset(ctx: Context, assetId: string): Promise<void>;
}
