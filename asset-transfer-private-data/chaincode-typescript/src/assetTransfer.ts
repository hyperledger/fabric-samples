/*
 * SPDX-License-Identifier: Apache-2.0
 */
import { Context, Contract, Info, Transaction } from 'fabric-contract-api';
import stringify from 'json-stringify-deterministic';
import sortKeysRecursive from 'sort-keys-recursive';
import { Asset } from './asset';
import { AssetPrivateDetails } from './assetTransferDetails';
import { AssetTransferTransientInput } from './assetTransferTransientInput';
import { TransferAgreement } from './transferAgreement';

const assetCollection = 'assetCollection';
const transferAgreementObjectType = 'transferAgreement';

@Info({ title: 'AssetTransfer', description: 'Smart contract for trading assets' })
export class AssetTransfer extends Contract {

    // CreateAsset issues a new asset to the world state with given details.
    @Transaction()
    public async CreateAsset(ctx: Context): Promise<void> {
        const transientMap = ctx.stub.getTransient();
        const transientAssetJSON = transientMap.get('asset_properties');

        if (transientAssetJSON.length === 0) {
            throw new Error('asset properties not found in the transient map');
        }
        const jsonBytesToString = String.fromCharCode(...transientAssetJSON);
        const jsonFromString = JSON.parse(jsonBytesToString);

        // Check properties
        if (jsonFromString.objectType.length === 0) {
            throw new Error('objectType field must be a non-empty string');
        }
        if (jsonFromString.assetID.length === 0) {
            throw new Error('assetID field must be a non-empty string');
        }
        if (jsonFromString.color.length === 0) {
            throw new Error('color field must be a non-empty string');
        }
        if (jsonFromString.size <= 0) {
            throw new Error('size field must be a positive integer');
        }
        if (jsonFromString.appraisedValue <= 0) {
            throw new Error('appraisedValue field must be a positive integer');
        }

        // Check if asset already exists
        const assetAsBytes = await ctx.stub.getPrivateData(assetCollection, jsonFromString.assetID);
        if (assetAsBytes.length !== 0) {
            throw new Error('this asset already exists: ' + jsonFromString.assetID);
        }

        // Get ID of submitting client identity
        const clientID = ctx.clientIdentity.getID();

        // Verify that the client is submitting request to peer in their organization
        // This is to ensure that a client from another org doesn't attempt to read or
        // write private data from this peer.
        const err = await this.verifyClientOrgMatchesPeerOrg(ctx);
        if (err !== null) {
            throw new Error('CreateAsset cannot be performed: Error ' + err);
        }
        const asset: Asset = {
            ID: jsonFromString.assetID,
            Color: jsonFromString.color,
            Size: jsonFromString.size,
            Owner: clientID,
        };

        // Save asset to private data collection
        // Typical logger, logs to stdout/file in the fabric managed docker container, running this chaincode
        // Look for container name like dev-peer0.org1.example.com-{chaincodename_version}-xyz
        await ctx.stub.putPrivateData(assetCollection, asset.ID, Buffer.from(stringify(sortKeysRecursive(asset))));

        // Save asset details to collection visible to owning organization
        const assetPrivateDetails: AssetPrivateDetails = {
            ID: jsonFromString.assetID,
            AppraisedValue: jsonFromString.appraisedValue,
        };
        // Get collection name for this organization.
        const orgCollection = await this.getCollectionName(ctx);
        // Put asset appraised value into owners org specific private data collection
        console.log('Put: collection %v, ID %v', orgCollection, jsonFromString.assetID);
        await ctx.stub.putPrivateData(orgCollection, asset.ID, Buffer.from(stringify(sortKeysRecursive(assetPrivateDetails))));
        return null;
    }
    // AgreeToTransfer is used by the potential buyer of the asset to agree to the
    // asset value. The agreed to appraisal value is stored in the buying orgs
    // org specifc collection, while the the buyer client ID is stored in the asset collection
    // using a composite key
    @Transaction()
    public async AgreeToTransfer(ctx: Context): Promise<void> {
        // Get ID of submitting client identity
        const clientID = ctx.clientIdentity.getID();
        // Value is private, therefore it gets passed in transient field
        const transientMap = ctx.stub.getTransient();
        // Persist the JSON bytes as-is so that there is no risk of nondeterministic marshaling.
        const valueJSONasBytes = transientMap.get('asset_value');
        if (valueJSONasBytes.length === 0) {
            throw new Error('asset value not found in the transient map');
        }
        const jsonBytesToString = String.fromCharCode(...valueJSONasBytes);
        const jsonFromString = JSON.parse(jsonBytesToString);
        // Do some error checking since we get the chance
        if (jsonFromString.assetID.length === 0) {
            throw new Error('assetID field must be a non-empty string');
        }
        if (jsonFromString.appraisedValue <= 0) {
            throw new Error('appraisedValue field must be a positive integer');
        }
        const valueJSON: AssetPrivateDetails = {
            ID: jsonFromString.assetID,
            AppraisedValue: jsonFromString.appraisedValue,
        };
        // Read asset from the private data collection
        const asset = await this.ReadAsset(ctx, valueJSON.ID);
        // Verify that the client is submitting request to peer in their organization
        const err = await this.verifyClientOrgMatchesPeerOrg(ctx);
        if (err !== null) {
            throw new Error('AgreeToTransfer  cannot be performed: Error ' + err);
        }
        // Get collection name for this organization. Needs to be read by a member of the organization.
        const orgCollection = await this.getCollectionName(ctx);
        console.log(`AgreeToTransfer Put: collection ${orgCollection}, ID ${valueJSON.ID}`);
        // Put agreed value in the org specifc private data collection
        await ctx.stub.putPrivateData(orgCollection, asset.ID, Buffer.from(stringify(sortKeysRecursive(valueJSON))));
        // Create agreeement that indicates which identity has agreed to purchase
        // In a more realistic transfer scenario, a transfer agreement would be secured to ensure that it cannot
        // be overwritten by another channel member
        const transferAgreeKey = ctx.stub.createCompositeKey(transferAgreementObjectType, [valueJSON.ID]);
        console.log(`AgreeToTransfer Put: collection ${assetCollection}, ID ${valueJSON.ID}, Key ${transferAgreeKey}`);
        await ctx.stub.putPrivateData(assetCollection, transferAgreeKey, new Uint8Array(Buffer.from(clientID)));
        return null;
    }
    @Transaction()
    // TransferAsset transfers the asset to the new owner by setting a new owner ID
    public async TransferAsset(ctx: Context): Promise<void> {
        const transientMap = ctx.stub.getTransient();
        // Asset properties are private, therefore they get passed in transient field
        const transientTransferJSON = transientMap.get('asset_owner');
        if (transientTransferJSON.length === 0) {
            throw new Error('asset owner not found in the transient map');
        }
        const jsonBytesToString = String.fromCharCode(...transientTransferJSON);
        const jsonFromString = JSON.parse(jsonBytesToString);
        // Do some error checking since we get the chance
        if (jsonFromString.assetID.length === 0) {
            throw new Error('assetID field must be a non-empty string');
        }
        if (jsonFromString.buyerMSP.length === 0) {
            throw new Error('buyerMSP field must be a non-empty string');
        }
        const assetTransferInput: AssetTransferTransientInput = {
            ID: jsonFromString.assetID,
            BuyerMSP: jsonFromString.buyerMSP,
        };
        console.log('TransferAsset: verify asset exists ID ' + assetTransferInput.ID);
        // Read asset from the private data collection
        const asset = await this.ReadAsset(ctx, assetTransferInput.ID);
        // Verify that the client is submitting request to peer in their organization
        const err = await this.verifyClientOrgMatchesPeerOrg(ctx);
        if (err !== null) {
            throw new Error('TransferAsset cannot be performed: Error ' + err);
        }
        // Verify transfer details and transfer owner
        await this.verifyAgreement(ctx, assetTransferInput.ID, asset.Owner, assetTransferInput.BuyerMSP);

        const transferAgreement = await this.ReadTransferAgreement(ctx, assetTransferInput.ID);
        if (transferAgreement.BuyerID === '') {
            throw new Error('BuyerID not found in TransferAgreement for ' + assetTransferInput.ID);
        }
        // Transfer asset in private data collection to new owner
        asset.Owner = transferAgreement.BuyerID;
        console.log(`TransferAsset Put: collection ${assetCollection}, ID ${assetTransferInput.ID}`);
        await ctx.stub.putPrivateData(assetCollection, assetTransferInput.ID, Buffer.from(stringify(sortKeysRecursive(asset)))); // rewrite the asset

        // Get collection name for this organization
        const ownersCollection = await this.getCollectionName(ctx);
        // Delete the asset appraised value from this organization's private data collection
        await ctx.stub.deletePrivateData(ownersCollection, assetTransferInput.ID);
        // Delete the transfer agreement from the asset collection
        const transferAgreeKey = ctx.stub.createCompositeKey(transferAgreementObjectType, [assetTransferInput.ID]);
        await ctx.stub.deletePrivateData(assetCollection, transferAgreeKey);
        return null;
    }
    @Transaction()
    // DeleteAsset can be used by the owner of the asset to delete the asset
    public async DeleteAsset(ctx: Context): Promise<void> {
        // Value is private, therefore it gets passed in transient field
        const transientMap = ctx.stub.getTransient();
        // Persist the JSON bytes as-is so that there is no risk of nondeterministic marshaling.
        const valueJSONasBytes = transientMap.get('asset_delete');
        if (valueJSONasBytes.length === 0) {
            throw new Error('asset to delete not found in the transient map');
        }
        const jsonBytesToString = String.fromCharCode(...valueJSONasBytes);
        const jsonFromString = JSON.parse(jsonBytesToString);
        if (jsonFromString.assetID.length === 0) {
            throw new Error('assetID field must be a non-empty string');
        }
        // Verify that the client is submitting request to peer in their organization
        const err = await this.verifyClientOrgMatchesPeerOrg(ctx);
        if (err !== null) {
            throw new Error('DeleteAsset cannot be performed: Error ' + err);
        }
        console.log('Deleting Asset: ' + jsonFromString.assetID);
        // get the asset from chaincode state
        const valAsbytes = await ctx.stub.getPrivateData(assetCollection, jsonFromString.assetID);
        if (valAsbytes.length === 0) {
            throw new Error('asset not found: ' + jsonFromString.assetID);
        }
        const ownerCollection = await this.getCollectionName(ctx);
        // Check the asset is in the caller org's private collection
        const valAsbytesPrivate = await ctx.stub.getPrivateData(ownerCollection, jsonFromString.assetID);
        if (valAsbytesPrivate.length === 0) {
            throw new Error(`asset not found in owner's private Collection: ${ownerCollection} : ${jsonFromString.assetID}`);
        }
        // delete the asset from state
        await ctx.stub.deletePrivateData(assetCollection, jsonFromString.assetID);
        // Finally, delete private details of asset
        await ctx.stub.deletePrivateData(ownerCollection, jsonFromString.assetID);
        return null;
    }
    @Transaction()
    // PurgeAsset can be used by the owner of the asset to delete the asset
    // Trigger removal of the asset
    public async PurgeAsset(ctx: Context): Promise<void> {
        // Value is private, therefore it gets passed in transient field
        const transientMap = ctx.stub.getTransient();
        // Persist the JSON bytes as-is so that there is no risk of nondeterministic marshaling.
        const valueJSONasBytes = transientMap.get('asset_purge');
        if (valueJSONasBytes.length === 0) {
            throw new Error('asset to purge not found in the transient map');
        }
        const jsonBytesToString = String.fromCharCode(...valueJSONasBytes);
        const jsonFromString = JSON.parse(jsonBytesToString);
        if (jsonFromString.assetID.length === 0) {
            throw new Error('assetID field must be a non-empty string');
        }
        // Verify that the client is submitting request to peer in their organization
        const err = await this.verifyClientOrgMatchesPeerOrg(ctx);
        if (err !== null) {
            throw new Error('PurgeAsset cannot be performed: Error ' + err);
        }
        console.log('Purging Asset: ' + jsonFromString.assetID);
        // Note that there is no check here to see if the id exist; it might have been 'deleted' already
        // so a check here is pointless. We would need to call purge irrespective of the result
        // A delete can be called before purge, but is not essential
        const ownerCollection = await this.getCollectionName(ctx);
        // delete the asset from state
        await ctx.stub.purgePrivateData(assetCollection, jsonFromString.assetID);
        // Finally, delete private details of asset
        await ctx.stub.purgePrivateData(ownerCollection, jsonFromString.assetID);
        return null;
    }
    @Transaction()
    // DeleteTranferAgreement can be used by the buyer to withdraw a proposal from
    // the asset collection and from his own collection.
    public async DeleteTransferAgreement(ctx: Context): Promise<void> {
        // Value is private, therefore it gets passed in transient field
        const transientMap = ctx.stub.getTransient();
        // Persist the JSON bytes as-is so that there is no risk of nondeterministic marshaling.
        const valueJSONasBytes = transientMap.get('agreement_delete');
        if (valueJSONasBytes.length === 0) {
            throw new Error('agreement to delete not found in the transient map');
        }
        const jsonBytesToString = String.fromCharCode(...valueJSONasBytes);
        const jsonFromString = JSON.parse(jsonBytesToString);
        if (jsonFromString.assetID.length === 0) {
            throw new Error('assetID field must be a non-empty string');
        }
        // Verify that the client is submitting request to peer in their organization
        const err = await this.verifyClientOrgMatchesPeerOrg(ctx);
        if (err !== null) {
            throw new Error('DeleteTranferAgreement cannot be performed: Error ' + err);
        }
        // Delete private details of agreement
        // Get proposers collection.
        const orgCollection = await this.getCollectionName(ctx);
        // Delete the transfer agreement from the asset collection
        const transferAgreeKey = ctx.stub.createCompositeKey(transferAgreementObjectType, [jsonFromString.assetID]);
        // get the transfer_agreement
        const valAsbytes = await ctx.stub.getPrivateData(assetCollection, transferAgreeKey);

        if (valAsbytes.length === 0) {
            throw new Error(`asset's transfer_agreement does not exist: ${jsonFromString.assetID}`);
        }
        // Delete the asset
        await ctx.stub.deletePrivateData(orgCollection, jsonFromString.assetID);
        // Delete transfer agreement record, remove agreement from state
        await ctx.stub.deletePrivateData(assetCollection, transferAgreeKey);
        return null;
    }
    /*
        GETTERS
    */
    // ReadAsset reads the information from collection
    @Transaction()
    public async ReadAsset(ctx: Context, id: string): Promise<Asset> {
        // Check if asset already exists
        const assetAsBytes = await ctx.stub.getPrivateData(assetCollection, id);
        // No Asset found, return empty response
        if (assetAsBytes.length === 0) {
            throw new Error(id + ' does not exist in collection ' + assetCollection);
        }
        const jsonBytesToString = String.fromCharCode(...assetAsBytes);
        const jsonFromBytes = JSON.parse(jsonBytesToString);
        const asset: Asset = {
            ID: jsonFromBytes.ID,
            Color: jsonFromBytes.Color,
            Size: jsonFromBytes.Size,
            Owner: jsonFromBytes.Owner,
        };
        return asset;
    }
    // ReadAssetPrivateDetails reads the asset private details in organization specific collection
    @Transaction()
    public async ReadAssetPrivateDetails(ctx: Context, collection: string, id: string): Promise<AssetPrivateDetails> {
        // Check if asset already exists
        const assetAsBytes = await ctx.stub.getPrivateData(collection, id);
        // No Asset found, return empty response
        if (assetAsBytes.length === 0) {
            throw new Error(id + ' does not exist in collection ' + collection);
        }
        const jsonBytesToString = String.fromCharCode(...assetAsBytes);
        const jsonFromBytes = JSON.parse(jsonBytesToString);
        const asset: AssetPrivateDetails = {
            ID: jsonFromBytes.ID,
            AppraisedValue: jsonFromBytes.AppraisedValue,
        };
        return asset;
    }
    // ReadTransferAgreement gets the buyer's identity from the transfer agreement from collection
    @Transaction()
    public async ReadTransferAgreement(ctx: Context, assetID: string): Promise<TransferAgreement> {
        // composite key for TransferAgreement of this asset
        const transferAgreeKey = ctx.stub.createCompositeKey(transferAgreementObjectType, [assetID]);
        // Get the identity from collection
        const buyerIdentity = await ctx.stub.getPrivateData(assetCollection, transferAgreeKey);

        if (buyerIdentity.length === 0) {
            throw new Error(`TransferAgreement for ${assetID} does not exist `);
        }
        const agreement: TransferAgreement = {
            ID: assetID,
            BuyerID: String(buyerIdentity),
        };
        return agreement;
    }

