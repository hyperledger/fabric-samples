package com.code.hyperledger.services;

import com.code.hyperledger.models.Receta;
import com.code.hyperledger.models.Vacuna;
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
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signer;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class VacunaService {

    private static final String MSP_ID = System.getenv().getOrDefault("MSP_ID", "Org1MSP");
    private static final String CHANNEL_NAME = System.getenv().getOrDefault("CHANNEL_NAME", "mychannel");
    private static final String CHAINCODE_NAME = System.getenv().getOrDefault("CHAINCODE_NAME", "basic");

    private static final Path CRYPTO_PATH = Paths
            .get("../../test-network/organizations/peerOrganizations/org1.example.com");
    private static final Path CERT_DIR_PATH = CRYPTO_PATH.resolve("users/User1@org1.example.com/msp/signcerts");
    private static final Path KEY_DIR_PATH = CRYPTO_PATH.resolve("users/User1@org1.example.com/msp/keystore");
    private static final Path TLS_CERT_PATH = CRYPTO_PATH.resolve("peers/peer0.org1.example.com/tls/ca.crt");

    private static final String PEER_ENDPOINT = "localhost:7051";
    private static final String OVERRIDE_AUTH = "peer0.org1.example.com";

    private Contract contract;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static Path getFirstFilePath(Path dirPath) throws IOException {
        try (var keyFiles = Files.list(dirPath)) {
            return keyFiles.findFirst().orElseThrow();
        }
    }

    @SneakyThrows
    @PostConstruct
    public void init() {
        var channel = newGrpcConnection();

        var builder = Gateway.newInstance()
                .identity(newIdentity())
                .signer(newSigner())
                .connection(channel)
                .evaluateOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                .endorseOptions(options -> options.withDeadlineAfter(15, TimeUnit.SECONDS))
                .submitOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                .commitStatusOptions(options -> options.withDeadlineAfter(1, TimeUnit.MINUTES));

        try (var gateway = builder.connect()) {
            this.setContract(gateway);
        }
    }

    private static ManagedChannel newGrpcConnection() throws IOException {
        var credentials = TlsChannelCredentials.newBuilder()
                .trustManager(TLS_CERT_PATH.toFile())
                .build();
        return Grpc.newChannelBuilder(PEER_ENDPOINT, credentials)
                .overrideAuthority(OVERRIDE_AUTH)
                .build();
    }

    private Identity newIdentity() throws IOException, CertificateException {
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
        var network = gateway.getNetwork(CHANNEL_NAME);
        contract = network.getContract(CHAINCODE_NAME);
    }

    public void registrarVacuna(Vacuna vacuna)
            throws CommitStatusException, EndorseException, CommitException, SubmitException {
        contract.submitTransaction(
                "CreateVacuna",
                vacuna.getId(),
                vacuna.getIdentificador(),
                vacuna.getStatus(),
                vacuna.getStatusChange(),
                vacuna.getStatusReason(),
                vacuna.getVaccinateCode(),
                vacuna.getAdministradedProduct(),
                vacuna.getManufacturer(),
                vacuna.getLotNumber(),
                vacuna.getExpirationDate(),
                vacuna.getPatientDocumentNumber(),
                vacuna.getReactions()
        );
    }

    public Vacuna obtenerVacuna(String vacunaId) throws GatewayException, IOException {
        var evaluateResult = contract.evaluateTransaction("ReadVacuna", vacunaId);
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(evaluateResult, Vacuna.class);
    }

    public List<Vacuna> obtenerTodasLasVacunas() throws GatewayException, IOException {
        var evaluateResult = contract.evaluateTransaction("GetAllVacunas");
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(evaluateResult,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Vacuna.class));
    }

    public List<Vacuna> obtenerVacunasPorIds(List<String> vacunaIds) throws GatewayException, IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        String idsJson = objectMapper.writeValueAsString(vacunaIds);
        var evaluateResult = contract.evaluateTransaction("GetMultipleVacunas", idsJson);

        if (evaluateResult == null || evaluateResult.length == 0) {
            System.err.println("GetMultipleVacunas devolvió una respuesta vacía.");
            return new ArrayList<>();
        }

        return objectMapper.readValue(evaluateResult,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Vacuna.class));
    }

    public List<Vacuna> obtenerVacunasPorDniYEstado(String dni, String estado) throws GatewayException, IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] evaluateResult;
        if (estado == null || estado.isBlank()) {
            evaluateResult = contract.evaluateTransaction("GetVacunasPorDni", dni);
        } else {
            evaluateResult = contract.evaluateTransaction("GetVacunasPorDniYEstado", dni, estado);
        }
        return objectMapper.readValue(evaluateResult,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Vacuna.class));
    }
}
