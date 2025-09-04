package com.code.hyperledger.services;

import io.grpc.TlsChannelCredentials;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import com.code.hyperledger.configs.FabricConfigProperties;
import com.code.hyperledger.models.Receta;
import com.code.hyperledger.models.RecetaDto;
//import com.code.hyperledger.models.ResultadoPaginado;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.SneakyThrows;
import main.java.com.code.hyperledger.models.ResultadoPaginado;

import org.hyperledger.fabric.client.*;
import org.hyperledger.fabric.client.identity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.JavaType;

@Service
public class RecetaService {

    private final FabricConfigProperties config;
    private Contract contract;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Autowired
    public RecetaService(FabricConfigProperties config) {
        this.config = config;
    }

    private Path getPath(String relativePath) {
        return Paths.get(config.getCryptoPath()).resolve(relativePath);
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

        var gateway = Gateway.newInstance()
                .identity(newIdentity())
                .signer(newSigner())
                .connection(channel)
                .evaluateOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                .endorseOptions(options -> options.withDeadlineAfter(15, TimeUnit.SECONDS))
                .submitOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
                .commitStatusOptions(options -> options.withDeadlineAfter(1, TimeUnit.MINUTES))
                .connect();

        this.setContract(gateway);
        this.initLedger();
    }

    private ManagedChannel newGrpcConnection() throws IOException {
        var tlsCertPath = getPath(config.getTlsCertPath());
        var credentials = TlsChannelCredentials.newBuilder()
                .trustManager(tlsCertPath.toFile())
                .build();

        return Grpc.newChannelBuilder(config.getPeerEndpoint(), credentials)
                .overrideAuthority(config.getOverrideAuth())
                .build();
    }

    private Identity newIdentity() throws IOException, CertificateException {
        Path certPath = getFirstFilePath(getPath(config.getCertPath()));
        try (Reader certReader = Files.newBufferedReader(certPath)) {
            var certificate = Identities.readX509Certificate(certReader);
            return new X509Identity(config.getMspId(), certificate);
        }
    }

    private Signer newSigner() throws IOException, InvalidKeyException {
        Path keyPath = getFirstFilePath(getPath(config.getKeyPath()));
        try (Reader keyReader = Files.newBufferedReader(keyPath)) {
            var privateKey = Identities.readPrivateKey(keyReader);
            return Signers.newPrivateKeySigner(privateKey);
        }
    }

    private void setContract(final Gateway gateway) {
        var network = gateway.getNetwork(config.getChannelName());
        contract = network.getContract(config.getChaincodeName());
    }

    // crea dos recetas default, borrar cuando no se necesite
    private void initLedger() throws EndorseException, SubmitException, CommitStatusException, CommitException {
        contract.submitTransaction("InitLedger");
    }

    public void cargarReceta(Receta receta)
            throws CommitStatusException, EndorseException, CommitException, SubmitException {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String recetaJson = objectMapper.writeValueAsString(receta);
            contract.submitTransaction("CreateReceta", recetaJson);
        } catch (Exception e) {
            System.err.println("Error en submitTransaction: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Receta obtenerReceta(String recetaId) throws Exception {
        var evaluateResult = contract.evaluateTransaction("ReadReceta", recetaId);
        return new ObjectMapper().readValue(evaluateResult, Receta.class);
    }

    public List<Receta> obtenerTodasLasRecetas() throws Exception {
        var evaluateResult = contract.evaluateTransaction("GetAllRecetas");
        return new ObjectMapper().readValue(evaluateResult,
                new ObjectMapper().getTypeFactory().constructCollectionType(List.class, Receta.class));
    }

    public List<Receta> obtenerRecetasPorIds(List<String> recetaIds) throws Exception {
        String idsJson = new ObjectMapper().writeValueAsString(recetaIds);
        var evaluateResult = contract.evaluateTransaction("GetMultipleRecetas", idsJson);
        return new ObjectMapper().readValue(evaluateResult,
                new ObjectMapper().getTypeFactory().constructCollectionType(List.class, Receta.class));
    }

    public void entregarReceta(String recetaId) throws Exception {
        contract.submitTransaction("EntregarReceta", recetaId);
    }

    public void firmarReceta(String recetaId, String signature) throws Exception {
        contract.submitTransaction("FirmarReceta", recetaId, signature);
    }

    public void borrarReceta(String recetaId) throws Exception {
        contract.submitTransaction("DeleteReceta", recetaId);
    }

    public ResultadoPaginado<RecetaDto> obtenerRecetasPorDniYEstadoPaginado(
            String dni, List<String> estados, int pageSize, String bookmark) throws Exception {
        String estadosJson = new ObjectMapper().writeValueAsString(estados);

        byte[] result = contract.evaluateTransaction(
                "GetRecetasPorDniYEstadosPaginado",
                dni,
                estadosJson,
                String.valueOf(pageSize),
                bookmark);
        ObjectMapper mapper = new ObjectMapper();
        JavaType tipo = mapper.getTypeFactory()
                .constructParametricType(ResultadoPaginado.class, RecetaDto.class);

        return mapper.readValue(result, tipo);
    }

}
