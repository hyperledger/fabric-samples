/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

// Running TestApp:
// gradle runApp

package application.java;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.hyperledger.fabric.gateway.Contract;
import org.hyperledger.fabric.gateway.Gateway;
import org.hyperledger.fabric.gateway.Network;
import org.hyperledger.fabric.gateway.Wallet;
import org.hyperledger.fabric.gateway.Wallets;


public class App {

	static {
		System.setProperty("org.hyperledger.fabric.sdk.service_discovery.as_localhost", "true");
	}

	// helper function for getting connected to the gateway
	public static Gateway connect() throws Exception{
		// Load a file system based wallet for managing identities.
		Path walletPath = Paths.get("wallet");
		Wallet wallet = Wallets.newFileSystemWallet(walletPath);
		// load a CCP
		Path networkConfigPath = Paths.get("..", "..", "test-network", "organizations", "peerOrganizations", "org1.example.com", "connection-org1.yaml");

		Gateway.Builder builder = Gateway.createBuilder();
		builder.identity(wallet, "appUser").networkConfig(networkConfigPath).discovery(true);
		return builder.connect();
	}

	public static void main(String[] args) throws Exception {
		// enrolls the admin and registers the user
		try {
			EnrollAdmin.main(null);
			RegisterUser.main(null);
		} catch (Exception e) {
			System.err.println(e);
		}

		// connect to the network and invoke the smart contract
		try (Gateway gateway = connect()) {

			// get the network and contract
			Network network = gateway.getNetwork("mychannel");
			Contract contract = network.getContract("ledger");

			byte[] result;

			System.out.println("Submit Transaction: InitLedger creates the initial set of assets on the ledger.");
			contract.submitTransaction("InitLedger");

			System.out.println("\n");
			// passing in 2 empty strings will query all the assets
			result = contract.evaluateTransaction("GetAssetsByRange", "", "");
			System.out.println("Evaluate Transaction: GetAssetsByRange, result: " + new String(result));

			System.out.println("\n");
			System.out.println("Submit Transaction: CreateAsset asset13");
			// CreateAsset creates an asset with ID asset13, color yellow, owner Tom, size 5 and appraisedValue of 1300
			contract.submitTransaction("CreateAsset", "asset13", "yellow", "5", "Tom", "1300");

			System.out.println("\n");
			System.out.println("Evaluate Transaction: ReadAsset asset13");
			// ReadAsset returns an asset with given assetID
			result = contract.evaluateTransaction("ReadAsset", "asset13");
			System.out.println("result: " + new String(result));

			System.out.println("\n");
			System.out.println("Evaluate Transaction: AssetExists asset1");
			// AssetExists returns "true" if an asset with given assetID exist
			result = contract.evaluateTransaction("AssetExists", "asset1");
			System.out.println("result: " + new String(result));

			System.out.println("\n");
			System.out.println("Submit Transaction: DeleteAsset asset1");
			contract.submitTransaction("DeleteAsset", "asset1");

			System.out.println("\n");
			System.out.println("Evaluate Transaction: AssetExists asset1");
			// AssetExists returns "true" if an asset with given assetID exist
			result = contract.evaluateTransaction("AssetExists", "asset1");
			System.out.println("result: " + new String(result));

			System.out.println("\n");
			System.out.println("Submit Transaction: TransferAsset asset2 from owner Tomoko > owner Tom");
			// TransferAsset transfers an asset with given ID to new owner Tom
			contract.submitTransaction("TransferAsset", "asset2", "Tom");

			// Rich Query with Pagination (Only supported if CouchDB is used as state database)
			System.out.println("\n");
			System.out.println("Evaluate Transaction:QueryAssetsWithPagination Tom's assets");
			result = contract.evaluateTransaction("QueryAssetsWithPagination","{\"selector\":{\"docType\":\"asset\",\"owner\":\"Tom\"}, \"use_index\":[\"_design/indexOwnerDoc\", \"indexOwner\"]}","3","");
			System.out.println("result: " + new String(result));

			System.out.println("\n");
			System.out.println("Submit Transaction: TransferAssetByColor yellow assets > newOwner Michel");
			contract.submitTransaction("TransferAssetByColor", "yellow", "Michel");

			// Rich Query (Only supported if CouchDB is used as state database):
			System.out.println("\n");
			System.out.println("Evaluate Transaction:QueryAssetsByOwner Michel");
			result = contract.evaluateTransaction("QueryAssetsByOwner", "Michel");
			System.out.println("result: " + new String(result));

			System.out.println("\n");
			System.out.println("Evaluate Transaction:GetAssetHistory asset13");
			result = contract.evaluateTransaction("GetAssetHistory", "asset13");
			System.out.println("result: " + new String(result));

			// Rich Query (Only supported if CouchDB is used as state database):
			System.out.println("\n");
			System.out.println("Evaluate Transaction:QueryAssets assets of size 15");
			result = contract.evaluateTransaction("QueryAssets", "{\"selector\":{\"size\":15}}");
			System.out.println("result: " + new String(result));

			// Rich Query with index design doc and index name specified (Only supported if CouchDB is used as state database):
			System.out.println("\n");
			System.out.println("Evaluate Transaction:QueryAssets Jin Soo's assets");
			result = contract.evaluateTransaction("QueryAssets","{\"selector\":{\"docType\":\"asset\",\"owner\":\"Jin Soo\"}, \"use_index\":[\"_design/indexOwnerDoc\", \"indexOwner\"]}");
			System.out.println("result: " + new String(result));

			// Range Query with Pagination
			System.out.println("\n");
			System.out.println("Evaluate Transaction:GetAssetsByRangeWithPagination assets 3-5");
			result = contract.evaluateTransaction("GetAssetsByRangeWithPagination", "asset3", "asset6", "3","");
			System.out.println("result: " + new String(result));
		}
		catch(Exception e){
			System.err.println(e);
			System.exit(1);
		}

	}
}
