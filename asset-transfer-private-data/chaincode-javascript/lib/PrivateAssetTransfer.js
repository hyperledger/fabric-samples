/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

'use strict';

// Deterministic JSON.stringify()
const stringify  = require('json-stringify-deterministic');
const sortKeysRecursive  = require('sort-keys-recursive');
const { Contract } = require('fabric-contract-api');


const assetCollection = "assetCollection"
const transferAgreementObjectType = "transferAgreement"

// PrivateData SmartContract
class PrivateAssetTransfer extends Contract {

    // CreateAsset creates a new asset by placing the main asset details in the assetCollection
    // that can be read by both organizations. The appraisal value is stored in the owners org specific collection.
    async CreateAsset(ctx) {
        
        // Get the new asset from transient map
        const transientMap = await ctx.stub.getTransient();

        // Asset properties are private, therefore they get passed in transient field, instead of func args
        const transientAssetJSON = transientMap.get("asset_properties");
        if (!transientAssetJSON) {
            throw new Error('The asset was not found in the transient map input.');
        }

        let assetInput = JSON.parse(transientAssetJSON);
        // inputs validation
        if (!assetInput.objectType && assetInput.objectType === "") {
            throw new Error('objectType field is required, it must be a non-empty string.');
        }
        if (!assetInput.assetID && assetInput.assetID === "") {
            throw new Error('assetID field is required, it must be a non-empty string');
        }
        if (!assetInput.color && assetInput.color === "") {
            throw new Error('color field is required, it must be a non-empty string.');
        }
        if (!assetInput.size && assetInput.size <= "") {
            throw new Error('size field is required, it must be a positive integer.');
        }
        if (!assetInput.appraisedValue && assetInput.appraisedValue <= "") {
            throw new Error('appraisedValue field is required, it must be a positive integer.');
        }

	    // Check if asset already exists
        const assetAsBytes = await ctx.stub.getPrivateData(assetCollection, assetInput.assetID);
        if (assetAsBytes != '') {
            throw new Error(`This asset (${assetInput.assetID}) already exists`);
        }

        // Get the ID of submitting client identity
        const ClientID = await this.submittingClientIdentity(ctx);

        // Verify that the client is submitting request to peer in their organization
        // This is to ensure that a client from another org doesn't attempt to read or
        // write private data from this peer.
        await this.verifyClientOrgMatchesPeerOrg(ctx);
        
        const asset = {
            objectType: assetInput.objectType,
            color: assetInput.color,
            assetID: assetInput.assetID,
            size: assetInput.size,
            owner: ClientID
        };

        // Save asset to private data collection
        // Typical logger, logs to stdout/file in the fabric managed docker container, running this chaincode
        // Look for container name like dev-peer0.org1.example.com-{chaincodename_version}-xyz
        console.log(`CreateAsset Put: collection ${assetCollection}, ID ${assetInput.assetID}, owner ${ClientID}`);
        try {
            await ctx.stub.putPrivateData(assetCollection, assetInput.assetID, Buffer.from(stringify(sortKeysRecursive(asset))))
        } catch (error) {
            throw Error('Failed to put asset into private data collecton.')
        }

	    // Save asset details to collection visible to owning organization
        const assetPrivateDetails = {
            ID:             assetInput.assetID,
            AppraisedValue: assetInput.appraisedValue,
        }

        const orgCollection = await this.getCollectionName(ctx);

        // Put asset appraised value into owners org specific private data collection
        await ctx.stub.putPrivateData(orgCollection, assetInput.assetID, Buffer.from(stringify(sortKeysRecursive(assetPrivateDetails))));

    }

    // GetAssetByRange performs a range query based on the start and end keys provided. Range
    // queries can be used to read data from private data collections, but can not be used in
    // a transaction that also writes to private data.
    async GetAssetByRange(ctx, startKey, endKey){
        const response = await ctx.stub.getPrivateDataByRange(assetCollection, startKey, endKey);
        const promiseOfIterator = response.iterator.response.results;

        const allResults = [];
        for await (const res of promiseOfIterator) {
            allResults.push(res.resultBytes.toString());
        }

        return allResults;
    }

