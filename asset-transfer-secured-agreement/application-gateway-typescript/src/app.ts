/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { connect, Contract} from '@hyperledger/fabric-gateway';
import { TextDecoder } from 'util';
import crpto from 'crypto';
import { newGrpcConnection,newIdentity,newSigner,tlsCertPathOrg1,peerEndpointOrg1,peerNameOrg1,certPathOrg1,mspIdOrg1,keyDirectoryPathOrg1,tlsCertPathOrg2,peerEndpointOrg2,peerNameOrg2,certPathOrg2,mspIdOrg2,keyDirectoryPathOrg2} from './connect'

const utf8Decoder = new TextDecoder();

const RED = '\x1b[31m\n';
const GREEN = '\x1b[32m\n';
const RESET = '\x1b[0m';

const channelName = 'mychannel';
const chaincodeName = 'secured';

//Use a random key so that we can run multiple times
const now = Date.now();
const assetKey = `asset${now}`;

//Generate random bytes using crypto
const randomBytes = crpto.randomBytes(256).toString('hex')

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

        // Get the smart contract from the network for Org2.
        const contractOrg2 = gatewayOrg2.getNetwork(channelName).getContract(chaincodeName);

        // Create an asset by organization Org1, this only requires the owning organization to endorse.
        await createAsset(contractOrg1,mspIdOrg1);

        // Read the public details by  org1.
        await readAsset(assetKey, mspIdOrg1, contractOrg1,mspIdOrg1);

        // Read the public details by  org2.
        await readAsset(assetKey, mspIdOrg1, contractOrg2,mspIdOrg2);

        // Org1 should be able to read the private data details of the asset.
        await readPrivateAsset(assetKey,mspIdOrg1,contractOrg1)

        // Org2 is not the owner and does not have the private details, read expected to fail.
        await readPrivateAsset(assetKey,mspIdOrg2,contractOrg2)

        // Org1 updates the assets public description.
        await changePublicDescription(assetKey,mspIdOrg1,`Asset ${assetKey} owned by ${mspIdOrg1} is for sale`,contractOrg1)

        // Read the public details by  org1.
        await readAsset(assetKey, mspIdOrg1, contractOrg1,mspIdOrg1);

        // Read the public details by  org2.
        await readAsset(assetKey, mspIdOrg1, contractOrg2,mspIdOrg2);

        // This is an update to the public state and requires the owner(Org1) to endorse and sent by the owner org client (Org1).
        // Since the client is from Org2, which is not the owner, this will fail
        await changePublicDescription(assetKey,mspIdOrg2,`Asset ${assetKey} owned by ${mspIdOrg2} is NOT for sale`,contractOrg2);

        // Read the public details by  org1.
        await readAsset(assetKey, mspIdOrg1, contractOrg1,mspIdOrg1);

        // Read the public details by  org2.
        await readAsset(assetKey, mspIdOrg1, contractOrg2,mspIdOrg2);

        // Agree to a sell by org1.
        await agreeToSell(assetKey,mspIdOrg1,110,contractOrg1);

        // Check the private information about the asset from Org2. Org1 would have to send Org2 asset details,
        // so the hash of the details may be checked by the chaincode.
        await verifyAssetProperties(assetKey,mspIdOrg2,contractOrg2);

        // Agree to a buy by org2.
        await agreeToBuy(assetKey,mspIdOrg2,100,contractOrg2);

        // Org1 should be able to read the sale price of this asset
        await readSalePrice(assetKey, mspIdOrg1, contractOrg1);

        // Org2 has not set a sale price and this should fail
        await readSalePrice(assetKey, mspIdOrg2, contractOrg2);

        // Org1 has not agreed to buy so this should fail
        await readBidPrice(assetKey, mspIdOrg1, contractOrg1);

        // Org2 should be able to see the price it has agreed
        await readBidPrice(assetKey, mspIdOrg2, contractOrg2);

        // Org1 will try to transfer the asset to Org2
        // This will fail due to the sell price and the bid price are not the same
        await transferAsset(assetKey, mspIdOrg1,mspIdOrg2, 110,contractOrg1);

        // Agree to a sell by Org1,the seller will agree to the bid price of Org2
        await agreeToSell(assetKey,mspIdOrg1,100,contractOrg1);

        // Read the public details by  org1.
        await readAsset(assetKey, mspIdOrg1, contractOrg1,mspIdOrg1);

        // Read the public details by  org2.
        await readAsset(assetKey, mspIdOrg1, contractOrg2,mspIdOrg2);

        // Org1 should be able to read the private data details of the asset.
        await readPrivateAsset(assetKey,mspIdOrg1,contractOrg1)

        // Org1 should be able to read the sale price of this asset
        await readSalePrice(assetKey, mspIdOrg1, contractOrg1);

        // Org2 should be able to see the price it has agreed
        await readBidPrice(assetKey, mspIdOrg2, contractOrg2);

        // Org2 user will try to transfer the asset to Org1
        // This will fail as the owner is Org1
        await transferAsset(assetKey, mspIdOrg2,mspIdOrg2, 100,contractOrg2);

        // Org1 will transfer the asset to Org2
        // This will now complete as the sell price and the bid price are the same
        await transferAsset(assetKey, mspIdOrg1,mspIdOrg2, 100, contractOrg1);

        // Read the public details by  org1.
        await readAsset(assetKey, mspIdOrg2, contractOrg1,mspIdOrg1);

        // Read the public details by  org2.
        await readAsset(assetKey, mspIdOrg2, contractOrg2,mspIdOrg2);

        // Org2 should be able to read the private data details of this asset
        await readPrivateAssetAfterTransfer(assetKey, mspIdOrg2, contractOrg2);

        // Org1 should not be able to read the private data details of this asset,expected to fail
        await readPrivateAssetAfterTransfer(assetKey, mspIdOrg1, contractOrg1);

        // This is an update to the public state and requires only the owner to endorse.
        // Org2 wants to indicate that the items is no longer for sale
        await changePublicDescriptionAfterTransfer(assetKey,mspIdOrg2,`Asset ${assetKey} owned by ${mspIdOrg2} is NOT for sale`,contractOrg2);

        // Read the public details by  org1.
        await readAsset(assetKey, mspIdOrg2, contractOrg1,mspIdOrg1);

        // Read the public details by  org2.
        await readAsset(assetKey, mspIdOrg2, contractOrg2,mspIdOrg2);

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

