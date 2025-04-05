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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RecetaService {

    private static final String MSP_ID = System.getenv().getOrDefault("MSP_ID", "Org1MSP");
    private static final String CHANNEL_NAME = System.getenv().getOrDefault("CHANNEL_NAME", "mychannel");
    private static final String CHAINCODE_NAME = System.getenv().getOrDefault("CHAINCODE_NAME", "basic");

    private static final Path CRYPTO_PATH = Paths.get("../../test-network/organizations/peerOrganizations/org1.example.com");
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
            this.initLedger();
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

    private void initLedger() throws EndorseException, SubmitException, CommitStatusException, CommitException {
        contract.submitTransaction("InitLedger");
    }

    public void cargarReceta(Receta receta) throws CommitStatusException, EndorseException, CommitException, SubmitException {
        contract.submitTransaction(
                "CreateReceta",
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
    }

    public Receta obtenerReceta(String recetaId) throws GatewayException, IOException {
        var evaluateResult = contract.evaluateTransaction("ReadReceta", recetaId);
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(evaluateResult, Receta.class);
    }

    public List<Receta> obtenerTodasLasRecetas() throws GatewayException, IOException {
        var evaluateResult = contract.evaluateTransaction("GetAllRecetas");
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(evaluateResult,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Receta.class));
    }

    public List<Receta> obtenerRecetasPorIds(List<String> recetaIds) throws GatewayException, IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        String idsJson = objectMapper.writeValueAsString(recetaIds);
        var evaluateResult = contract.evaluateTransaction("GetMultipleRecetas", idsJson);
        return objectMapper.readValue(evaluateResult,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Receta.class));
    }
}

public List<Receta> obtenerRecetasPorDniYEstado(String dni, String estado) throws GatewayException, IOException {
    if (dni == null || dni.isBlank() || estado == null || estado.isBlank()) {
        throw new IllegalArgumentException("DNI y estado son obligatorios");
    }

    var evaluateResult = contract.evaluateTransaction("GetRecetasPorDniYEstado", dni, estado);
    ObjectMapper objectMapper = new ObjectMapper();
    return objectMapper.readValue(evaluateResult,
            objectMapper.getTypeFactory().constructCollectionType(List.class, Receta.class));
}

