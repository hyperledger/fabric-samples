/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import { Contract } from '@hyperledger/fabric-gateway';
import { TextDecoder } from 'util';
import { Asset, AssetJSON, AssetPrice, AssetPriceJSON, AssetPrivateData, AssetProperties, AssetPropertiesJSON, GREEN, parse, RED, RESET } from './utils';
import crpto from 'crypto';

const randomBytes = crpto.randomBytes(256).toString('hex');

export class ContractWrapper {

    readonly #contract: Contract;
    readonly #org: string;
    readonly #utf8Decoder  = new TextDecoder();
    readonly #randomBytes: string = randomBytes;

    public constructor(contract: Contract, org: string) {
        this.#contract = contract;
        this.#org = org;
    }

    public async createAsset(asset: Asset, privateData: AssetPrivateData): Promise<void> {
        console.log(`${GREEN}--> Submit Transaction: CreateAsset, ${asset.AssetId} as ${asset.OwnerOrg} - endorsed by Org1${RESET}`);
        const assetPropertiesJSON: AssetPropertiesJSON = {
            objectType: 'asset_properties',
            assetID: asset.AssetId,
            color: privateData.Color,
            size: privateData.Size,
            salt: this.#randomBytes };

        await this.#contract.submit('CreateAsset', {
            arguments: [asset.AssetId, asset.PublicDescription],
            transientData: { asset_properties: JSON.stringify(assetPropertiesJSON)},
        });

