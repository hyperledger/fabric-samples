/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { connect } from '@hyperledger/fabric-gateway';
import crpto from 'crypto';
import { newGrpcConnection, newIdentity, newSigner, tlsCertPathOrg1, peerEndpointOrg1, peerNameOrg1, certPathOrg1, mspIdOrg1, keyDirectoryPathOrg1, tlsCertPathOrg2, peerEndpointOrg2, peerNameOrg2, certPathOrg2, mspIdOrg2, keyDirectoryPathOrg2 } from './connect';
import { AssetPriceJSON, AssetPropertiesJSON, GREEN, RED, RESET } from './utils';
import { ContractWrapper } from './contractWrapper';

const channelName = 'mychannel';
const chaincodeName = 'secured';

//Use a random key so that we can run multiple times
const now = Date.now();
const assetKey = `asset${now}`;

//Generate random bytes using crypto
const randomBytes = crpto.randomBytes(256).toString('hex');

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
        const contractWrapperOrg1  = new ContractWrapper(contractOrg1);

        // Get the smart contract from the network for Org2.
        const contractOrg2 = gatewayOrg2.getNetwork(channelName).getContract(chaincodeName);
        const contractWrapperOrg2  = new ContractWrapper(contractOrg2);

        // Create an asset by organization Org1, this only requires the owning organization to endorse.
        await createAsset(contractWrapperOrg1, mspIdOrg1);

        // Read the public details by org1.
        await readAsset(contractWrapperOrg1, assetKey, mspIdOrg1, mspIdOrg1);

        // Read the public details by org2.
        await readAsset(contractWrapperOrg2, assetKey, mspIdOrg1, mspIdOrg2);

        // Org1 should be able to read the private data details of the asset.
        await readPrivateAsset(contractWrapperOrg1, assetKey, mspIdOrg1);

        // Org2 is not the owner and does not have the private details, read expected to fail.
        await readPrivateAsset(contractWrapperOrg2, assetKey, mspIdOrg2);

        // Org1 updates the assets public description.
        await changePublicDescription(contractWrapperOrg1, assetKey, mspIdOrg1, `Asset ${assetKey} owned by ${mspIdOrg1} is for sale`);

        // Read the public details by org1.
        await readAsset(contractWrapperOrg1, assetKey, mspIdOrg1, mspIdOrg1);

        // Read the public details by org2.
        await readAsset(contractWrapperOrg2, assetKey, mspIdOrg1, mspIdOrg2);

        // This is an update to the public state and requires the owner(Org1) to endorse and sent by the owner org client (Org1).
        // Since the client is from Org2, which is not the owner, this will fail
        await changePublicDescription(contractWrapperOrg2, assetKey, mspIdOrg2, `Asset ${assetKey} owned by ${mspIdOrg2} is NOT for sale`);

        // Read the public details by org1.
        await readAsset(contractWrapperOrg1, assetKey, mspIdOrg1, mspIdOrg1);

        // Read the public details by org2.
        await readAsset(contractWrapperOrg2, assetKey, mspIdOrg1, mspIdOrg2);

        // Agree to a sell by org1.
        await agreeToSell(contractWrapperOrg1, assetKey, mspIdOrg1, 110);

        // Check the private information about the asset from Org2. Org1 would have to send Org2 asset details,
        // so the hash of the details may be checked by the chaincode.
        await verifyAssetProperties(contractWrapperOrg2, assetKey, mspIdOrg2);

        // Agree to a buy by org2.
        await agreeToBuy(contractWrapperOrg2, assetKey, mspIdOrg2, 100);

        // Org1 should be able to read the sale price of this asset
        await readSalePrice(contractWrapperOrg1, assetKey, mspIdOrg1);

        // Org2 has not set a sale price and this should fail
        await readSalePrice(contractWrapperOrg2, assetKey, mspIdOrg2);

        // Org1 has not agreed to buy so this should fail
        await readBidPrice(contractWrapperOrg1, assetKey, mspIdOrg1);

        // Org2 should be able to see the price it has agreed
        await readBidPrice(contractWrapperOrg2, assetKey, mspIdOrg2);

        // Org1 will try to transfer the asset to Org2
        // This will fail due to the sell price and the bid price are not the same
        await transferAsset(contractWrapperOrg1, assetKey, mspIdOrg1, mspIdOrg2, 110);

        // Agree to a sell by Org1,the seller will agree to the bid price of Org2
        await agreeToSell(contractWrapperOrg1, assetKey, mspIdOrg1, 100);

        // Read the public details by  org1.
        await readAsset(contractWrapperOrg1, assetKey, mspIdOrg1, mspIdOrg1);

        // Read the public details by  org2.
        await readAsset(contractWrapperOrg2, assetKey, mspIdOrg1, mspIdOrg2);

        // Org1 should be able to read the private data details of the asset.
        await readPrivateAsset(contractWrapperOrg1, assetKey, mspIdOrg1,);

        // Org1 should be able to read the sale price of this asset
        await readSalePrice(contractWrapperOrg1, assetKey, mspIdOrg1);

        // Org2 should be able to see the price it has agreed
        await readBidPrice(contractWrapperOrg2, assetKey, mspIdOrg2);

        // Org2 user will try to transfer the asset to Org1
        // This will fail as the owner is Org1
        await transferAsset(contractWrapperOrg2, assetKey, mspIdOrg2, mspIdOrg2, 100);

        // Org1 will transfer the asset to Org2
        // This will now complete as the sell price and the bid price are the same
        await transferAsset(contractWrapperOrg1, assetKey, mspIdOrg1, mspIdOrg2, 100);

        // Read the public details by  org1.
        await readAsset(contractWrapperOrg1, assetKey, mspIdOrg2, mspIdOrg1);

        // Read the public details by  org2.
        await readAsset(contractWrapperOrg2, assetKey, mspIdOrg2, mspIdOrg2);

        // Org2 should be able to read the private data details of this asset
        await readPrivateAssetAfterTransfer(contractWrapperOrg2, assetKey, mspIdOrg2);

        // Org1 should not be able to read the private data details of this asset,expected to fail
        await readPrivateAssetAfterTransfer(contractWrapperOrg1, assetKey, mspIdOrg1);

        // This is an update to the public state and requires only the owner to endorse.
        // Org2 wants to indicate that the items is no longer for sale
        await changePublicDescriptionAfterTransfer(contractWrapperOrg2, assetKey, mspIdOrg2, `Asset ${assetKey} owned by ${mspIdOrg2} is NOT for sale`);

        // Read the public details by  org1.
        await readAsset(contractWrapperOrg1, assetKey, mspIdOrg2, mspIdOrg1);

        // Read the public details by  org2.
        await readAsset(contractWrapperOrg2, assetKey, mspIdOrg2, mspIdOrg2);

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

