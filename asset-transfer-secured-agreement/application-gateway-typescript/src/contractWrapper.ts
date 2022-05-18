/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import { Contract } from '@hyperledger/fabric-gateway';
import { TextDecoder } from 'util';
import { Asset, AssetJSON, AssetPrice, AssetPriceJSON, AssetProperties, AssetPropertiesJSON, parse } from './utils';

export class ContractWrapper {

    readonly #contract: Contract;
    readonly #utf8Decoder  = new TextDecoder();

    public constructor(contract: Contract) {
        this.#contract = contract;
    }

    public async createAsset(org: string, assetKey: string, assetProperties: AssetPropertiesJSON): Promise<void> {
        await this.#contract.submit('CreateAsset', {
            arguments: [assetKey, `Asset ${assetKey} owned by ${org} is not for sale`],
            transientData: { asset_properties: JSON.stringify(assetProperties)},
        });
    }

    public async readAsset( assetKey: string): Promise<Asset> {
        const resultBytes = await this.#contract.evaluateTransaction('ReadAsset', assetKey);
        const result = this.#utf8Decoder.decode(resultBytes);
        if (result.length !== 0) {
            const json = parse<AssetJSON>(result);
            return {
                assetId: json.assetId,
                ownerOrg: json.ownerOrg,
                publicDescription: json.publicDescription
            }
        } else {
            throw new Error('No Asset Found');
        }
    }

    public async getAssetPrivateProperties( assetKey: string ): Promise<AssetProperties> {
        const resultBytes = await this.#contract.evaluateTransaction('GetAssetPrivateProperties', assetKey);
        const result = this.#utf8Decoder.decode(resultBytes);
        const json = parse<AssetPropertiesJSON>(result);
        return {
            assetId: json.assetId,
            color: json.color,
            size: json.size,
        }

    }

    public async changePublicDescription(assetKey: string, description: string): Promise<void> {
        await this.#contract.submit('ChangePublicDescription', {
            arguments:[assetKey, description],
        });
    }

    public async agreeToSell(asset_price: AssetPriceJSON): Promise<void> {
        await this.#contract.submit('AgreeToSell', {
            arguments:[asset_price.assetId],
            transientData: {asset_price: JSON.stringify(asset_price)}
        });
    }

    public async verifyAssetProperties(assetProperties: AssetPropertiesJSON, org: string): Promise<AssetProperties> {
        const resultBytes = await this.#contract.evaluate('VerifyAssetProperties', {
            arguments:[assetProperties.assetId],
            transientData: {asset_properties: JSON.stringify(assetProperties)},
        });
        const result = this.#utf8Decoder.decode(resultBytes);
        if (result.length !== 0) {
            const json = parse<AssetPropertiesJSON>(result);
            return {
                assetId: json.assetId,
                color: json.color,
                size: json.size
            }
        } else {
            throw new Error(`Private information about asset ${assetProperties.assetId} has not been verified by ${org}`);
        }
    }

    public async agreeToBuy(asset_price: AssetPriceJSON): Promise<void> {
        await this.#contract.submit('AgreeToBuy', {
            arguments:[asset_price.assetId],
            transientData: {asset_price: JSON.stringify(asset_price)}
        });
    }

    public async getAssetSalesPrice(assetKey: string): Promise<Asset> {
        const resultBytes = await this.#contract.evaluateTransaction('GetAssetSalesPrice', assetKey);
        const result = this.#utf8Decoder.decode(resultBytes);
        const json = parse<AssetJSON>(result);
        return {
            assetId: json.assetId,
            ownerOrg: json.ownerOrg,
            publicDescription: json.publicDescription
        }
    }

    public async getAssetBidPrice( assetKey: string): Promise<AssetPrice> {
        const resultBytes = await this.#contract.evaluateTransaction('GetAssetBidPrice', assetKey);
        const result = this.#utf8Decoder.decode(resultBytes);
        const json = parse<AssetPriceJSON>(result);
        return {
            assetId: json.assetId,
            price: json.price,
            tradeId: json.tradeId,
        }
    }

    public async transferAsset(buyerOrgID: string, asset_properties: AssetPropertiesJSON, asset_price: AssetPriceJSON, endorsingOrganizations: string[]): Promise<void> {
        await this.#contract.submit('TransferAsset', {
            arguments:[asset_properties.assetId, buyerOrgID],
            transientData: {
                asset_properties: JSON.stringify(asset_properties),
                asset_price: JSON.stringify(asset_price)},
            endorsingOrganizations:endorsingOrganizations
        });
    }
}