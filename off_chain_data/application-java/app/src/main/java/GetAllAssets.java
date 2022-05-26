/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.grpc.Channel;
import org.hyperledger.fabric.client.CommitException;
import org.hyperledger.fabric.client.CommitStatusException;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.EndorseException;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Network;
import org.hyperledger.fabric.client.SubmitException;

public final class GetAllAssets implements Command {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void run(final Channel grpcChannel)
            throws CertificateException, IOException, InvalidKeyException, EndorseException, CommitException, SubmitException, CommitStatusException {
        try (Gateway gateway = Connections.newGatewayBuilder(grpcChannel).connect()) {
            Network network = gateway.getNetwork(Connections.CHANNEL_NAME);
            Contract contract = network.getContract(Connections.CHAINCODE_NAME);

            AssetTransferBasic smartContract = new AssetTransferBasic(contract);

            List<Asset> assets = smartContract.getAllAssets();
            String assetsJson = GSON.toJson(assets);
            System.out.println(assetsJson);
        }
    }
}
