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
    assetId: string;
    ownerOrg: string;
    publicDescription: string;
}

export interface AssetPropertiesJSON {
    objectType: string;
    assetId: string;
    color: string;
    size: number;
    salt: string;
}

export interface AssetPriceJSON {
    assetId: string;
    price: number;
    tradeId: string;
}

export interface Asset {
    assetId: string;
    ownerOrg: string;
    publicDescription: string;
}

export interface AssetProperties {
    assetId: string;
    color: string;
    size: number;
}

export interface AssetPrice {
    assetId: string;
    price: number;
    tradeId: string;
}

export function parse<T>(data: string): T {
    return JSON.parse(data);
}