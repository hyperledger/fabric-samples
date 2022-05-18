/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

export const RED = '\x1b[31m\n';
export const GREEN = '\x1b[32m\n';
export const RESET = '\x1b[0m';

export interface Asset {
    objectType: string;
    assetID: string;
    ownerOrg: string;
    publicDescription: string;
}

export interface AssetProperties {
    object_type: string;
    asset_id: string;
    color: string;
    size: number;
    salt: string;
}

export interface AssetPrice {
    asset_id: string;
    price: number;
    trade_id: string;
}

export function parse<T>(data: string): T {
    return JSON.parse(data);
}