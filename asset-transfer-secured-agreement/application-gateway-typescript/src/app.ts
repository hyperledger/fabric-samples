/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { connect } from '@hyperledger/fabric-gateway';

import { newGrpcConnection, newIdentity, newSigner, tlsCertPathOrg1, peerEndpointOrg1, peerNameOrg1, certPathOrg1, mspIdOrg1, keyDirectoryPathOrg1, tlsCertPathOrg2, peerEndpointOrg2, peerNameOrg2, certPathOrg2, mspIdOrg2, keyDirectoryPathOrg2 } from './connect';
import { ContractWrapper } from './contractWrapper';
import { RED, RESET } from './utils';

const channelName = 'mychannel';
const chaincodeName = 'secured';

//Use a random key so that we can run multiple times
const now = Date.now().toString();
let assetKey: string;

async function main(): Promise<void> {

    // The gRPC client connection from org1 should be shared by all Gateway connections to this endpoint.
    const clientOrg1 = await newGrpcConnection(
        tlsCertPathOrg1,
        peerEndpointOrg1,
        peerNameOrg1
    );

    const gatewayOrg1 = connect({
        client: clientOrg1,
        identity: await newIdentity(certPathOrg1, mspIdOrg1),
        signer: await newSigner(keyDirectoryPathOrg1),
    });

    // The gRPC client connection from org2 should be shared by all Gateway connections to this endpoint.
    const clientOrg2 = await newGrpcConnection(
        tlsCertPathOrg2,
        peerEndpointOrg2,
        peerNameOrg2
    );

    const gatewayOrg2 = connect({
        client: clientOrg2,
        identity: await newIdentity(certPathOrg2, mspIdOrg2),
        signer: await newSigner(keyDirectoryPathOrg2),
    });


    try {

        // Get the smart contract from the network for Org1.
        const contractOrg1 = gatewayOrg1.getNetwork(channelName).getContract(chaincodeName);
        const contractWrapperOrg1  = new ContractWrapper(contractOrg1, mspIdOrg1);

        // Get the smart contract from the network for Org2.
        const contractOrg2 = gatewayOrg2.getNetwork(channelName).getContract(chaincodeName);
        const contractWrapperOrg2  = new ContractWrapper(contractOrg2, mspIdOrg2);

        // Create an asset by organization Org1, this only requires the owning organization to endorse.
        assetKey = await contractWrapperOrg1.createAsset(mspIdOrg1,
            `Asset owned by ${mspIdOrg1} is not for sale`, { ObjectType: 'asset_properties', Color: 'blue', Size: 35 });

        // Read the public details by org1.
        await contractWrapperOrg1.readAsset(assetKey, mspIdOrg1);

        // Read the public details by org2.
        await contractWrapperOrg2.readAsset(assetKey, mspIdOrg1);

        // Org1 should be able to read the private data details of the asset.
        await contractWrapperOrg1.getAssetPrivateProperties(assetKey, mspIdOrg1);

        // Org2 is not the owner and does not have the private details, read expected to fail.
        try {
            await contractWrapperOrg2.getAssetPrivateProperties(assetKey, mspIdOrg1);
        } catch(e) {
            console.log(`${RED}*** Failed: getAssetPrivateProperties - ${e}${RESET}`);
        }

        // Org1 updates the assets public description.
        await contractWrapperOrg1.changePublicDescription({assetId: assetKey,
            ownerOrg: mspIdOrg1,
            publicDescription: `Asset ${assetKey} owned by ${mspIdOrg1} is for sale`});

        // Read the public details by org1.
        await contractWrapperOrg1.readAsset(assetKey, mspIdOrg1);

        // Read the public details by org2.
        await contractWrapperOrg2.readAsset(assetKey, mspIdOrg1);

        // This is an update to the public state and requires the owner(Org1) to endorse and sent by the owner org client (Org1).
        // Since the client is from Org2, which is not the owner, this will fail.
        try{
            await contractWrapperOrg2.changePublicDescription({assetId: assetKey,
                ownerOrg: mspIdOrg1,
                publicDescription: `Asset ${assetKey} owned by ${mspIdOrg2} is NOT for sale`});
        } catch(e) {
            console.log(`${RED}*** Failed: changePublicDescription - ${e}${RESET}`);
        }

        // Read the public details by org1.
        await contractWrapperOrg1.readAsset(assetKey, mspIdOrg1);

        // Read the public details by org2.
        await contractWrapperOrg2.readAsset(assetKey, mspIdOrg1);

        // Agree to a sell by org1.
        await contractWrapperOrg1.agreeToSell({
            assetId: assetKey,
            price: 110,
            tradeId: now,
        }, mspIdOrg2);

        // Check the private information about the asset from Org2. Org1 would have to send Org2 asset details,
        // so the hash of the details may be checked by the chaincode.
        await contractWrapperOrg2.verifyAssetProperties(assetKey, {color:'blue', size:35});

        // Agree to a buy by org2.
        await contractWrapperOrg2.agreeToBuy( {assetId: assetKey,
            price: 100,
            tradeId: now}, { ObjectType: 'asset_properties', Color: 'blue', Size: 35 });

        // Org1 should be able to read the sale price of this asset.
        await contractWrapperOrg1.getAssetSalesPrice(assetKey, mspIdOrg1);

        // Org2 has not set a sale price and this should fail.
        try{
            await contractWrapperOrg2.getAssetSalesPrice(assetKey, mspIdOrg1);
        } catch(e) {
            console.log(`${RED}*** Failed: getAssetSalesPrice - ${e}${RESET}`);
        }

        // Org1 has not agreed to buy so this should fail.
        try{
            await contractWrapperOrg1.getAssetBidPrice(assetKey, mspIdOrg2);
        } catch(e) {
            console.log(`${RED}*** Failed: getAssetBidPrice - ${e}${RESET}`);
        }
        // Org2 should be able to see the price it has agreed.
        await contractWrapperOrg2.getAssetBidPrice(assetKey, mspIdOrg2);

        // Org1 will try to transfer the asset to Org2
        // This will fail due to the sell price and the bid price are not the same.
        try{
            await contractWrapperOrg1.transferAsset({ assetId: assetKey, price: 110, tradeId: now}, mspIdOrg1, mspIdOrg2);
        } catch(e) {
            console.log(`${RED}*** Failed: transferAsset - ${e}${RESET}`);
        }
        // Agree to a sell by Org1, the seller will agree to the bid price of Org2.
        await contractWrapperOrg1.agreeToSell({assetId:assetKey, price:100, tradeId:now}, mspIdOrg2);

        // Read the public details by  org1.
        await contractWrapperOrg1.readAsset(assetKey, mspIdOrg1);

        // Read the public details by  org2.
        await contractWrapperOrg2.readAsset(assetKey, mspIdOrg1);

        // Org1 should be able to read the private data details of the asset.
        await contractWrapperOrg1.getAssetPrivateProperties(assetKey, mspIdOrg1);

        // Org1 should be able to read the sale price of this asset.
        await contractWrapperOrg1.getAssetSalesPrice(assetKey, mspIdOrg1);

        // Org2 should be able to see the price it has agreed.
        await contractWrapperOrg2.getAssetBidPrice(assetKey, mspIdOrg2);

        // Org2 user will try to transfer the asset to Org1.
        // This will fail as the owner is Org1.
        try{
            await contractWrapperOrg2.transferAsset({ assetId: assetKey, price: 100, tradeId: now}, mspIdOrg1, mspIdOrg2);
        } catch(e) {
            console.log(`${RED}*** Failed: transferAsset - ${e}${RESET}`);
        }

        // Org1 will transfer the asset to Org2.
        // This will now complete as the sell price and the bid price are the same.
        await contractWrapperOrg1.transferAsset({ assetId: assetKey, price: 100, tradeId: now}, mspIdOrg1, mspIdOrg2);

        // Read the public details by  org1.
        await contractWrapperOrg1.readAsset(assetKey, mspIdOrg2);

        // Read the public details by  org2.
        await contractWrapperOrg2.readAsset(assetKey, mspIdOrg2);

        // Org2 should be able to read the private data details of this asset.
        await contractWrapperOrg2.getAssetPrivateProperties(assetKey, mspIdOrg2);

        // Org1 should not be able to read the private data details of this asset, expected to fail.
        try{
            await contractWrapperOrg1.getAssetPrivateProperties(assetKey, mspIdOrg2);
        } catch(e) {
            console.log(`${RED}*** Failed: getAssetPrivateProperties - ${e}${RESET}`);
        }

        // This is an update to the public state and requires only the owner to endorse.
        // Org2 wants to indicate that the items is no longer for sale.
        await contractWrapperOrg2.changePublicDescription( {assetId: assetKey, ownerOrg: mspIdOrg2, publicDescription: `Asset ${assetKey} owned by ${mspIdOrg2} is NOT for sale`});

        // Read the public details by org1.
        await contractWrapperOrg1.readAsset(assetKey, mspIdOrg2);

        // Read the public details by org2.
        await contractWrapperOrg2.readAsset(assetKey, mspIdOrg2);

    } finally {
        gatewayOrg1.close();
        gatewayOrg2.close();
        clientOrg1.close();
        clientOrg2.close();
    }
}

main().catch(error => {
    console.error('******** FAILED to run the application:', error);
    process.exitCode = 1;
});