async function createAsset(contract: ContractWrapper, org: string): Promise<void> {
    console.log(`${GREEN}--> Submit Transaction: CreateAsset, ${assetKey} as ${org} - endorsed by Org1${RESET}`);

    await contract.createAsset(org, assetKey, { objectType: 'asset_properties', assetId: assetKey, color: 'blue', size: 35, salt: randomBytes });

    console.log(`*** Result: committed, asset ${assetKey} is owned by Org1`);
}

async function readAsset(contract: ContractWrapper, assetKey: string, ownerOrg: string, org: string): Promise<void> {

    console.log(`${GREEN}--> Evaluate Transactions: ReadAsset as ${org}, - ${assetKey} should be owned by ${ownerOrg}${RESET}`);
    try {
        const result = await contract.readAsset(assetKey);
        if (result.ownerOrg === ownerOrg) {
            console.log(`*** Result from ${org} - asset ${result.assetId} owned by ${result.ownerOrg} DESC:${result.publicDescription}`);
        } else {
            console.log(`${RED}*** Failed owner check from ${org} - asset ${result.assetId} owned by ${result.ownerOrg} DESC:${result.publicDescription}${RESET}`);
        }
    }catch (e) {
        console.log(`${RED}*** Failed evaluateTransaction readAsset - ${e}${RESET}`);
    }
}