    // ReadAssetPrivateDetails reads the asset private details in organization specific collection
    async ReadAssetPrivateDetails(ctx, collection, assetID){
        const assetDetailsJSON = await ctx.stub.getPrivateData(collection, assetID);

        if(!assetDetailsJSON.toString()){
            throw Error('Failed to read asset details.')
        }
        return assetDetailsJSON.toString();
    }

    // TransferAsset transfers the asset to the new owner by setting a new owner ID
    async TransferAsset (ctx){

        const transientMap = await ctx.stub.getTransient();

        // Asset properties are private, therefore they get passed in transient field
        const transientTransferJSON = transientMap.get("asset_owner");
        if (!transientTransferJSON) {
            throw new Error(`The asset owner not found in the transient map`);
        }
        const assetTransferInput = {
            ID: JSON.parse(transientTransferJSON).assetID,
            BuyerMSP: JSON.parse(transientTransferJSON).buyerMSP
        }

        if (!assetTransferInput.ID && assetTransferInput.ID === "") {
            throw new Error('The assetID field is required, it must be a non-empty string.');
        }

        if (!assetTransferInput.BuyerMSP && assetTransferInput.BuyerMSP === "") {
            throw new Error('The buyerMSP field is required, it must be a non-empty string.');
        }

        // Read asset from the private data collection
        let asset = await this.ReadAsset(ctx, assetTransferInput.ID);

        if(!asset){
            throw new Error(`${assetTransferInput.ID} does not exist.`);
        }

        // Verify that the client is submitting request to peer in their organization
        await this.verifyClientOrgMatchesPeerOrg(ctx);

        // Verify transfer details and transfer owner
        await this.verifyAgreement(ctx, assetTransferInput.ID, asset.owner, assetTransferInput.BuyerMSP);

        const transferAgreement = await this.ReadTransferAgreement(ctx, assetTransferInput.ID);

        if(!transferAgreement){
            throw new Error(`There has been no agreement related to this asset ${assetTransferInput.ID}.`);
        }

        if(!transferAgreement.BuyerID){
            throw new Error(`The BuyerID was not found in TransferAgreement for ${assetTransferInput.ID}.`);
        }

        // Transfer asset in private data collection to new owner
        console.log(asset);
        asset = JSON.parse(asset);
	    asset.owner = transferAgreement.BuyerID;

        console.log(asset);


        await ctx.stub.putPrivateData(assetCollection, assetTransferInput.ID, Buffer.from(stringify(sortKeysRecursive(asset))));

        // Get collection name for this organization
        const ownerCollection = await this.getCollectionName(ctx);

        // Delete the asset appraised value from this organization's private data collection
        await ctx.stub.deletePrivateData(ownerCollection, assetTransferInput.ID);

	    // Delete the transfer agreement from the asset collection
        let transferAgreeKey = await ctx.stub.createCompositeKey(transferAgreementObjectType, [assetTransferInput.ID]);

        await ctx.stub.deletePrivateData(assetCollection, transferAgreeKey);

    }

    // ReadAsset reads the information from collection
    async ReadAsset(ctx, assetID) {
        const assetJSON = await ctx.stub.getPrivateData(assetCollection, assetID);
        const asset = assetJSON.toString();

        //No Asset found, return empty response
        if (!asset) {
            throw new Error(`${assetID} does not exist in collection ${assetCollection}.`);
        }

        return asset;
    }

