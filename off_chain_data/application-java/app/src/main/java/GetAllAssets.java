/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.grpc.Channel;
import org.hyperledger.fabric.client.CommitException;
import org.hyperledger.fabric.client.CommitStatusException;
import org.hyperledger.fabric.client.EndorseException;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.SubmitException;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;

public final class GetAllAssets implements Command {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void run(final Channel grpcChannel)
            throws CertificateException, IOException, InvalidKeyException, EndorseException, CommitException, SubmitException, CommitStatusException {
        try (Gateway gateway = Connections.newGatewayBuilder(grpcChannel).connect()) {
            var network = gateway.getNetwork(Connections.CHANNEL_NAME);
            var contract = network.getContract(Connections.CHAINCODE_NAME);

            var smartContract = new AssetTransferBasic(contract);

            var assets = smartContract.getAllAssets();
            var assetsJson = GSON.toJson(assets);
            System.out.println(assetsJson);
        }
    }
}