async function readPrivateAsset(contract: ContractWrapper, assetKey: string, org: string): Promise<void> {
    try{
        console.log(`${GREEN}--> Evaluate Transaction: GetAssetPrivateProperties, - ${assetKey} from organization ${org}${RESET}`);
        if(org === mspIdOrg2){
            console.log(`${GREEN}* Expected to fail as ${org} is not the owner and does not have the private details${RESET}`);
        }
        const result = await contract.getAssetPrivateProperties(assetKey);
        console.log('*** Result:', result);
    }
    catch(e){
        console.log(`${RED}*** Failed evaluateTransaction readPrivateAsset: ${e}${RESET}`);
    }
}

async function changePublicDescription(contract: ContractWrapper, assetKey: string, org: string, description: string): Promise<void> {
    try {
        console.log(`${GREEN}--> Submit Transaction: ChangePublicDescription ${assetKey}, as ${org} - endorse by ${org}${RESET}`);
        if (org === mspIdOrg2) {
            console.log(`${GREEN}* Expected to fail as ${org} is not the owner${RESET}`);
        }
        await contract.changePublicDescription(assetKey, description);
        console.log(`*** Result: committed, asset ${assetKey} is now for sale by ${org}`);
    } catch (e) {
        console.log(`${RED}*** Failed: ChangePublicDescription - ${e}${RESET}`);
    }
}

async function agreeToSell(contract: ContractWrapper, assetKey: string, org: string, price: number): Promise<void> {
    try {
        console.log(`${GREEN}--> Submit Transaction: AgreeToSell, ${assetKey} as ${org} - endorsed by ${org}${RESET}`);

        await contract.agreeToSell({assetId:assetKey, price, tradeId:now.toString()});

        console.log(`*** Result: committed, ${org} has agreed to sell asset ${assetKey} for ${price}`);
    } catch (e) {
        console.log(`${RED}*** Failed: AgreeToSell - ${e}${RESET}`);
    }
}

async function verifyAssetProperties(contract: ContractWrapper, assetKey: string, org: string): Promise<void> {
    try {
        console.log(`${GREEN}--> Evalute: VerifyAssetProperties, ${assetKey} as ${org} - endorsed by ${org}${RESET}`);

        const result = await contract.verifyAssetProperties({objectType:'asset_properties', assetId:assetKey, color:'blue', size:35, salt:randomBytes}, org);

        if (result) {
            console.log(`*** Success VerifyAssetProperties, private information about asset ${assetKey} has been verified by ${org}`);
        } else {
            console.log(`*** Failed: VerifyAssetProperties, private information about asset ${assetKey} has not been verified by ${org}`);
        }
    } catch (e) {
        console.log(`${RED}*** Failed: VerifyAssetProperties - ${e}${RESET}`);
    }
}

async function agreeToBuy(contract: ContractWrapper, assetKey: string, org: string, price: number): Promise<void> {
    try {
        console.log(`${GREEN}--> Submit Transaction: AgreeToBuy, ${assetKey} as ${org} - endorsed by ${org}${RESET}`);

        await contract.agreeToBuy( {assetId:assetKey, price, tradeId: now.toString()});

        console.log(`*** Result: committed, ${org} has agreed to buy asset ${assetKey} for 100`);
    } catch (e) {
        console.log(`${RED}*** Failed: AgreeToBuy - ${e}${RESET}`);
    }
}