    // GetAssetByRange performs a range query based on the start and end keys provided. Range
    // queries can be used to read data from private data collections, but can not be used in
    // a transaction that also writes to private data.
    @Transaction()
    public async GetAssetByRange(ctx: Context, startKey: string, endKey: string): Promise<Asset[]> {
        const resultsIterator = ctx.stub.getPrivateDataByRange(assetCollection, startKey, endKey);
        const results: Asset[] = [];
        for await (const res of resultsIterator) {
            const resBytesToString = String.fromCharCode(...res.value);
            const jsonFromString = JSON.parse(resBytesToString);
            results.push({
                ID: jsonFromString.ID,
                Color: jsonFromString.Color,
                Size: jsonFromString.Size,
                Owner: jsonFromString.Owner,
            });
        }
        return results;
    }
    /*
        HELPERS
    */
    // verifyAgreement is an internal helper function used by TransferAsset to verify
    // that the transfer is being initiated by the owner and that the buyer has agreed
    // to the same appraisal value as the owner
    public async verifyAgreement(ctx: Context, assetID: string, owner: string, buyerMSP: string): (Promise<void>) {
        // Check 1: verify that the transfer is being initiatied by the owner
        // Get ID of submitting client identity
        const clientID = ctx.clientIdentity.getID();
        if (clientID !== owner) {
            throw new Error(`error: submitting client(${clientID}) identity does not own asset ${assetID}.Owner is ${owner}`);
        }
        // Check 2: verify that the buyer has agreed to the appraised value
        // Get collection names
        const collectionOwner = await this.getCollectionName(ctx); // get owner collection from caller identity

        const collectionBuyer = buyerMSP + 'PrivateCollection'; // get buyers collection
        // Get hash of owners agreed to value
        const ownerAppraisedValueHash = await ctx.stub.getPrivateDataHash(collectionOwner, assetID);

        if (ownerAppraisedValueHash.length === 0) {
            throw new Error(`hash of appraised value for ${assetID} does not exist in collection ${collectionOwner}`);
        }
        // Get hash of buyers agreed to value
        const buyerAppraisedValueHash = await ctx.stub.getPrivateDataHash(collectionBuyer, assetID);
        if (buyerAppraisedValueHash.length === 0) {
            throw new Error(`hash of appraised value for ${assetID} does not exist in collection ${buyerAppraisedValueHash} . AgreeToTransfer must be called by the buyer first`);
        }
        // Verify that the two hashes match
        if (ownerAppraisedValueHash.toString() !== buyerAppraisedValueHash.toString()) {
            throw new Error(`hash for appraised value for owner ${ownerAppraisedValueHash} does not value for seller ${buyerAppraisedValueHash}`);
        }
        return null;
    }
    // getCollectionName is an internal helper function to get collection of submitting client identity.
    public async getCollectionName(ctx: Context): Promise<string> {
        // Get the MSP ID of submitting client identity
        const clientMSPID = ctx.clientIdentity.getMSPID();
        // Create the collection name
        const orgCollection = clientMSPID + 'PrivateCollection';

        return orgCollection;
    }
    // Get ID of submitting client identity
    public async submittingClientIdentity(ctx: Context): (Promise<string>) {

        const b64ID = ctx.clientIdentity.getID();

        // base64.StdEncoding.DecodeString(b64ID);
        const decodeID = Buffer.from(b64ID, 'base64').toString('binary');

        return String(decodeID);
    }
    // verifyClientOrgMatchesPeerOrg is an internal function used verify client org id and matches peer org id.
    public async verifyClientOrgMatchesPeerOrg(ctx: Context): (Promise<void>) {

        const clientMSPID = ctx.clientIdentity.getMSPID();

        const peerMSPID = ctx.stub.getMspID();

        if (clientMSPID !== peerMSPID) {
            throw new Error('client from org %v is not authorized to read or write private data from an org ' + clientMSPID + ' peer ' + peerMSPID);
        }

        return null;

    }
    // =======Rich queries =========================================================================
    // Two examples of rich queries are provided below (parameterized query and ad hoc query).
    // Rich queries pass a query string to the state database.
    // Rich queries are only supported by state database implementations
    //  that support rich query (e.g. CouchDB).
    // The query string is in the syntax of the underlying state database.
    // With rich queries there is no guarantee that the result set hasn't changed between
    //  endorsement time and commit time, aka 'phantom reads'.
    // Therefore, rich queries should not be used in update transactions, unless the
    // application handles the possibility of result set changes between endorsement and commit time.
    // Rich queries can be used for point-in-time queries against a peer.
    // ============================================================================================