async function createAsset(contract:Contract,org:string):Promise<void> {
    const asset_properties = {
        object_type: 'asset_properties',
        asset_id: assetKey,
        color: 'blue',
        size: 35,
        salt: randomBytes
    };
    console.log(`${GREEN}--> Submit Transaction: CreateAsset, ${assetKey} as ${org} - endorsed by Org1${RESET}`);

    await contract.submit('CreateAsset', {
        arguments:[assetKey,`Asset ${assetKey} owned by ${org} is not for sale`],
        transientData: { asset_properties: JSON.stringify(asset_properties) },
    });

    console.log(`*** Result: committed, asset ${assetKey} is owned by Org1`);
}

async function readAsset(assetKey:string, ownerOrg:string, contract:Contract,org:string):Promise<void> {
    console.log(`${GREEN}--> Evaluate Transactions: ReadAsset as ${org}, - ${assetKey} should be owned by ${ownerOrg}${RESET}`);

    const resultBytes = await contract.evaluateTransaction('ReadAsset', assetKey);

    const resultString = utf8Decoder.decode(resultBytes);
    if (resultString.length !== 0) {

        const result:{ objectType: string, assetID: string, ownerOrg: string, publicDescription: string } = JSON.parse(resultString);
        if (result?.ownerOrg === ownerOrg) {
            console.log(`*** Result from ${org} - asset ${result.assetID} owned by ${result.ownerOrg} DESC:${result.publicDescription}`);
        } else {
            console.log(`${RED}*** Failed owner check from ${org} - asset ${result.assetID} owned by ${result.ownerOrg} DESC:${result.publicDescription}${RESET}`);
        }
    }else{
        console.log(`${RED}*** Failed ReadAsset ${RESET}`);
    }
}