    // AgreeToTransfer is used by the potential buyer of the asset to agree to the
    // asset value. The agreed to appraisal value is stored in the buying orgs
    // org specifc collection, while the the buyer client ID is stored in the asset collection
    // using a composite key
    async AgreeToTransfer(ctx){

        // Get ID of submitting client identity
        const ClientID = await this.submittingClientIdentity(ctx);

	    // Value is private, therefore it gets passed in transient field
        const transientMap = await ctx.stub.getTransient();

	    // Persist the JSON bytes as-is so that there is no risk of nondeterministic marshaling.
        const transientAssetJSON = transientMap.get("asset_value");

        if (!transientAssetJSON) {
            throw new Error(`The asset was not found in the transient map input.`);
        }

        let valueJSON = JSON.parse(transientAssetJSON);

        // Do some error checking since we get the chance
        if (!valueJSON.assetID && valueJSON.assetID === "") {
            throw new Error(`assetID field must be a non-empty string`);
        }

        if (valueJSON.appraisedValue <= 0) {
            throw new Error(`AppraisedValue field must be a non-empty string`);
        }

        // Read asset from the private data collection
        const asset = await this.ReadAsset(ctx, valueJSON.assetID);
        if(!asset){
            throw new Error(`${assetTransferInput.ID} does not exist.`);
        }

        // Verify that the client is submitting request to peer in their organization
        await this.verifyClientOrgMatchesPeerOrg(ctx);

	    // Get collection name for this organization. Needs to be read by a member of the organization.
        const orgCollection = await this.getCollectionName(ctx);

        // Put agreed value in the org specifc private data collection
        await ctx.stub.putPrivateData(orgCollection, valueJSON.assetID, Buffer.from(stringify(sortKeysRecursive(valueJSON))))

        // Create agreeement that indicates which identity has agreed to purchase
	    // In a more realistic transfer scenario, a transfer agreement would be secured to ensure that it cannot
	    // be overwritten by another channel member.
        let transferAgreeKey = await ctx.stub.createCompositeKey(transferAgreementObjectType, [valueJSON.assetID])
    
        await ctx.stub.putPrivateData(assetCollection, transferAgreeKey, Buffer.from(stringify(sortKeysRecursive(ClientID))));
    }

    // DeleteTranferAgreement can be used by the buyer to withdraw a proposal from
    // the asset collection and from his own collection.
    async DeleteTransferAgreement(ctx){

        const transientMap = await ctx.stub.getTransient();
        if (!transientMap) {
            throw new Error(`error getting transient`);
        }

        // Asset properties are private, therefore they get passed in transient field
        const transientDeleteJSON = transientMap.get("agreement_delete");
        if (!transientDeleteJSON) {
            throw new Error(`Asset not found in the transient map input`);
        }

        let assetDelete = JSON.parse(transientDeleteJSON);
        if (!assetDelete.assetID && assetDelete.assetID === "") {
            throw new Error(`assetID field must be a non-empty string`);
        }

        // Verify that the client is submitting request to peer in their organization
        await this.verifyClientOrgMatchesPeerOrg(ctx);

        // Delete private details of agreement
        const orgCollection = await this.getCollectionName(ctx);

        let transferAgreeKey = await ctx.stub.createCompositeKey(transferAgreementObjectType, [assetDelete.assetID])

        const valAsBytes = await ctx.stub.getPrivateData(assetCollection, transferAgreeKey);

        if (!valAsBytes) {
            throw new Error(`Asset's transfer_agreement does not exist.`);
        }

        await ctx.stub.deletePrivateData(orgCollection, assetDelete.assetID);

        // Delete transfer agreement record
        await ctx.stub.deletePrivateData(assetCollection, transferAgreeKey);
    }

    // ReadTransferAgreement gets the buyer's identity from the transfer agreement from collection
    async ReadTransferAgreement(ctx, assetID){
        const transferAgreeKey = await ctx.stub.createCompositeKey(transferAgreementObjectType, [assetID]);
        const buyerIdentity = await ctx.stub.getPrivateData(assetCollection, transferAgreeKey);
        
        if(!buyerIdentity.toString()) {
            return Error(`TransferAgreement for ${assetID} does not exist.`)
        }

        const agreement = {
            ID: assetID,
            BuyerID: buyerIdentity.toString()
        }
        return agreement;
    }

    // DeleteAsset can be used by the owner of the asset to delete the asset
    async DeleteAsset(ctx){

        const transientMap = await ctx.stub.getTransient();
        if (!transientMap) {
            throw new Error(`error getting transient`);
        }
        // Asset properties are private, therefore they get passed in transient field
        const transientDeleteJSON = transientMap.get("asset_delete");

        if (!transientDeleteJSON) {
            throw new Error(`asset not found in the transient map input`);
        }

        let assetDelete = JSON.parse(transientDeleteJSON);
        if (!assetDelete.assetID && assetDelete.assetID === "") {
            throw new Error(`assetID field must be a non-empty string`);
        }

        // Verify that the client is submitting request to peer in their organization
	    await this.verifyClientOrgMatchesPeerOrg(ctx);

        let assetAsBytes = await ctx.stub.getPrivateData(assetCollection, assetDelete.assetID);
        if (assetAsBytes == '') {
            throw new Error(`The asset not found`);
        }

        const ownerCollection = await this.getCollectionName(ctx);

        assetAsBytes = await ctx.stub.getPrivateData(ownerCollection, assetDelete.assetID);
        if (assetAsBytes == '') {
            throw new Error(`The asset not found in owner s private collection.`);
        }

        // delete the asset from state
        await ctx.stub.deletePrivateData(assetCollection, assetDelete.assetID)

        // Finally, delete private details of asset
        await ctx.stub.deletePrivateData(ownerCollection, assetDelete.assetID)

    }

