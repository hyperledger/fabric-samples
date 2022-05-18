/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import { Contract } from '@hyperledger/fabric-gateway';
import { TextDecoder } from 'util';
import { Asset, AssetPrice, AssetProperties, parse } from './utils';

export class ContractWrapper {

    contract?: Contract;
    utf8Decoder: TextDecoder =new TextDecoder();

    public constructor(contract: Contract) {
        this.contract = contract;
    }

    public async createAsset(org: string, assetKey: string, assetProperties: AssetProperties): Promise<void> {
        await this.contract?.submit('CreateAsset', {
            arguments: [assetKey, `Asset ${assetKey} owned by ${org} is not for sale`],
            transientData: { asset_properties: JSON.stringify(assetProperties)},
        });
    }

    public async readAsset( assetKey: string): Promise<Asset> {
        const resultBytes = await this.contract?.evaluateTransaction('ReadAsset', assetKey);
        const result = this.utf8Decoder.decode(resultBytes);
        if (result.length !== 0) {
            return parse(result);
        } else {
            throw new Error('No Asset Found');
        }
    }

    public async getAssetPrivateProperties( assetKey: string ): Promise<AssetProperties> {
        const resultBytes = await this.contract?.evaluateTransaction('GetAssetPrivateProperties', assetKey);
        const result = this.utf8Decoder.decode(resultBytes);
        return parse<AssetProperties>(result);
    }

    public async changePublicDescription(assetKey: string, description: string): Promise<void> {
        await this.contract?.submit('ChangePublicDescription', {
            arguments:[assetKey, description],
        });
    }

    public async agreeToSell(asset_price: AssetPrice): Promise<void> {
        await this.contract?.submit('AgreeToSell', {
            arguments:[asset_price.asset_id],
            transientData: {asset_price: JSON.stringify(asset_price)}
        });
    }

    public async verifyAssetProperties(assetProperties: AssetProperties, org: string): Promise<AssetProperties> {
        const resultBytes = await this.contract?.evaluate('VerifyAssetProperties', {
            arguments:[assetProperties.asset_id],
            transientData: {asset_properties: JSON.stringify(assetProperties)},
        });
        const result = this.utf8Decoder.decode(resultBytes);
        if (result.length !== 0) {
            return parse<AssetProperties>(result);
        } else {
            throw new Error(`Private information about asset ${assetProperties.asset_id} has not been verified by ${org}`);
        }
    }

    public async agreeToBuy(asset_price: AssetPrice): Promise<void> {
        await this.contract?.submit('AgreeToBuy', {
            arguments:[asset_price.asset_id],
            transientData: {asset_price: JSON.stringify(asset_price)}
        });
    }

    public async getAssetSalesPrice(assetKey: string): Promise<Asset> {
        const resultBytes = await this.contract?.evaluateTransaction('GetAssetSalesPrice', assetKey);
        const result = this.utf8Decoder.decode(resultBytes);
        return parse<Asset>(result);
    }

    public async getAssetBidPrice( assetKey: string): Promise<AssetPrice> {
        const resultBytes = await this.contract?.evaluateTransaction('GetAssetBidPrice', assetKey);
        const result = this.utf8Decoder.decode(resultBytes);
        return parse<AssetPrice>(result);
    }

    public async transferAsset(buyerOrgID: string, asset_properties: AssetProperties, asset_price: AssetPrice, endorsingOrganizations: string[]): Promise<void> {
        await this.contract?.submit('TransferAsset', {
            arguments:[asset_properties.asset_id, buyerOrgID],
            transientData: {
                asset_properties: JSON.stringify(asset_properties),
                asset_price: JSON.stringify(asset_price)},
            endorsingOrganizations:endorsingOrganizations
        });
    }
}