async function readPrivateAsset(assetKey:string, org:string, contract:Contract):Promise<void> {
    try{
        console.log(`${GREEN}--> Evaluate Transaction: GetAssetPrivateProperties, - ${assetKey} from organization ${org}${RESET}`);
        if(org === mspIdOrg2){
            console.log(`${GREEN}* Expected to fail as ${org} is not the owner and does not have the private details${RESET}`)
        }
        const resultBytes = await contract.evaluateTransaction('GetAssetPrivateProperties', assetKey);

        const resultString = utf8Decoder.decode(resultBytes);
        const result = JSON.parse(resultString);

        console.log('*** Result:', result);
    }
    catch(e){
        console.log(`${RED}*** Failed evaluateTransaction readPrivateAsset: ${e}${RESET}`);
    }
}

async function changePublicDescription(assetKey:string, org:string, description:string,contract:Contract):Promise<void> {
    try {
        console.log(`${GREEN}--> Submit Transaction: ChangePublicDescription ${assetKey}, as ${org} - endorse by ${org}${RESET}`);
        if(org===mspIdOrg2){
            console.log(`${GREEN}* Expected to fail as ${org} is not the owner${RESET}`)
        }

        await contract.submit('ChangePublicDescription', {
            arguments:[assetKey,description],
        });

        console.log(`*** Result: committed, asset ${assetKey} is now for sale by ${org}`);
    } catch (e) {
        console.log(`${RED}*** Failed: ChangePublicDescription - ${e}${RESET}`);
    }
}

async function agreeToSell(assetKey:string, org:string, price:number,contract:Contract):Promise<void> {
    try {
        const asset_price = {
            asset_id: assetKey,
            price:price,
            trade_id: now.toString()
        };
        console.log(`${GREEN}--> Submit Transaction: AgreeToSell, ${assetKey} as ${org} - endorsed by ${org}${RESET}`);

        await contract.submit('AgreeToSell',{
            arguments:[assetKey],
            transientData: { asset_price: JSON.stringify(asset_price) }
        });

        console.log(`*** Result: committed, ${org} has agreed to sell asset ${assetKey} for ${price}`);
    } catch (e) {
        console.log(`${RED}*** Failed: AgreeToSell - ${e}${RESET}`);
    }
}

async function verifyAssetProperties(assetKey:string, org:string, contract:Contract):Promise<void> {
    try {
        const asset_properties = {
            object_type: 'asset_properties',
            asset_id: assetKey,
            color: 'blue',
            size: 35,
            salt: randomBytes
        };
        console.log(`${GREEN}--> Evalute: VerifyAssetProperties, ${assetKey} as ${org} - endorsed by ${org}${RESET}`);

        const resultBytes = await contract.evaluate('VerifyAssetProperties',{
            arguments:[assetKey],
            transientData: { asset_properties: JSON.stringify(asset_properties) },
        });

        const resultString = utf8Decoder.decode(resultBytes);

        if (resultString.length !==0) {
            const result = JSON.parse(resultString);
            if (result) {
                console.log(`*** Success VerifyAssetProperties, private information about asset ${assetKey} has been verified by ${org}`);
            } else {
                console.log(`*** Failed: VerifyAssetProperties, private information about asset ${assetKey} has not been verified by ${org}`);
            }
        } else {
            console.log(`*** Failed: VerifyAssetProperties, private information about asset ${assetKey} has not been verified by ${org}`);
        }
    } catch (e) {
        console.log(`${RED}*** Failed: VerifyAssetProperties - ${e}${RESET}`);
    }
}

async function agreeToBuy(assetKey:string, org:string, price:number, contract:Contract):Promise<void> {
    try {
        const asset_price = {
            asset_id: assetKey,
            price:price,
            trade_id: now.toString()
        };
        console.log(`${GREEN}--> Submit Transaction: AgreeToBuy, ${assetKey} as ${org} - endorsed by ${org}${RESET}`);

        await contract.submit('AgreeToBuy',{
            arguments:[assetKey],
            transientData: { asset_price: JSON.stringify(asset_price) }
        });

        console.log(`*** Result: committed, ${org} has agreed to buy asset ${assetKey} for 100`);
    } catch (e) {
        console.log(`${RED}*** Failed: AgreeToBuy - ${e}${RESET}`);
    }
}

