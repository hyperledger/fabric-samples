package org.example;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.Properties;
import java.util.Set;

import org.hyperledger.fabric.gateway.Contract;
import org.hyperledger.fabric.gateway.Gateway;
import org.hyperledger.fabric.gateway.Network;
import org.hyperledger.fabric.gateway.Wallet;
import org.hyperledger.fabric.gateway.Wallet.Identity;
import org.hyperledger.fabric.sdk.Enrollment;
import org.hyperledger.fabric.sdk.User;
import org.hyperledger.fabric.sdk.security.CryptoSuite;
import org.hyperledger.fabric.sdk.security.CryptoSuiteFactory;
import org.hyperledger.fabric_ca.sdk.EnrollmentRequest;
import org.hyperledger.fabric_ca.sdk.HFCAClient;
import org.hyperledger.fabric_ca.sdk.RegistrationRequest;

public class ClientApp {

	public static void main(String[] args) throws Exception {
	    // Create a new file system based wallet for managing identities.
		Path walletPath = Paths.get("wallet");
	    Wallet wallet = Wallet.createFileSystemWallet(walletPath);
	    loadWallet(wallet);

	    // load a CCP
	    Path networkConfigPath = Paths.get("..", "..", "first-network", "connection-org1.yaml");

	    Gateway.Builder builder = Gateway.createBuilder();
	    builder.identity(wallet, "user1").networkConfig(networkConfigPath).discovery(true);

	    // create a gateway connection
	    try (Gateway gateway = builder.connect()) {

	      // get the network and contract
	      Network network = gateway.getNetwork("mychannel");
	      Contract contract = network.getContract("fabcar");

	      byte[] result;

	      result = contract.evaluateTransaction("queryAllCars");
	      System.out.println(new String(result));

	      contract.submitTransaction("createCar", "CAR10", "VW", "Polo", "Grey", "Mary");

	      result = contract.evaluateTransaction("queryCar", "CAR10");
	      System.out.println(new String(result));

	      contract.submitTransaction("changeCarOwner", "CAR10", "Archie");

	      result = contract.evaluateTransaction("queryCar", "CAR10");
	      System.out.println(new String(result));

	    } catch (Exception ex) {
	      ex.printStackTrace();
	    }

	}

	private static void loadWallet(Wallet wallet) throws Exception {
    	// Create a CA client for interacting with the CA.
		Properties props = new Properties();
		props.put("pemFile", "../../first-network/crypto-config/peerOrganizations/org1.example.com/ca/ca.org1.example.com-cert.pem");
		props.put("allowAllHostNames", "true");
        HFCAClient caClient = HFCAClient.createNewInstance("https://localhost:7054", props);
        CryptoSuite cryptoSuite = CryptoSuiteFactory.getDefault().getCryptoSuite();
        caClient.setCryptoSuite(cryptoSuite);

	    enrollAdmin(wallet, caClient);
	    registerUser(wallet, caClient);

	}

	private static void enrollAdmin(Wallet wallet, HFCAClient caClient) throws Exception {
		// Check to see if we've already enrolled the admin user.
		boolean adminExists = wallet.exists("admin");
        if (adminExists) {
            System.out.println("An identity for the admin user \"admin\" already exists in the wallet");
            return;
        }

        // Enroll the admin user, and import the new identity into the wallet.
        final EnrollmentRequest enrollmentRequestTLS = new EnrollmentRequest();
        enrollmentRequestTLS.addHost("localhost");
        enrollmentRequestTLS.setProfile("tls");
        Enrollment enrollment = caClient.enroll("admin", "adminpw", enrollmentRequestTLS);
        Identity user = Identity.createIdentity("Org1MSP", enrollment.getCert(), enrollment.getKey());
        wallet.put("admin", user);
	}

	private static void registerUser(Wallet wallet, HFCAClient caClient) throws Exception {
		// Check to see if we've already enrolled the user.
		boolean userExists = wallet.exists("user1");
		if (userExists) {
			System.out.println("An identity for the user \"user1\" already exists in the wallet");
			return;
		}

		Identity adminIdentity = wallet.get("admin");
		User admin = new User() {

			@Override
			public String getName() {
				return "admin";
			}

			@Override
			public Set<String> getRoles() {
				return null;
			}

			@Override
			public String getAccount() {
				return null;
			}

			@Override
			public String getAffiliation() {
				return "org1.department1";
			}

			@Override
			public Enrollment getEnrollment() {
				return new Enrollment() {

					@Override
					public PrivateKey getKey() {
						return adminIdentity.getPrivateKey();
					}

					@Override
					public String getCert() {
						return adminIdentity.getCertificate();
					}
				};
			}

			@Override
			public String getMspId() {
				return "Org1MSP";
			}

		};

		// Register the user, enroll the user, and import the new identity into the wallet.
		RegistrationRequest registrationRequest = new RegistrationRequest("user1");
		registrationRequest.setAffiliation("org1.department1");
		registrationRequest.setEnrollmentID("user1");
		String enrollmentSecret = caClient.register(registrationRequest, admin);
		Enrollment enrollment = caClient.enroll("user1", enrollmentSecret);
		Identity user = Identity.createIdentity("Org1MSP", enrollment.getCert(), enrollment.getKey());
		wallet.put("user1", user);
		System.out.println("Successfully enrolled user \"user1\" and imported it into the wallet");

	}

}
