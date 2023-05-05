/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;

public final class Connections {
    public static final String CHANNEL_NAME = Utils.getEnvOrDefault("CHANNEL_NAME", "mychannel");
    public static final String CHAINCODE_NAME = Utils.getEnvOrDefault("CHAINCODE_NAME", "basic");

    private static final String PEER_NAME = "peer0.org1.example.com";
    private static final String MSP_ID = Utils.getEnvOrDefault("MSP_ID", "Org1MSP");

    // Path to crypto materials.
    private static final Path CRYPTO_PATH = Utils.getEnvOrDefault(
            "CRYPTO_PATH",
            Paths::get,
            Paths.get("..", "..", "..", "test-network", "organizations", "peerOrganizations", "org1.example.com")
    );

    // Path to user private key directory.
    private static final Path KEY_DIR_PATH = Utils.getEnvOrDefault(
            "KEY_DIRECTORY_PATH",
            Paths::get,
            CRYPTO_PATH.resolve(Paths.get("users", "User1@org1.example.com", "msp", "keystore"))
    );

    // Path to user certificate.
    private static final Path CERT_PATH = Utils.getEnvOrDefault(
            "CERT_PATH",
            Paths::get,
            CRYPTO_PATH.resolve(Paths.get("users", "User1@org1.example.com", "msp", "signcerts", "cert.pem"))
    );

    // Path to peer tls certificate.
    private static final Path TLS_CERT_PATH = Utils.getEnvOrDefault(
            "TLS_CERT_PATH",
            Paths::get,
            CRYPTO_PATH.resolve(Paths.get("peers", PEER_NAME, "tls", "ca.crt"))
    );

    // Gateway peer end point.
    private static final String PEER_ENDPOINT = Utils.getEnvOrDefault("PEER_ENDPOINT", "localhost:7051");

    // Gateway peer SSL host name override.
    private static final String PEER_HOST_ALIAS = Utils.getEnvOrDefault("PEER_HOST_ALIAS", PEER_NAME);

    private static final long EVALUATE_TIMEOUT_SECONDS = 5;
    private static final long ENDORSE_TIMEOUT_SECONDS = 15;
    private static final long SUBMIT_TIMEOUT_SECONDS = 5;
    private static final long COMMIT_STATUS_TIMEOUT_SECONDS = 60;

    private Connections() {
        // Private constructor to prevent instantiation
    }

    public static ManagedChannel newGrpcConnection() throws IOException {
        var credentials = TlsChannelCredentials.newBuilder()
                .trustManager(TLS_CERT_PATH.toFile())
                .build();
        return Grpc.newChannelBuilder(PEER_ENDPOINT, credentials)
                .overrideAuthority(PEER_HOST_ALIAS)
                .build();
    }

    public static Gateway.Builder newGatewayBuilder(final Channel grpcChannel) throws CertificateException, IOException, InvalidKeyException {
        return Gateway.newInstance()
                .identity(newIdentity())
                .signer(newSigner())
                .connection(grpcChannel)
                .evaluateOptions(options -> options.withDeadlineAfter(EVALUATE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .endorseOptions(options -> options.withDeadlineAfter(ENDORSE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .submitOptions(options -> options.withDeadlineAfter(SUBMIT_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .commitStatusOptions(options -> options.withDeadlineAfter(COMMIT_STATUS_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }

    private static Identity newIdentity() throws IOException, CertificateException {
        var certReader = Files.newBufferedReader(CERT_PATH);
        var certificate = Identities.readX509Certificate(certReader);

        return new X509Identity(MSP_ID, certificate);
    }

    private static Signer newSigner() throws IOException, InvalidKeyException {
        var keyReader = Files.newBufferedReader(getPrivateKeyPath());
        var privateKey = Identities.readPrivateKey(keyReader);

        return Signers.newPrivateKeySigner(privateKey);
    }

    private static Path getPrivateKeyPath() throws IOException {
        try (var keyFiles = Files.list(KEY_DIR_PATH)) {
            return keyFiles.findFirst().orElseThrow();
        }
    }
}
