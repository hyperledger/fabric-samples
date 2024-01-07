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
Object.defineProperty(exports, "__esModule", { value: true });
exports.Asset = void 0;
const fabric_contract_api_1 = require("fabric-contract-api");
let Asset = class Asset {
};
__decorate([
    (0, fabric_contract_api_1.Property)(),
    __metadata("design:type", String)
], Asset.prototype, "ID", void 0);
__decorate([
    (0, fabric_contract_api_1.Property)(),
    __metadata("design:type", Number)
], Asset.prototype, "Size", void 0);
__decorate([
    (0, fabric_contract_api_1.Property)(),
    __metadata("design:type", String)
], Asset.prototype, "Owner", void 0);
__decorate([
    (0, fabric_contract_api_1.Property)(),
    __metadata("design:type", Number)
], Asset.prototype, "TimeStamp", void 0);
Asset = __decorate([
    (0, fabric_contract_api_1.Object)()
], Asset);
exports.Asset = Asset;
//# sourceMappingURL=landAsset.js.map