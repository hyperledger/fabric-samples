/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
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

public final class Connections {
    // Path to crypto materials.
    private static final Path cryptoPath = Paths.get("..", "..", "test-network", "organizations", "peerOrganizations",	"org1.example.com");
    // Path to user certificate.
    private static final Path certPath = cryptoPath.resolve(Paths.get("users", "User1@org1.example.com", "msp", "signcerts", "cert.pem"));
    // Path to user private key directory.
    private static final Path keyDirPath = cryptoPath.resolve(Paths.get("users", "User1@org1.example.com", "msp", "keystore"));
    // Path to peer tls certificate.
    private static final Path tlsCertPath = cryptoPath.resolve(Paths.get("peers", "peer0.org1.example.com", "tls", "ca.crt"));

    // Gateway peer end point.
    private static final String peerEndpoint = "localhost:7051";
    private static final String overrideAuth = "peer0.org1.example.com";

    private static final String mspID = "Org1MSP";

    private Connections() {
        // Private constructor to prevent instantiation
    }

    public static ManagedChannel newGrpcConnection() throws IOException, CertificateException {
        var tlsCertReader = Files.newBufferedReader(tlsCertPath);
        var tlsCert = Identities.readX509Certificate(tlsCertReader);

        return NettyChannelBuilder.forTarget(peerEndpoint)
                .sslContext(GrpcSslContexts.forClient().trustManager(tlsCert).build()).overrideAuthority(overrideAuth)
                .build();
    }

    public static Identity newIdentity() throws IOException, CertificateException {
        var certReader = Files.newBufferedReader(certPath);
        var certificate = Identities.readX509Certificate(certReader);

        return new X509Identity(mspID, certificate);
    }

    public static Signer newSigner() throws IOException, InvalidKeyException {
        var keyReader = Files.newBufferedReader(getPrivateKeyPath());
        var privateKey = Identities.readPrivateKey(keyReader);

        return Signers.newPrivateKeySigner(privateKey);
    }

    private static Path getPrivateKeyPath() throws IOException {
        try (var keyFiles = Files.list(keyDirPath)) {
            return keyFiles.findFirst().orElseThrow();
        }
    }
}
