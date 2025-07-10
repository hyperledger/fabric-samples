package com.code.hyperledger.services;

import com.code.hyperledger.models.Receta;
import com.code.hyperledger.models.RecetaDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import lombok.SneakyThrows;
import main.java.com.code.hyperledger.models.ResultadoPaginado;

import org.hyperledger.fabric.client.*;
import org.hyperledger.fabric.client.identity.*;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Identity;
import java.security.InvalidKeyException;
import java.security.Signer;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RecetaService {

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

    public void cargarReceta(Receta receta)
            throws CommitStatusException, EndorseException, CommitException, SubmitException {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String recetaJson = objectMapper.writeValueAsString(receta);

            contract.submitTransaction("CreateReceta", recetaJson);
            System.out.println("Receta creada correctamente");
        } catch (Exception e) {
            System.err.println("Error en submitTransaction: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Receta obtenerReceta(String recetaId) throws GatewayException, IOException {
        System.out.println("[INFO] Iniciando obtención de receta con ID: " + recetaId);

        try {
            System.out.println("[DEBUG] Ejecutando transacción 'ReadReceta' con ID: " + recetaId);
            var evaluateResult = contract.evaluateTransaction("ReadReceta", recetaId);
            System.out.println("[DEBUG] Resultado de transacción recibido: " + new String(evaluateResult));

            ObjectMapper objectMapper = new ObjectMapper();
            Receta receta = objectMapper.readValue(evaluateResult, Receta.class);
            System.out.println("[INFO] Receta parseada exitosamente para ID: " + recetaId);

            return receta;
        } catch (GatewayException e) {
            System.err.println("[ERROR] GatewayException al obtener receta con ID: " + recetaId);
            e.printStackTrace(System.err);
            throw e;
        } catch (IOException e) {
            System.err.println("[ERROR] IOException al parsear receta con ID: " + recetaId);
            e.printStackTrace(System.err);
            throw e;
        } catch (Exception e) {
            System.err.println("[ERROR] Error inesperado al obtener receta con ID: " + recetaId);
            e.printStackTrace(System.err);
            throw new RuntimeException("Error inesperado al obtener la receta", e);
        }
    }

    public List<Receta> obtenerTodasLasRecetas() throws GatewayException, IOException {
        var evaluateResult = contract.evaluateTransaction("GetAllRecetas");
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(evaluateResult,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Receta.class));
    }

    public List<Receta> obtenerRecetasPorIds(List<String> recetaIds) throws GatewayException, IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        System.out.println("Solicitando recetas con IDs: " + recetaIds);

        String idsJson = objectMapper.writeValueAsString(recetaIds);
        var evaluateResult = contract.evaluateTransaction("GetMultipleRecetas", idsJson);

        if (evaluateResult == null || evaluateResult.length == 0) {
            System.err.println("GetMultipleRecetas devolvió una respuesta vacía.");
            return new ArrayList<>();
        }

        List<Receta> recetas = objectMapper.readValue(
                evaluateResult,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Receta.class));

        System.out.println("Recetas obtenidas del contrato:");
        for (Receta receta : recetas) {
            System.out.println(" - ID: " + receta.getId() + " | Estado: " + receta.getStatus());
        }

        return recetas;
    }

    public void entregarReceta(String recetaId)
            throws CommitStatusException, EndorseException, CommitException, SubmitException {
        contract.submitTransaction("EntregarReceta", recetaId);
    }

    public void firmarReceta(String recetaId, String signature)
            throws CommitStatusException, EndorseException, CommitException, SubmitException {
        System.out.println("[INFO] Iniciando firma de receta con ID: " + recetaId);
        try {
            var evaluateResult = contract.submitTransaction("FirmarReceta", recetaId, signature);
            System.out.println("[DEBUG] Resultado de transacción recibido: " + new String(evaluateResult));
            System.out.println("[INFO] Receta firmada exitosamente para ID: " + recetaId);
        } catch (Exception e) {
            System.err.println("[ERROR] Error al firmar receta: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void borrarReceta(String recetaId)
            throws CommitStatusException, EndorseException, CommitException, SubmitException {
        System.out.println("[INFO] Iniciando borrado de receta con ID: " + recetaId);
        try {
            var evaluateResult = contract.submitTransaction("DeleteReceta", recetaId);
            System.out.println("[DEBUG] Resultado de transacción recibido: " + new String(evaluateResult));
            System.out.println("[INFO] Receta borrada exitosamente para ID: " + recetaId);
        } catch (Exception e) {
            System.err.println("[ERROR] Error al borrar receta: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public ResultadoPaginado<RecetaDto> obtenerRecetasPorDniYEstadoPaginado(
            String dni, String estado, int pageSize, String bookmark) throws GatewayException, IOException {

        if (dni == null || dni.isBlank() || estado == null || estado.isBlank()) {
            throw new IllegalArgumentException("DNI y estado son obligatorios");
        }

        var result = contract.evaluateTransaction("GetRecetasPorDniYEstado", dni, estado, String.valueOf(pageSize),
                bookmark);

        var type = new ObjectMapper()
                .getTypeFactory()
                .constructParametricType(ResultadoPaginado.class, RecetaDto.class);

        return new ObjectMapper().readValue(result, type);
    }
}
