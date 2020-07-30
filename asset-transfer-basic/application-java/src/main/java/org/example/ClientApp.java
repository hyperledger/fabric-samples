/*
SPDX-License-Identifier: Apache-2.0
*/

package org.example;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.hyperledger.fabric.gateway.Contract;
import org.hyperledger.fabric.gateway.Gateway;
import org.hyperledger.fabric.gateway.Network;
import org.hyperledger.fabric.gateway.Wallet;
import org.hyperledger.fabric.gateway.Wallets;

public class ClientApp {

	static {
		System.setProperty("org.hyperledger.fabric.sdk.service_discovery.as_localhost", "true");
	}

	// helper function for getting connected to the gateway
	public static Gateway Connect() throws Exception{
		// Load a file system based wallet for managing identities.
		Path walletPath = Paths.get("wallet");
		Wallet wallet = Wallets.newFileSystemWallet(walletPath);
		// load a CCP
		Path networkConfigPath = Paths.get("..", "..", "test-network", "organizations", "peerOrganizations", "org1.example.com", "connection-org1.yaml");

		Gateway.Builder builder = Gateway.createBuilder();
		builder.identity(wallet, "appUser").networkConfig(networkConfigPath).discovery(true);
		return builder.connect();

	}

	// Connects to the chaincode and calls InitLedger()
	public static void main(String[] args) throws Exception {

		// create a gateway connection
		try (Gateway gateway = Connect()) {

			// get the network and contract
			Network network = gateway.getNetwork("mychannel");
			Contract contract = network.getContract("basic");

			byte[] result;

			contract.submitTransaction("InitLedger");
			System.out.println("Ledger has been initialized with 6 assets");
			gateway.close();
		}
		catch(Exception e){
			System.out.println("Error connecting to gateway: " + e);
		}
	}

	// Connects to the chaincode and calls CreateAsset()
	public static void CreateAsset(final String assetID, final String color, final String size,
        final String owner, final String appraisedValue) throws Exception {

		// create a gateway connection
		try (Gateway gateway = Connect()) {

			// get the network and contract
			Network network = gateway.getNetwork("mychannel");
			Contract contract = network.getContract("basic");

			contract.submitTransaction("CreateAsset", assetID, color, size, owner, appraisedValue);
			System.out.println("Created asset: [assetID=" + assetID + ", color="+ color + ", size=" + size 
								+ ", owner=" + owner + ", appraisedValue=" + appraisedValue +"]");
			gateway.close();
		}
		catch(Exception e){
			System.out.println("Error connecting to gateway: " + e);
		}
	}

	public static void ReadAsset(final String assetID) throws Exception {
		// create a gateway connection
		try (Gateway gateway = Connect()) {

			// get the network and contract
			Network network = gateway.getNetwork("mychannel");
			Contract contract = network.getContract("basic");

			byte[] result;

			result = contract.evaluateTransaction("ReadAsset", assetID);
			System.out.println(new String(result));
			gateway.close();
		}
		catch(Exception e){
			System.out.println("Error connecting to gateway: " + e);
		}
	}

	// Connects to the chaincode and calls CreateAsset()
	public static void UpdateAsset(final String assetID, final String color, final String size,
        final String owner, final String appraisedValue) throws Exception {

		// create a gateway connection
		try (Gateway gateway = Connect()) {

			// get the network and contract
			Network network = gateway.getNetwork("mychannel");
			Contract contract = network.getContract("basic");

			contract.submitTransaction("UpdateAsset", assetID, color, size, owner, appraisedValue);
			System.out.println("Updated asset: [assetID=" + assetID + ", color="+ color + ", size=" + size 
					+ ", owner=" + owner + ", appraisedValue=" + appraisedValue +"]");
			gateway.close();
		}
		catch(Exception e){
			System.out.println("Error connecting to gateway: " + e);
		}
	}

	// Connects to the chaincode and calls CreateAsset()
	public static void DeleteAsset(final String assetID) throws Exception {

		// create a gateway connection
		try (Gateway gateway = Connect()) {

			// get the network and contract
			Network network = gateway.getNetwork("mychannel");
			Contract contract = network.getContract("basic");

			contract.submitTransaction("DeleteAsset", assetID);
			System.out.println("Deleted: " + assetID);
			gateway.close();
		}
		catch(Exception e){
			System.out.println("Error connecting to gateway: " + e);
		}
	}

	// Connects to the chaincode and calls CreateAsset()
	public static void TransferAsset(final String assetID, final String newOwner) throws Exception {

		// create a gateway connection
		try (Gateway gateway = Connect()) {

			// get the network and contract
			Network network = gateway.getNetwork("mychannel");
			Contract contract = network.getContract("basic");

			byte[] result;

			result = contract.submitTransaction("TransferAsset", assetID, newOwner);
			System.out.println(new String(result));
			System.out.println("Transferred " + assetID + " to " + newOwner);
			gateway.close();
		}
		catch(Exception e){
			System.out.println("Error connecting to gateway: " + e);
		}
	}

	public static void GetAllAssets() throws Exception {
		// create a gateway connection
		try (Gateway gateway = Connect()) {

			// get the network and contract
			Network network = gateway.getNetwork("mychannel");
			Contract contract = network.getContract("basic");

			byte[] result;

			result = contract.evaluateTransaction("GetAllAssets");
			System.out.println(new String(result));
			gateway.close();
		}
		catch(Exception e){
			System.out.println("Error connecting to gateway: " + e);
		}
	}
}