async function readSalePrice(contract: ContractWrapper, assetKey: string, org: string): Promise<void> {
    try {
        console.log(`${GREEN}--> Evaluate Transaction: GetAssetSalesPrice, - ${assetKey} from organization ${org}${RESET}`);
        if(org === mspIdOrg2){
            console.log(`${GREEN}* Expected to fail as ${org} has not set a sale price${RESET}`);
        }

        const result = await contract.getAssetSalesPrice(assetKey);

        console.log('*** Result: GetAssetSalesPrice', result);
    } catch (e) {
        console.log(`${RED}*** Failed evaluateTransaction GetAssetSalesPrice: ${e}${RESET}`);
    }
}

async function readBidPrice(contract: ContractWrapper, assetKey: string, org: string): Promise<void> {
    try{
        console.log(`${GREEN}--> Evaluate Transaction: GetAssetBidPrice, - ${assetKey} from organization ${org}${RESET}`);
        if(org === mspIdOrg1){
            console.log(`${GREEN}* Expected to fail as Org1 has not agreed to buy${RESET}`);
        }

        const result = await contract.getAssetBidPrice(assetKey);

        console.log('*** Result: GetAssetBidPrice', result);
    } catch (e) {
        console.log(`${RED}*** Failed evaluateTransaction GetAssetBidPrice: ${e}${RESET}`);
    }
}

async function transferAsset(contract: ContractWrapper, assetKey: string, org: string, buyerOrgID: string, price: number): Promise<void> {
    try {
        console.log(`${GREEN}--> Submit Transaction: TransferAsset, ${assetKey} as ${org} - endorsed by ${org}${RESET}`);
        if(org === mspIdOrg2){
            console.log(`${GREEN}* Expected to fail as the owner is Org1${RESET}`);
        }else if(price === 110){
            console.log(`${GREEN}* Expected to fail as sell price and the bid price are not the same${RESET}`);
        }

        const assetProperties: AssetPropertiesJSON =
        {   objectType:'asset_properties',
            assetId:assetKey,
            color:'blue',
            size:35,
            salt:randomBytes
        };
        const assetPrice: AssetPriceJSON = { assetId: assetKey, price, tradeId:now.toString()};
        await contract.transferAsset(buyerOrgID, assetProperties, assetPrice, [ mspIdOrg1, mspIdOrg2 ]);

        console.log(`${GREEN}*** Result: committed, ${org} has transfered the asset ${assetKey} to ${mspIdOrg2} ${RESET}`);
    } catch (e) {
        console.log(`${RED}*** Failed: TransferAsset - ${e}${RESET}`);
    }
}

async function readPrivateAssetAfterTransfer(contract: ContractWrapper, assetKey: string, org: string): Promise<void> {
    try{
        console.log(`${GREEN}--> Evaluate Transaction: GetAssetPrivateProperties, - ${assetKey} from organization ${org}${RESET}`);
        if(org === mspIdOrg1) {
            console.log(`${GREEN}* Expected to fail as ${org} is not the owner and does not have the private details${RESET}`);
        }

        const result = await contract.getAssetPrivateProperties(assetKey);

        console.log('*** Result:', result);
    }
    catch(e){
        console.log(`${RED}*** Failed evaluateTransaction readPrivateAsset: ${e}${RESET}`);
    }
}

async function changePublicDescriptionAfterTransfer(contract: ContractWrapper, assetKey: string, org: string, description: string): Promise<void> {
    try {
        console.log(`${GREEN}--> Submit Transaction: changePublicDescription ${assetKey}, as ${org} - endorse by ${org}${RESET}`);
        if(org === mspIdOrg1) {
            console.log(`${GREEN}* Expected to fail as ${org} is not the owner${RESET}`);
        }

        await contract.changePublicDescription(assetKey, description);

        console.log(`*** Result: committed, asset ${assetKey} is now for sale by ${org}`);
    } catch (e) {
        console.log(`${RED}*** Failed: changePublicDescription - ${e}${RESET}`);
    }
}
