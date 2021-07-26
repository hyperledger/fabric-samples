"use strict";
/*
 * SPDX-License-Identifier: Apache-2.0
 */
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.AssetTransferEvents = void 0;
const fabric_contract_api_1 = require("fabric-contract-api");
async function savePrivateData(ctx, assetKey) {
    const clientOrg = ctx.clientIdentity.getMSPID();
    const peerOrg = ctx.stub.getMspID();
    const collection = '_implicit_org_' + peerOrg;
    if (clientOrg === peerOrg) {
        const transientMap = ctx.stub.getTransient();
        if (transientMap) {
            const properties = transientMap.get('asset_properties');
            if (properties) {
                await ctx.stub.putPrivateData(collection, assetKey, properties);
            }
        }
    }
}
async function removePrivateData(ctx, assetKey) {
    const clientOrg = ctx.clientIdentity.getMSPID();
    const peerOrg = ctx.stub.getMspID();
    const collection = '_implicit_org_' + peerOrg;
    if (clientOrg === peerOrg) {
        const propertiesBuffer = await ctx.stub.getPrivateData(collection, assetKey);
        if (propertiesBuffer && propertiesBuffer.length > 0) {
            await ctx.stub.deletePrivateData(collection, assetKey);
        }
    }
}
async function addPrivateData(ctx, assetKey, asset) {
    const clientOrg = ctx.clientIdentity.getMSPID();
    const peerOrg = ctx.stub.getMspID();
    const collection = '_implicit_org_' + peerOrg;
    if (clientOrg === peerOrg) {
        const propertiesBuffer = await ctx.stub.getPrivateData(collection, assetKey);
        if (propertiesBuffer && propertiesBuffer.length > 0) {
            const properties = JSON.parse(propertiesBuffer.toString());
            asset.asset_properties = properties;
        }
    }
}
async function readState(ctx, id) {
    const assetBuffer = await ctx.stub.getState(id); // get the asset from chaincode state
    if (!assetBuffer || assetBuffer.length === 0) {
        throw new Error(`The asset ${id} does not exist`);
    }
    const assetString = assetBuffer.toString();
    const asset = JSON.parse(assetString);
    return asset;
}
let AssetTransferEvents = class AssetTransferEvents extends fabric_contract_api_1.Contract {
    // CreateAsset issues a new asset to the world state with given details.
    async CreateAsset(ctx, id, color, size, owner, appraisedValue) {
        const asset = {
            ID: id,
            Color: color,
            Size: size,
            Owner: owner,
            AppraisedValue: appraisedValue,
        };
        await savePrivateData(ctx, id);
        const assetBuffer = Buffer.from(JSON.stringify(asset));
        ctx.stub.setEvent('CreateAsset', assetBuffer);
        return ctx.stub.putState(id, assetBuffer);
    }
    // TransferAsset updates the owner field of an asset with the given id in
    // the world state.
    async TransferAsset(ctx, id, newOwner) {
        const asset = await readState(ctx, id);
        asset.Owner = newOwner;
        const assetBuffer = Buffer.from(JSON.stringify(asset));
        await savePrivateData(ctx, id);
        ctx.stub.setEvent('TransferAsset', assetBuffer);
        return ctx.stub.putState(id, assetBuffer);
    }
    // ReadAsset returns the asset stored in the world state with given id.
    async ReadAsset(ctx, id) {
        const asset = await readState(ctx, id);
        await addPrivateData(ctx, asset.ID, asset);
        return JSON.stringify(asset);
    }
    // UpdateAsset updates an existing asset in the world state with provided parameters.
    async UpdateAsset(ctx, id, color, size, owner, appraisedValue) {
        const asset = await readState(ctx, id);
        asset.Color = color;
        asset.Size = size;
        asset.Owner = owner;
        asset.AppraisedValue = appraisedValue;
        const assetBuffer = Buffer.from(JSON.stringify(asset));
        await savePrivateData(ctx, id);
        ctx.stub.setEvent('UpdateAsset', assetBuffer);
        return ctx.stub.putState(id, assetBuffer);
    }
    // DeleteAsset deletes an given asset from the world state.
    async DeleteAsset(ctx, assetId) {
        const asset = await readState(ctx, assetId);
        const assetBuffer = Buffer.from(JSON.stringify(asset));
        await removePrivateData(ctx, assetId);
        ctx.stub.setEvent('DeleteAsset', assetBuffer);
        return ctx.stub.deleteState(assetId);
    }
};
__decorate([
    fabric_contract_api_1.Transaction(),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [fabric_contract_api_1.Context, String, String, Number, String, Number]),
    __metadata("design:returntype", Promise)
], AssetTransferEvents.prototype, "CreateAsset", null);
__decorate([
    fabric_contract_api_1.Transaction(false),
    fabric_contract_api_1.Returns('String'),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [fabric_contract_api_1.Context, String]),
    __metadata("design:returntype", Promise)
], AssetTransferEvents.prototype, "ReadAsset", null);
__decorate([
    fabric_contract_api_1.Transaction(),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [fabric_contract_api_1.Context, String, String, Number, String, Number]),
    __metadata("design:returntype", Promise)
], AssetTransferEvents.prototype, "UpdateAsset", null);
__decorate([
    fabric_contract_api_1.Transaction(),
    __metadata("design:type", Function),
    __metadata("design:paramtypes", [fabric_contract_api_1.Context, String]),
    __metadata("design:returntype", Promise)
], AssetTransferEvents.prototype, "DeleteAsset", null);
AssetTransferEvents = __decorate([
    fabric_contract_api_1.Info({ title: 'AssetTransferEvents', description: 'Smart Contract for trading assets and generate events' })
], AssetTransferEvents);
exports.AssetTransferEvents = AssetTransferEvents;
//# sourceMappingURL=asset-contract.js.map