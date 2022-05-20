/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

export const RED = '\x1b[31m\n';
export const GREEN = '\x1b[32m\n';
export const RESET = '\x1b[0m';

export interface AssetJSON {
    objectType: string;
    assetID: string;
    ownerOrg: string;
    publicDescription: string;
}

export interface AssetPropertiesJSON {
    objectType: string;
    assetID: string;
    color: string;
    size: number;
    salt: string;
}

export interface AssetPriceJSON {
    assetID: string;
    price: number;
    tradeID: string;
}

export interface AssetPrivateData {
    ObjectType: string;
    Color: string;
    Size: number;
}
export interface Asset {
    AssetId: string;
    OwnerOrg: string;
    PublicDescription: string;
}

export interface AssetProperties {
    AssetId: string;
    Color: string;
    Size: number;
}

export interface AssetPrice {
    AssetId: string;
    Price: number;
    TradeId: string;
}

export function parse<T>(data: string): T {
    return JSON.parse(data);
}