    // submittingClientIdentity is an internal function to get client identity who submit the transaction.
    async submittingClientIdentity(ctx){
        const ClientID = await ctx.clientIdentity.getID();
        if (!ClientID && ClientID === '') {
            throw new Error(`Failed to read clientID`);
        }
        return ClientID;
    }

    // verifyClientOrgMatchesPeerOrg is an internal function used verify client org id and matches peer org id.
    async verifyClientOrgMatchesPeerOrg(ctx){

        const ClientMSPID = await ctx.clientIdentity.getMSPID();
        if (!ClientMSPID && ClientMSPID === '') {
            throw new Error("Failed getting the client's MSPID.");
        }

        const peerMSPID = await ctx.stub.getMspID();
        if (!peerMSPID && peerMSPID === '') {
            throw new Error("Failed getting the peer's MSPID.");
        }

        if (ClientMSPID !== peerMSPID) {
            throw new Error(`Client from org ${ClientMSPID} is not authorized to read or write private data from an org ${peerMSPID} peer.`);
        }
    }

    // getCollectionName is an internal helper function to get collection of submitting client identity.
    async getCollectionName(ctx){
        const ClientMSPID = await ctx.clientIdentity.getMSPID();
        if (!ClientMSPID && ClientMSPID === '') {
            throw new Error("Failed getting the client's MSPID.");
        }
        // Create the collection name
        const orgCollection = ClientMSPID + "PrivateCollection";
        return orgCollection;
    }

    // verifyAgreement is an internal helper function used by TransferAsset to verify
    // that the transfer is being initiated by the owner and that the buyer has agreed
    // to the same appraisal value as the owner
    async verifyAgreement(ctx, assetID, owner, buyerMSP){

        // Check 1: verify that the transfer is being initiatied by the owner

        // Get ID of submitting client identity
        const ClientID = await this.submittingClientIdentity(ctx);
        if (ClientID !== owner) {
            return Error("Error: Submitting client identity does not own the asset.")
        }

        // Check 2: verify that the buyer has agreed to the appraised value

	    // Get collection names
        const collectionOwner = await this.getCollectionName(ctx); // get owner collection from caller identity

        const collectionBuyer = buyerMSP + "PrivateCollection"; // get buyers collection

        // Get hash of owners agreed to value
        const ownerAppraisedValueHash = await ctx.stub.getPrivateDataHash(collectionOwner, assetID);
    
        if (!ownerAppraisedValueHash) {
            throw Error(`Hash of appraised value for ${assetID} does not exist in collection ${collectionOwner}.`)
        }

        // Get hash of buyers agreed to value
        const buyerAppraisedValueHash = await ctx.stub.getPrivateDataHash(collectionBuyer, assetID);

        if (!buyerAppraisedValueHash) {
            throw Error(`Hash of appraised value for ${assetID} does not exist in collection ${collectionOwner}.`)
        }

        console.log("collectionOwner", collectionOwner);
        console.log("collectionBuyer", collectionBuyer);


        console.log("ownerAppraisedValueHash", ownerAppraisedValueHash);
        console.log("buyerAppraisedValueHash", buyerAppraisedValueHash);

        // Verify that the two hashes match
        if (ownerAppraisedValueHash !== buyerAppraisedValueHash) {
            throw new Error(`Hash for the appraised value for owner ${ownerAppraisedValueHash} does not match the value for seller which is ${buyerAppraisedValueHash}.`);
        }

    }

}

module.exports = PrivateAssetTransfer;
