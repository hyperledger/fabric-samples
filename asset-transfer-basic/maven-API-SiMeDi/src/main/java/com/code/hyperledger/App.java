package com.code.hyperledger;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.code.hyperledger.models.Receta;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import org.hyperledger.fabric.client.CommitException;
import org.hyperledger.fabric.client.CommitStatusException;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.EndorseException;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.GatewayException;
import org.hyperledger.fabric.client.SubmitException;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;
import org.springframework.context.annotation.Configuration;

import java.beans.BeanProperty;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


@SpringBootApplication
public class App {
	/*private static final String MSP_ID = System.getenv().getOrDefault("MSP_ID", "Org1MSP");
	private static final String CHANNEL_NAME = System.getenv().getOrDefault("CHANNEL_NAME", "mychannel");
	private static final String CHAINCODE_NAME = System.getenv().getOrDefault("CHAINCODE_NAME", "basic");

	// Path to crypto materials.
	private static final Path CRYPTO_PATH = Paths.get("../../test-network/organizations/peerOrganizations/org1.example.com");
	// Path to user certificate.
	private static final Path CERT_DIR_PATH = CRYPTO_PATH.resolve(Paths.get("users/User1@org1.example.com/msp/signcerts"));
	// Path to user private key directory.
	private static final Path KEY_DIR_PATH = CRYPTO_PATH.resolve(Paths.get("users/User1@org1.example.com/msp/keystore"));
	// Path to peer tls certificate.
	private static final Path TLS_CERT_PATH = CRYPTO_PATH.resolve(Paths.get("peers/peer0.org1.example.com/tls/ca.crt"));

	// Gateway peer end point.
	private static final String PEER_ENDPOINT = "localhost:7051";
	private static final String OVERRIDE_AUTH = "peer0.org1.example.com";

	private final Contract contract;
	private final String assetId = "asset" + Instant.now().toEpochMilli();
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();*/

	public static void main(final String[] args) throws Exception {
		/*System.out.println("\n--> ENTRANDO");

		// The gRPC client connection should be shared by all Gateway connections to
		// this endpoint.

		var channel = newGrpcConnection();

		var builder = Gateway.newInstance().identity(newIdentity()).signer(newSigner()).connection(channel)
				// Default timeouts for different gRPC calls
				.evaluateOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
				.endorseOptions(options -> options.withDeadlineAfter(15, TimeUnit.SECONDS))
				.submitOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
				.commitStatusOptions(options -> options.withDeadlineAfter(1, TimeUnit.MINUTES));

		try (var gateway = builder.connect()) {
			new App(gateway).run();
		} finally {
			channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
		}*/
        SpringApplication.run(App.class, args);

	}

