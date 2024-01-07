"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.LandTransferContract = void 0;
const fabric_contract_api_1 = require("fabric-contract-api");
const json_stringify_deterministic_1 = __importDefault(require("json-stringify-deterministic"));
const sort_keys_recursive_1 = __importDefault(require("sort-keys-recursive"));
22224;
let LandTransferContract = class LandTransferContract extends fabric_contract_api_1.Contract {
    async InitLedger(ctx) {
        const assets = [
            {
                ID: "0",
                Size: 10000,
                Owner: "Government",
                TimeStamp: Date.now()
            }
        ];
        for (const asset of assets) {
            await ctx.stub.putState(asset.ID, Buffer.from((0, json_stringify_deterministic_1.default)((0, sort_keys_recursive_1.default)(asset))));
            console.info(`Asset ${asset.ID} initialized`);
        }
    }
    async CreateAsset(ctx, id, size, owner) {
        const exists = await this.AssetExists(ctx, id);
        if (exists) {
            throw new Error(`The asset ${id} already exists`);
        }
        const asset = {
            ID: id,
            Size: size,
            Owner: owner,
            TimeStamp: Date.now()
        };
        await ctx.stub.putState(id, Buffer.from((0, json_stringify_deterministic_1.default)((0, sort_keys_recursive_1.default)(asset))));
    }
    async DeleteAsset(ctx, id) {
        const exists = await this.AssetExists(ctx, id);
        if (!exists) {
            throw new Error(`The asset ${id} does not exist`);
        }
        return ctx.stub.deleteState(id);
    }
    async TransferAsset(ctx, id, newOwner) {
        const assetString = await this.ReadAsset(ctx, id);
        const asset = JSON.parse(assetString);
        const oldOwner = asset.Owner;
        asset.Owner = newOwner;
        // we insert data in alphabetic order using 'json-stringify-deterministic' and 'sort-keys-recursive'
        await ctx.stub.putState(id, Buffer.from((0, json_stringify_deterministic_1.default)((0, sort_keys_recursive_1.default)(asset))));
        return oldOwner;
    }
    async SplitAsset(ctx, id, newOwner, transfer) {
        const transferSize = parseInt(transfer);
        const assetString = await this.ReadAsset(ctx, id);
        const asset = JSON.parse(assetString);
        if (asset.Size < transferSize) {
            throw new Error(`Cannot transfer more than the user owns`);
        }
        const oldOwner = asset.Owner;
        try {
            await this.DeleteAsset(ctx, id);
        }
        catch (error) {
            throw error;
        }
        if (asset.Size > transferSize)
            await this.CreateAsset(ctx, id + "0", asset.Size - transferSize, oldOwner);
        await this.CreateAsset(ctx, id + "1", transferSize, newOwner);
        // we insert data in alphabetic order using 'json-stringify-deterministic' and 'sort-keys-recursive'
        // await ctx.stub.putState(id, Buffer.from(stringify(sortKeysRecursive(asset))));
        return oldOwner;
    }
    async ReadAsset(ctx, id) {
        const assetJSON = await ctx.stub.getState(id); // get the asset from chaincode state
        if (!assetJSON || assetJSON.length === 0) {
            throw new Error(`The asset ${id} does not exist`);
        }
        return assetJSON.toString();
    }
    async AssetExists(ctx, id) {
        const assetJSON = await ctx.stub.getState(id);
        return assetJSON && assetJSON.length > 0;
    }
    async GetAllAssets(ctx) {
        const allResults = [];
        // range query with empty string for startKey and endKey does an open-ended query of all assets in the chaincode namespace.
        const iterator = await ctx.stub.getStateByRange('', '');
        let result = await iterator.next();
        while (!result.done) {
            const strValue = Buffer.from(result.value.value.toString()).toString('utf8');
            let record;
            try {
                record = JSON.parse(strValue);
            }
            catch (err) {
                console.log(err);
                record = strValue;
            }
            allResults.push(record);
            result = await iterator.next();
        }
        return JSON.stringify(allResults);
    }
};
__decorate([
    (0, fabric_contract_api_1.Transaction)(),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [fabric_contract_api_1.Context]),
    __metadata("design:returntype", Promise)
], LandTransferContract.prototype, "InitLedger", null);
__decorate([
    (0, fabric_contract_api_1.Transaction)(),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [fabric_contract_api_1.Context, String, Number, String]),
    __metadata("design:returntype", Promise)
], LandTransferContract.prototype, "CreateAsset", null);
__decorate([
    (0, fabric_contract_api_1.Transaction)(),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [fabric_contract_api_1.Context, String]),
    __metadata("design:returntype", Promise)
], LandTransferContract.prototype, "DeleteAsset", null);
__decorate([
    (0, fabric_contract_api_1.Transaction)(),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [fabric_contract_api_1.Context, String, String]),
    __metadata("design:returntype", Promise)
], LandTransferContract.prototype, "TransferAsset", null);
__decorate([
    (0, fabric_contract_api_1.Transaction)(),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [fabric_contract_api_1.Context, String, String, String]),
    __metadata("design:returntype", Promise)
], LandTransferContract.prototype, "SplitAsset", null);
__decorate([
    (0, fabric_contract_api_1.Transaction)(false),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [fabric_contract_api_1.Context, String]),
    __metadata("design:returntype", Promise)
], LandTransferContract.prototype, "ReadAsset", null);
__decorate([
    (0, fabric_contract_api_1.Transaction)(false),
    (0, fabric_contract_api_1.Returns)('boolean'),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [fabric_contract_api_1.Context, String]),
    __metadata("design:returntype", Promise)
], LandTransferContract.prototype, "AssetExists", null);
__decorate([
    (0, fabric_contract_api_1.Transaction)(false),
    (0, fabric_contract_api_1.Returns)('string'),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [fabric_contract_api_1.Context]),
    __metadata("design:returntype", Promise)
], LandTransferContract.prototype, "GetAllAssets", null);
LandTransferContract = __decorate([
    (0, fabric_contract_api_1.Info)({ title: 'LandTransfer', description: 'Smart contract for trading land assets' })
], LandTransferContract);
exports.LandTransferContract = LandTransferContract;
//# sourceMappingURL=landTransaction.js.map