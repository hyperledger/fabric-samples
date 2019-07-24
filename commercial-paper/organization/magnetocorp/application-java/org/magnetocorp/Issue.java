/*
SPDX-License-Identifier: Apache-2.0
*/

package org.magnetocorp;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;

import org.hyperledger.fabric.gateway.Contract;
import org.hyperledger.fabric.gateway.Gateway;
import org.hyperledger.fabric.gateway.GatewayException;
import org.hyperledger.fabric.gateway.Network;
import org.hyperledger.fabric.gateway.Wallet;
import org.papernet.CommercialPaper;

public class Issue {

	public static void main(String[] args) {
		Gateway.Builder builder = Gateway.createBuilder();

		try {
			// A wallet stores a collection of identities
			Path walletPath = Paths.get("organization", "magnetocorp", "identity", "user", "isabella", "wallet");
			Wallet wallet = Wallet.createFileSystemWallet(walletPath);

			String userName = "User1@org1.example.com";

			Path connectionProfile = Paths.get("organization", "magnetocorp", "gateway", "networkConnection.yaml");

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

			    // Issue commercial paper
				System.out.println("Submit commercial paper issue transaction.");
				byte[] response = contract.submitTransaction("issue", "MagnetoCorp", "00001", "2020-05-31", "2020-11-30", "5000000");

				// Process response
				System.out.println("Process issue transaction response.");
				CommercialPaper paper = CommercialPaper.create(response);
				System.out.println(paper);
			}
		} catch (GatewayException | IOException | TimeoutException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
