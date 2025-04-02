package com.code.hyperledger.services;

import com.code.hyperledger.coso.Receta;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import lombok.SneakyThrows;
import org.hyperledger.fabric.client.*;
import org.hyperledger.fabric.client.identity.*;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

import java.io.Console;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Service
public class RecetaService {

    private static final String MSP_ID = System.getenv().getOrDefault("MSP_ID", "Org1MSP");
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

    private Contract contract;
    //private final String assetId = "asset" + Instant.now().toEpochMilli();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();



    private static Path getFirstFilePath(Path dirPath) throws IOException {
        try (var keyFiles = Files.list(dirPath)) {
            return keyFiles.findFirst().orElseThrow();
        }
    }
    @SneakyThrows
    @PostConstruct
    public void init() {

        System.out.println("LLEGO ACA");
        var channel = newGrpcConnection();

        var builder = Gateway.newInstance().identity(newIdentity()).signer(newSigner()).connection(channel)
                // Default timeouts for different gRPC calls
                .evaluateOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                .endorseOptions(options -> options.withDeadlineAfter(15, TimeUnit.SECONDS))
                .submitOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                .commitStatusOptions(options -> options.withDeadlineAfter(1, TimeUnit.MINUTES));

        try (var gateway = builder.connect()) {
            System.out.println("LLEGO ACA 2");
            this.setContract(gateway);
            System.out.println("LLEGO ACA 3");
            this.initLedger();
            System.out.println("LLEGO ACA 4");
        } /*finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }*/

    }

    private static ManagedChannel newGrpcConnection() throws IOException {
        var credentials = TlsChannelCredentials.newBuilder()
                .trustManager(TLS_CERT_PATH.toFile())
                .build();
        return Grpc.newChannelBuilder(PEER_ENDPOINT, credentials)
                .overrideAuthority(OVERRIDE_AUTH)
                .build();
    }

    private  Identity newIdentity() throws IOException, CertificateException {
        try (var certReader = Files.newBufferedReader(getFirstFilePath(CERT_DIR_PATH))) {
            var certificate = Identities.readX509Certificate(certReader);
            return new X509Identity(MSP_ID, certificate);
        }
    }

    private Signer newSigner() throws IOException, InvalidKeyException {
        try (var keyReader = Files.newBufferedReader(getFirstFilePath(KEY_DIR_PATH))) {
            var privateKey = Identities.readPrivateKey(keyReader);
            return Signers.newPrivateKeySigner(privateKey);
        }
    }

    private void setContract(final Gateway gateway) {
        // Get a network instance representing the channel where the smart contract is
        // deployed.
        var network = gateway.getNetwork(CHANNEL_NAME);

        // Get the smart contract from the network.
        contract = network.getContract(CHAINCODE_NAME);
    }

    private void initLedger() throws EndorseException, SubmitException, CommitStatusException, CommitException {
        System.out.println("\n--> Submit Transaction: InitLedger, function creates the initial set of assets on the ledger");

        contract.submitTransaction("InitLedger");

        System.out.println("*** Transaction committed successfully");
    }

    public void cargarReceta(Receta receta) throws CommitStatusException, EndorseException, CommitException, SubmitException {
        System.out.println("\n--> Submit Transaction: CreateAsset, creates new asset with all arguments");

            contract.submitTransaction(
                    "CreateAsset",
                    receta.getId(),
                    receta.getOwner(),
                    receta.getPrescripcionAnteriorId(),
                    receta.getStatus(),
                    receta.getStatusChange(),
                    receta.getPrioridad(),
                    receta.getMedicacion(),
                    receta.getRazon(),
                    receta.getNotas(),
                    receta.getPeriodoDeTratamiento(),
                    receta.getInstruccionesTratamiento(),
                    receta.getPeriodoDeValidez(),
                    receta.getDniPaciente(),
                    receta.getFechaDeAutorizacion(),
                    Integer.toString(receta.getCantidad()),
                    receta.getExpectedSupplyDuration()
            );

        System.out.println("*** Transaction committed successfully");
    }

    public Receta obtenerReceta(String assetId) throws GatewayException, IOException {
        System.out.println("\n--> Evaluate Transaction: ReadAsset, function returns asset attributes");

        System.out.println("assetId: " + assetId);
        var evaluateResult = contract.evaluateTransaction("ReadAsset", assetId);
        System.out.println("evaluate");
        ObjectMapper objectMapper = new ObjectMapper();
        var receta = objectMapper.readValue(evaluateResult, Receta.class);
        System.out.println("mapper:" + receta.getDniPaciente());
        return receta;
    }
}
