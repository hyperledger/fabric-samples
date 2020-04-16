/*
SPDX-License-Identifier: Apache-2.0
*/

package org.digibank;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.hyperledger.fabric.gateway.Contract;
import org.hyperledger.fabric.gateway.Gateway;
import org.hyperledger.fabric.gateway.GatewayException;
import org.hyperledger.fabric.gateway.Network;
import org.hyperledger.fabric.gateway.Wallet;
import org.hyperledger.fabric.gateway.Wallets;
import org.papernet.CommercialPaper;

public class Redeem {

	private static final String ENVKEY="CONTRACT_NAME";

	public static void main(String[] args) {
		Gateway.Builder builder = Gateway.createBuilder();

		String contractName="papercontract";
		// get the name of the contract, in case it is overridden
		Map<String,String> envvar = System.getenv();
		if (envvar.containsKey(ENVKEY)){
			contractName=envvar.get(ENVKEY);
		}

		try {
			// A wallet stores a collection of identities
			Path walletPath = Paths.get(".", "wallet");
			Wallet wallet = Wallets.newFileSystemWallet(walletPath);

			String userName = "User1@org1.example.com";

			Path connectionProfile = Paths.get("..", "gateway", "connection-org1.yaml");

			// Set connection options on the gateway builder
			builder.identity(wallet, userName).networkConfig(connectionProfile).discovery(false);

			// Connect to gateway using application specified parameters
			try(Gateway gateway = builder.connect()) {

				// Access PaperNet network
				System.out.println("Use network channel: mychannel.");
				Network network = gateway.getNetwork("mychannel");

				// Get addressability to commercial paper contract
				System.out.println("Use org.papernet.commercialpaper smart contract.");
				Contract contract = network.getContract("papercontract", "org.papernet.commercialpaper");

				// Redeem commercial paper
				System.out.println("Submit commercial paper redeem transaction.");
				byte[] response = contract.submitTransaction("redeem", "MagnetoCorp", "00001", "DigiBank", "2020-11-30");

				// Process response
				System.out.println("Process redeem transaction response.");
				CommercialPaper paper = CommercialPaper.deserialize(response);
				System.out.println(paper);
			}
		} catch (GatewayException | IOException | TimeoutException | InterruptedException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

}