    // ===== Example: Parameterized rich query =================================================

    // QueryAssetByOwner queries for assets based on assetType, owner.
    // This is an example of a parameterized query where the query logic is baked into the chaincode,
    // and accepting a single query parameter (owner).
    // Only available on state databases that support rich query (e.g. CouchDB)
    // =========================================================================================
    public async QueryAssetByOwner(ctx: Context, assetType: string, owner: string): Promise<Asset[]>  {

        const queryString = `{\'selector\':{\'objectType\':\'${assetType}\',\'owner\':\'${owner}\'}}`;

        return await this.getQueryResultForQueryString(ctx, queryString);
    }
    public async QueryAssets(ctx: Context, queryString: string): Promise<Asset[]>  {
        return await this.getQueryResultForQueryString(ctx, queryString);
    }
    public async getQueryResultForQueryString(ctx: Context, queryString: string): Promise<Asset[]> {

        const resultsIterator = ctx.stub.getPrivateDataQueryResult(assetCollection, queryString);

        const results: Asset[] = [];

        for await (const res of resultsIterator) {
            const resBytesToString = String.fromCharCode(...res.value);
            const jsonFromString = JSON.parse(resBytesToString);
            results.push({
                ID: jsonFromString.ID,
                Color: jsonFromString.Color,
                Size: jsonFromString.Size,
                Owner: jsonFromString.Owner,
            });
        }

        return results;
    }
}
