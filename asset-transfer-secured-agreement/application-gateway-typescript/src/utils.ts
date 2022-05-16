/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

export const RED = '\x1b[31m\n';
export const GREEN = '\x1b[32m\n';
export const RESET = '\x1b[0m';

export interface asset {
    objectType: string,
    assetID: string,
    ownerOrg: string,
    publicDescription: string,
}

export interface asset_properties {
    object_type: string,
    asset_id: string,
    color: string,
    size: number,
    salt: string
}

export interface asset_price {
    asset_id: string,
    price: number,
    trade_id: string
}

export interface asset_price {
    asset_id: string,
    price: number,
    trade_id: string
}

export class AssetProperties {

    asset_properties: asset_properties;

    private constructor (asset_properties: asset_properties) {
        this.asset_properties = asset_properties;
    }

    static instance(object_type: string, asset_id: string, color: string, size: number, salt: string): string {
        const asset_properties : asset_properties = {
            object_type,
            asset_id,
            color,
            size,
            salt,
        };
        return new AssetProperties(asset_properties).serialize();
    }

    private serialize(): string {
        return JSON.stringify(this.asset_properties);
    }

}

export class Asset {

    asset: asset;

    private constructor (asset: asset) {
        this.asset = asset;
    }

    static instance( objectType: string, assetID: string, ownerOrg: string, publicDescription: string): string {
        const asset = {
            objectType,
            assetID,
            ownerOrg,
            publicDescription
        }
        return new Asset(asset).serialize();
    }

    private serialize(): string {
        return JSON.stringify(this.asset);
    }

    static parse(asset: string): asset {
        return JSON.parse(asset);
    }

}

export class AssetPrice {

    asset_price: asset_price;

    constructor (asset_price : asset_price) {
        this.asset_price = asset_price;
    }

    static instance(asset_id: string, price:number, trade_id: string): string {
        const asset_price = {
            asset_id,
            price,
            trade_id
        }
        return new AssetPrice(asset_price).serialize()
    }

    private serialize(): string {
        return JSON.stringify(this.asset_price);
    }

}