        console.log(`*** Result: committed, asset ${asset.AssetId} is owned by Org1`);
    }

    public async readAsset(assetKey: string, ownerOrg: string): Promise<void> {
        console.log(`${GREEN}--> Evaluate Transactions: ReadAsset as ${this.#org}, - ${assetKey} should be owned by ${ownerOrg}${RESET}`);

        const resultBytes = await this.#contract.evaluateTransaction('ReadAsset', assetKey);

        const result = this.#utf8Decoder.decode(resultBytes);
        if (result.length !== 0) {
            const json = parse<AssetJSON>(result);
            if (json.ownerOrg === ownerOrg) {
                console.log(`*** Result from ${this.#org} - asset ${json.assetID} owned by ${json.ownerOrg} DESC: ${json.publicDescription}`);
            } else {
                console.log(`${RED}*** Failed owner check from ${this.#org} - asset ${json.assetID} owned by ${json.ownerOrg} DESC:${json.publicDescription}${RESET}`);
            }
        } else {
            throw new Error('No Asset Found');
        }
    }

    public async getAssetPrivateProperties(assetKey: string, ownerOrg: string): Promise<void> {
        console.log(`${GREEN}--> Evaluate Transaction: GetAssetPrivateProperties, - ${assetKey} from organization ${this.#org}${RESET}`);
        if(this.#org !== ownerOrg) {
            console.log(`${GREEN}* Expected to fail as ${this.#org} is not the owner and does not have the private details.${RESET}`);
        }

        const resultBytes = await this.#contract.evaluateTransaction('GetAssetPrivateProperties', assetKey);

        const resultString = this.#utf8Decoder.decode(resultBytes);
        const json = parse<AssetPropertiesJSON>(resultString);
        const result: AssetProperties = {
            AssetId: json.assetID,
            Color: json.color,
            Size: json.size,
        };
        console.log('*** Result:', result);
    }


    public async changePublicDescription(asset: Asset): Promise<void> {
        console.log(`${GREEN}--> Submit Transaction: ChangePublicDescription ${asset.AssetId}, as ${this.#org} - endorse by ${this.#org} ${RESET}`);
        if (asset.OwnerOrg !== this.#org) {
            console.log(`${GREEN}* Expected to fail as ${this.#org} is not the owner.${RESET}`);
        }

        await this.#contract.submit('ChangePublicDescription', {
            arguments:[asset.AssetId, asset.PublicDescription],
        });

        console.log(`*** Result: committed, Desc: ${asset.PublicDescription}`);
    }

    public async agreeToSell(assetPrice: AssetPrice): Promise<void> {

        console.log(`${GREEN}--> Submit Transaction: AgreeToSell, ${assetPrice.AssetId} as ${this.#org} - endorsed by ${this.#org}${RESET}`);
        const assetPriceJSON: AssetPriceJSON = {
            assetID:assetPrice.AssetId,
            price:assetPrice.Price,
            tradeID:assetPrice.TradeId
        };

        await this.#contract.submit('AgreeToSell', {
            arguments:[assetPrice.AssetId],
            transientData: {asset_price: JSON.stringify(assetPriceJSON)}
        });

        console.log(`*** Result: committed, ${this.#org} has agreed to sell asset ${assetPrice.AssetId} for ${assetPrice.Price}`);
    }

    public async verifyAssetProperties(assetProperties: AssetProperties): Promise<void> {
        console.log(`${GREEN}--> Evalute: VerifyAssetProperties, ${assetProperties.AssetId} as ${this.#org} - endorsed by ${this.#org}${RESET}`);
        const assetPropertiesJSON: AssetPropertiesJSON = Object.assign({}, {objectType: 'asset_properties',
            assetID: assetProperties.AssetId,
            color: assetProperties.Color,
            size: assetProperties.Size,
            salt: this.#randomBytes });

        const resultBytes = await this.#contract.evaluate('VerifyAssetProperties', {
            arguments:[assetPropertiesJSON.assetID],
            transientData: {asset_properties: JSON.stringify(assetPropertiesJSON)},
        });

        const resultString = this.#utf8Decoder.decode(resultBytes);
        if (resultString.length !== 0) {
            const json = parse<AssetPropertiesJSON>(resultString);
            const result: AssetProperties =  {
                AssetId: json.assetID,
                Color: json.color,
                Size: json.size
            };
            if (result) {
                console.log(`*** Success VerifyAssetProperties, private information about asset ${assetProperties.AssetId} has been verified by ${this.#org}`);
            } else {
                console.log(`*** Failed: VerifyAssetProperties, private information about asset ${assetProperties.AssetId} has not been verified by ${this.#org}`);
            }

        } else {
            throw new Error(`Private information about asset ${assetProperties.AssetId} has not been verified by ${this.#org}`);
        }
    }

    public async agreeToBuy(assetPrice: AssetPrice, ): Promise<void> {

        console.log(`${GREEN}--> Submit Transaction: AgreeToBuy, ${assetPrice.AssetId} as ${this.#org} - endorsed by ${this.#org}${RESET}`);
        const assetPriceJSON: AssetPriceJSON = {
            assetID: assetPrice.AssetId,
            price: assetPrice.Price,
            tradeID: assetPrice.TradeId
        };

        await this.#contract.submit('AgreeToBuy', {
            arguments:[assetPrice.AssetId],
            transientData: {asset_price: JSON.stringify(assetPriceJSON)}
        });

        console.log(`*** Result: committed, ${this.#org} has agreed to buy asset ${assetPrice.AssetId} for 100`);

    }

    public async getAssetSalesPrice(assetKey: string, ownerOrg: string): Promise<void> {

        console.log(`${GREEN}--> Evaluate Transaction: GetAssetSalesPrice, - ${assetKey} from organization ${this.#org}${RESET}`);
        if(this.#org !== ownerOrg) {
            console.log(`${GREEN}* Expected to fail as ${this.#org} has not set a sale price.${RESET}`);
        }

        const resultBytes = await this.#contract.evaluateTransaction('GetAssetSalesPrice', assetKey);

        const resultString = this.#utf8Decoder.decode(resultBytes);
        const json = parse<AssetPriceJSON>(resultString);

        const result: AssetPrice =  {
            AssetId: json.assetID,
            Price: json.price,
            TradeId: json.tradeID
        };

        console.log('*** Result: GetAssetSalesPrice', result);
    }

    public async getAssetBidPrice(assetKey: string, buyerOrgID: string): Promise<void> {

        console.log(`${GREEN}--> Evaluate Transaction: GetAssetBidPrice, - ${assetKey} from organization ${this.#org}${RESET}`);
        if(this.#org !== buyerOrgID){
            console.log(`${GREEN}* Expected to fail as ${this.#org} has not agreed to buy.${RESET}`);
        }

        const resultBytes = await this.#contract.evaluateTransaction('GetAssetBidPrice', assetKey);

        const resultString = this.#utf8Decoder.decode(resultBytes);
        const json = parse<AssetPriceJSON>(resultString);
        const result: AssetPrice = {
            AssetId: json.assetID,
            Price: json.price,
            TradeId: json.tradeID,
        };

        console.log('*** Result: GetAssetBidPrice', result);
    }

    public async transferAsset( privateData: AssetPrivateData, assetPrice: AssetPrice, endorsingOrganizations: string[], ownerOrgID: string, buyerOrgID: string): Promise<void> {

        console.log(`${GREEN}--> Submit Transaction: TransferAsset, ${assetPrice.AssetId} as ${this.#org } - endorsed by ${this.#org}${RESET}`);

        if (this.#org !== ownerOrgID) {
            console.log(`${GREEN}* Expected to fail as the owner is ${ownerOrgID}.${RESET}`);
        } else if (assetPrice.Price === 110) {
            console.log(`${GREEN}* Expected to fail as sell price and the bid price are not the same.${RESET}`);
        }

        const assetPropertiesJSON: AssetPropertiesJSON = Object.assign({}, {objectType: 'asset_properties',
            assetID: assetPrice.AssetId,
            color: privateData.Color,
            size: privateData.Size,
            salt: this.#randomBytes });

        const assetPriceJSON: AssetPriceJSON = { assetID: assetPrice.AssetId, price:assetPrice.Price, tradeID:assetPrice.TradeId};

        await this.#contract.submit('TransferAsset', {
            arguments:[assetPropertiesJSON.assetID, buyerOrgID],
            transientData: {
                asset_properties: JSON.stringify(assetPropertiesJSON),
                asset_price: JSON.stringify(assetPriceJSON)},
            endorsingOrganizations:endorsingOrganizations
        });

        console.log(`${GREEN}*** Result: committed, ${this.#org} has transfered the asset ${assetPrice.AssetId} to ${buyerOrgID} ${RESET}`);
    }
}