	@Bean
	public WebMvcConfigurer corsConfigurer() {
		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				registry.addMapping("/**")
						.allowedOrigins("http://localhost:5173", "http://localhost:3005") // Cambia esto a tu dominio de origen
						.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
						.allowedHeaders("*")
						.allowCredentials(true);
			}
		};
	}	

	/*private static ManagedChannel newGrpcConnection() throws IOException {
		var credentials = TlsChannelCredentials.newBuilder()
				.trustManager(TLS_CERT_PATH.toFile())
				.build();
		return Grpc.newChannelBuilder(PEER_ENDPOINT, credentials)
				.overrideAuthority(OVERRIDE_AUTH)
				.build();
	}

	private static Identity newIdentity() throws IOException, CertificateException {
        try (var certReader = Files.newBufferedReader(getFirstFilePath(CERT_DIR_PATH))) {
            var certificate = Identities.readX509Certificate(certReader);
            return new X509Identity(MSP_ID, certificate);
        }
    }

    private static Signer newSigner() throws IOException, InvalidKeyException {
        try (var keyReader = Files.newBufferedReader(getFirstFilePath(KEY_DIR_PATH))) {
            var privateKey = Identities.readPrivateKey(keyReader);
            return Signers.newPrivateKeySigner(privateKey);
        }
    }

	private static Path getFirstFilePath(Path dirPath) throws IOException {
		try (var keyFiles = Files.list(dirPath)) {
			return keyFiles.findFirst().orElseThrow();
		}
	}

	public App(final Gateway gateway) {
		// Get a network instance representing the channel where the smart contract is
		// deployed.
		var network = gateway.getNetwork(CHANNEL_NAME);

		// Get the smart contract from the network.
		contract = network.getContract(CHAINCODE_NAME);
	}

	public void run() throws GatewayException, CommitException {
		// Initialize a set of asset data on the ledger using the chaincode 'InitLedger' function.
		initLedger();

		// Return all the current assets on the ledger.
		getAllAssets();

		// Create a new asset on the ledger.
		createAsset();

		// Update an existing asset asynchronously.
		transferAssetAsync();

		// Get the asset details by assetID.
		readAssetById();

		// Update an asset which does not exist.
		updateNonExistentAsset();
	}*/

	/**
	 * This type of transaction would typically only be run once by an application
	 * the first time it was started after its initial deployment. A new version of
	 * the chaincode deployed later would likely not need to run an "init" function.
	 */
	/*private void initLedger() throws EndorseException, SubmitException, CommitStatusException, CommitException {
		System.out.println("\n--> Submit Transaction: InitLedger, function creates the initial set of assets on the ledger");

		contract.submitTransaction("InitLedger");

		System.out.println("*** Transaction committed successfully");
	}*/

	/**
	 * Evaluate a transaction to query ledger state.
	 */
	/*private void getAllAssets() throws GatewayException {
		System.out.println("\n--> Evaluate Transaction: GetAllAssets, function returns all the current assets on the ledger");

		var result = contract.evaluateTransaction("GetAllAssets");

		System.out.println("*** Result: " + prettyJson(result));
	}

	private String prettyJson(final byte[] json) {
		return prettyJson(new String(json, StandardCharsets.UTF_8));
	}

	private String prettyJson(final String json) {
		var parsedJson = JsonParser.parseString(json);
		return gson.toJson(parsedJson);
	}*/

	/**
	 * Submit a transaction synchronously, blocking until it has been committed to
	 * the ledger.
	 */
	/*private void createAsset() throws EndorseException, SubmitException, CommitStatusException, CommitException {
		System.out.println("\n--> Submit Transaction: CreateAsset, creates new asset with all arguments");

		Receta receta = new Receta(
				assetId,
				"Tom",
				"presc456",
				"active",
				LocalDateTime.now(),
				"high",
				"Medicine XYZ",
				"Condition ABC",
				"Take with food",
				"30 days",
				"1 pill per day",
				"1 year",
				"12345678",
				LocalDate.now(),
				30,
				LocalDate.now()
		);

		contract.submitTransaction(
				"CreateAsset",
				receta.getId(),
				receta.getOwner(),
				receta.getPrescripcionAnteriorId(),
				receta.getStatus(),
				receta.getStatusChange().toString(),
				receta.getPrioridad(),
				receta.getMedicacion(),
				receta.getRazon(),
				receta.getNotas(),
				receta.getPeriodoDeTratamiento(),
				receta.getInstruccionesTratamiento(),
				receta.getPeriodoDeValidez(),
				receta.getDniPaciente(),
				receta.getFechaDeAutorizacion().toString(),
				Integer.toString(receta.getCantidad()),
				receta.getExpectedSupplyDuration().toString()
		);

		System.out.println("*** Transaction committed successfully");
	}*/

	/**
	 * Submit transaction asynchronously, allowing the application to process the
	 * smart contract response (e.g. update a UI) while waiting for the commit
	 * notification.
	 */
	/*private void transferAssetAsync() throws EndorseException, SubmitException, CommitStatusException {
		System.out.println("\n--> Async Submit Transaction: TransferAsset, updates existing asset owner");

		var commit = contract.newProposal("TransferAsset")
				.addArguments(assetId, "Saptha")
				.build()
				.endorse()
				.submitAsync();

		var result = commit.getResult();
		var oldOwner = new String(result, StandardCharsets.UTF_8);

		System.out.println("*** Successfully submitted transaction to transfer ownership from " + oldOwner + " to Saptha");
		System.out.println("*** Waiting for transaction commit");

		var status = commit.getStatus();
		if (!status.isSuccessful()) {
			throw new RuntimeException("Transaction " + status.getTransactionId() +
					" failed to commit with status code " + status.getCode());
		}

		System.out.println("*** Transaction committed successfully");
	}

	private void readAssetById() throws GatewayException {
		System.out.println("\n--> Evaluate Transaction: ReadAsset, function returns asset attributes");

		var evaluateResult = contract.evaluateTransaction("ReadAsset", assetId);

		System.out.println("*** Result:" + prettyJson(evaluateResult));
	}*/

	/**
	 * submitTransaction() will throw an error containing details of any error
	 * responses from the smart contract.
	 */
	/*private void updateNonExistentAsset() {
		try {
			System.out.println("\n--> Submit Transaction: UpdateAsset asset70, asset70 does not exist and should return an error");

			Receta receta = new Receta(
					"asset70",                  // Asset ID
					"Tomoko",                   // Owner
					"presc789",                 // Prescripcion Anterior Id
					"inactive",                 // Status
					LocalDateTime.now()  ,     // Status Change
					"medium",                   // Prioridad
					"Medicine ABC",             // Medicacion
					"Condition XYZ",            // Razon
					"Take with water",          // Notas
					"60 days",                  // Periodo De Tratamiento
					"2 pills per day",          // Instrucciones Tratamiento
					"60 days",               // Periodo De Validez
					"87654321",                 // Dni Paciente
					LocalDate.now() ,               // Fecha De Autorizacion
					60,                       // Cantidad
					LocalDate.now()               // Expected Supply Duration
			);

			contract.submitTransaction(
					"UpdateAsset",
					"asset70",
					receta.getOwner(),
					receta.getPrescripcionAnteriorId(),
					receta.getStatus(),
					receta.getStatusChange().toString(),
					receta.getPrioridad(),
					receta.getMedicacion(),
					receta.getRazon(),
					receta.getNotas(),
					receta.getPeriodoDeTratamiento(),
					receta.getInstruccionesTratamiento(),
					receta.getPeriodoDeValidez(),
					receta.getDniPaciente(),
					receta.getFechaDeAutorizacion().toString(),
					Integer.toString(receta.getCantidad()),
					receta.getExpectedSupplyDuration().toString()
			);

			System.out.println("******** FAILED to return an error");
		} catch (EndorseException | SubmitException | CommitStatusException e) {
			System.out.println("*** Successfully caught the error: ");
			e.printStackTrace(System.out);
			System.out.println("Transaction ID: " + e.getTransactionId());

			var details = e.getDetails();
			if (!details.isEmpty()) {
				System.out.println("Error Details:");
				for (var detail : details) {
					System.out.println("- address: " + detail.getAddress() + ", mspId: " + detail.getMspId()
							+ ", message: " + detail.getMessage());
				}
			}
		} catch (CommitException e) {
			System.out.println("*** Successfully caught the error: " + e);
			e.printStackTrace(System.out);
			System.out.println("Transaction ID: " + e.getTransactionId());
			System.out.println("Status code: " + e.getCode());
		}
	}*/
}