async function readSalePrice(assetKey:string, org:string, contract:Contract):Promise<void> {
    try {
        console.log(`${GREEN}--> Evaluate Transaction: GetAssetSalesPrice, - ${assetKey} from organization ${org}${RESET}`);
        if(org===mspIdOrg2){
            console.log(`${GREEN}* Expected to fail as ${org} has not set a sale price${RESET}`)
        }

        const resultBytes = await contract.evaluateTransaction('GetAssetSalesPrice', assetKey);

        const resultString = utf8Decoder.decode(resultBytes);
        const result = JSON.parse(resultString);
        console.log('*** Result: GetAssetSalesPrice', result);
    } catch (e) {
        console.log(`${RED}*** Failed evaluateTransaction GetAssetSalesPrice: ${e}${RESET}`);
    }
}

async function readBidPrice(assetKey:string, org:string, contract:Contract):Promise<void> {
    try{
        console.log(`${GREEN}--> Evaluate Transaction: GetAssetBidPrice, - ${assetKey} from organization ${org}${RESET}`);
        if(org===mspIdOrg1){
            console.log(`${GREEN}* Expected to fail as Org1 has not agreed to buy${RESET}`)
        }

        const resultBytes = await contract.evaluateTransaction('GetAssetBidPrice', assetKey);

        const resultString = utf8Decoder.decode(resultBytes);
        const result = JSON.parse(resultString);

        console.log('*** Result: GetAssetBidPrice', result);
    } catch (e) {
        console.log(`${RED}*** Failed evaluateTransaction GetAssetBidPrice: ${e}${RESET}`);
    }
}

async function transferAsset(assetKey:string, org:string,buyerOrgID:string, price:number,contract:Contract):Promise<void> {
    try{
        const asset_properties = {
            object_type: 'asset_properties',
            asset_id: assetKey,
            color: 'blue',
            size: 35,
            salt: randomBytes
        };
        const asset_price = {
            asset_id: assetKey,
            price:price,
            trade_id: now.toString()
        };

        console.log(`${GREEN}--> Submit Transaction: TransferAsset, ${assetKey} as ${org} - endorsed by ${org}${RESET}`);
        if(org === mspIdOrg2){
            console.log(`${GREEN}* Expected to fail as the owner is Org1${RESET}`)
        }else if(price === 110){
            console.log(`${GREEN}* Expected to fail as sell price and the bid price are not the same${RESET}`)
        }

        await contract.submit('TransferAsset', {
            arguments:[assetKey,buyerOrgID],
            transientData: {
                asset_properties: JSON.stringify(asset_properties),
                asset_price: JSON.stringify(asset_price) },
            endorsingOrganizations:[mspIdOrg1,mspIdOrg2]
        });

        console.log(`${GREEN}*** Result: committed, ${org} has transfered the asset ${assetKey} to ${mspIdOrg2} ${RESET}`);
    } catch (e) {
        console.log(`${RED}*** Failed: TransferAsset - ${e}${RESET}`);
    }
}

async function readPrivateAssetAfterTransfer(assetKey:string, org:string, contract:Contract):Promise<void> {
    try{
        console.log(`${GREEN}--> Evaluate Transaction: GetAssetPrivateProperties, - ${assetKey} from organization ${org}${RESET}`);
        if(org === mspIdOrg1){
            console.log(`${GREEN}* Expected to fail as ${org} is not the owner and does not have the private details${RESET}`)
        }
        const resultBytes = await contract.evaluateTransaction('GetAssetPrivateProperties', assetKey);

        const resultString = utf8Decoder.decode(resultBytes);
        const result = JSON.parse(resultString);

        console.log('*** Result:', result);
    }
    catch(e){
        console.log(`${RED}*** Failed evaluateTransaction readPrivateAsset: ${e}${RESET}`);
    }
}

async function changePublicDescriptionAfterTransfer(assetKey:string, org:string, description:string,contract:Contract):Promise<void> {
    try {
        console.log(`${GREEN}--> Submit Transaction: changePublicDescription ${assetKey}, as ${org} - endorse by ${org}${RESET}`);
        if(org===mspIdOrg1){
            console.log(`${GREEN}* Expected to fail as ${org} is not the owner${RESET}`)
        }

        await contract.submit('ChangePublicDescription', {
            arguments:[assetKey,description],
        });

        console.log(`*** Result: committed, asset ${assetKey} is now for sale by ${org}`);
    } catch (e) {
        console.log(`${RED}*** Failed: changePublicDescription - ${e}${RESET}`);
    }
}