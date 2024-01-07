import { Context, Contract } from 'fabric-contract-api';
export declare class LandTransferContract extends Contract {
    InitLedger(ctx: Context): Promise<void>;
    private CreateAsset;
    private DeleteAsset;
    TransferAsset(ctx: Context, id: string, newOwner: string): Promise<string>;
    SplitAsset(ctx: Context, id: string, newOwner: string, transfer: string): Promise<string>;
    ReadAsset(ctx: Context, id: string): Promise<string>;
    AssetExists(ctx: Context, id: string): Promise<boolean>;
    GetAllAssets(ctx: Context): Promise<string>;
}
