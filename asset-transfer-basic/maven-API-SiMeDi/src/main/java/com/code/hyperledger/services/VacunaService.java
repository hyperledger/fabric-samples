package com.code.hyperledger.services;

import com.code.hyperledger.configs.FabricConfigProperties;
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

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class VacunaService {

    private final FabricConfigProperties config;
    private Contract contract;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public VacunaService(FabricConfigProperties config) {
        this.config = config;
    }

    private Path getFirstFilePath(Path dirPath) throws IOException {
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

    private ManagedChannel newGrpcConnection() throws IOException {
        Path tlsPath = Paths.get(config.getCryptoPath(), config.getTlsCertPath());
        var credentials = TlsChannelCredentials.newBuilder()
                .trustManager(tlsPath.toFile())
                .build();
        return Grpc.newChannelBuilder(config.getPeerEndpoint(), credentials)
                .overrideAuthority(config.getOverrideAuth())
                .build();
    }

    private Identity newIdentity() throws IOException, CertificateException {
        Path certPath = Paths.get(config.getCryptoPath(), config.getCertPath());
        try (var certReader = Files.newBufferedReader(getFirstFilePath(certPath))) {
            var certificate = Identities.readX509Certificate(certReader);
            return new X509Identity(config.getMspId(), certificate);
        }
    }

    private Signer newSigner() throws IOException, InvalidKeyException {
        Path keyPath = Paths.get(config.getCryptoPath(), config.getKeyPath());
        try (var keyReader = Files.newBufferedReader(getFirstFilePath(keyPath))) {
            var privateKey = Identities.readPrivateKey(keyReader);
            return Signers.newPrivateKeySigner(privateKey);
        }
    }

    private void setContract(final Gateway gateway) {
        var network = gateway.getNetwork(config.getChannelName());
        contract = network.getContract(config.getChaincodeName());
    }

    // Todos los métodos siguientes permanecen idénticos...

    public void cargarVacuna(Vacuna vacuna)
            throws CommitStatusException, EndorseException, CommitException, SubmitException {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String vacunaJson = objectMapper.writeValueAsString(vacuna);

            contract.submitTransaction("CreateVacuna", vacunaJson);
        } catch (Exception e) {
            System.err.println("Error en submitTransaction: " + e.getMessage());
            e.printStackTrace();
        }
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
