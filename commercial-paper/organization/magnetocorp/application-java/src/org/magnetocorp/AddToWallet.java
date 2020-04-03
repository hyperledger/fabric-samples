/*
SPDX-License-Identifier: Apache-2.0
*/

package org.magnetocorp;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import org.hyperledger.fabric.gateway.Identities;
import org.hyperledger.fabric.gateway.Wallet;
import org.hyperledger.fabric.gateway.Wallets;
import org.hyperledger.fabric.gateway.Identity;

public class AddToWallet {

  private static X509Certificate readX509Certificate(final Path certificatePath) throws IOException, CertificateException {
    try (Reader certificateReader = Files.newBufferedReader(certificatePath, StandardCharsets.UTF_8)) {
      return Identities.readX509Certificate(certificateReader);
    }
  }

  private static PrivateKey getPrivateKey(final Path privateKeyPath) throws IOException, InvalidKeyException {
    try (Reader privateKeyReader = Files.newBufferedReader(privateKeyPath, StandardCharsets.UTF_8)) {
      return Identities.readPrivateKey(privateKeyReader);
    }
  }

  public static void main(String[] args) {
    try {
      // A wallet stores a collection of identities
      Path walletPath = Paths.get(".", "wallet");
      Wallet wallet = Wallets.newFileSystemWallet(walletPath);

      Path credentialPath = Paths.get("..", "..", "..",".." ,"test-network", "organizations",
          "peerOrganizations", "org2.example.com", "users", "User1@org2.example.com", "msp");
      System.out.println("credentialPath: " + credentialPath.toString());
      Path certificatePath = credentialPath.resolve(Paths.get("signcerts",
          "User1@org2.example.com-cert.pem"));
      System.out.println("certificatePem: " + certificatePath.toString());
      Path privateKeyPath = credentialPath.resolve(Paths.get("keystore",
          "c7077781c776d90009b3f68a75b15109e1cb82ae5dbdcc0b79644ff47d280db8_sk"));

      X509Certificate certificate = readX509Certificate(certificatePath);
      PrivateKey privateKey = getPrivateKey(privateKeyPath);

      Identity identity = Identities.newX509Identity("Org2MSP", certificate, privateKey);


      String identityLabel = "User1@org2.example.com";
      wallet.put(identityLabel, identity);

      System.out.println("成功写入钱包信息：" + walletPath.toString());

    } catch (IOException | CertificateException | InvalidKeyException e) {
      System.err.println("Error adding to wallet");
      e.printStackTrace();
    }
  }

}