/*
SPDX-License-Identifier: Apache-2.0
*/

package org.magnetocorp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.hyperledger.fabric.gateway.GatewayException;
import org.hyperledger.fabric.gateway.Wallet;
import org.hyperledger.fabric.gateway.Wallet.Identity;

public class AddToWallet {

	public static void main(String[] args) {
		try {
			// A wallet stores a collection of identities
			Path walletPath = Paths.get("..", "identity", "user", "isabella", "wallet");
			Wallet wallet = Wallet.createFileSystemWallet(walletPath);

	        // Location of credentials to be stored in the wallet
			Path credentialPath = Paths.get("..", "..",".." ,".." ,"basic-network", "crypto-config",
					"peerOrganizations", "org1.example.com", "users", "User1@org1.example.com", "msp");
			Path certificatePem = credentialPath.resolve(Paths.get("signcerts",
					"User1@org1.example.com-cert.pem"));
			Path privateKey = credentialPath.resolve(Paths.get("keystore",
					"c75bd6911aca808941c3557ee7c97e90f3952e379497dc55eb903f31b50abc83_sk"));

		       // Load credentials into wallet
			String identityLabel = "User1@org1.example.com";
			Identity identity = Identity.createIdentity("Org1MSP", Files.newBufferedReader(certificatePem), Files.newBufferedReader(privateKey));

			wallet.put(identityLabel, identity);

		} catch (IOException e) {
			System.err.println("Error adding to wallet");
			e.printStackTrace();
		}
	}

}
