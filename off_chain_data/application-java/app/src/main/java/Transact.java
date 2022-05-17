/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;

import io.grpc.Channel;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Network;

public final class Transact implements Command {
    @Override
    public void run(final Channel grpcChannel)
            throws CertificateException, IOException, InvalidKeyException {
        try (Gateway gateway = Connections.newGatewayBuilder(grpcChannel).connect()) {
            Network network = gateway.getNetwork(Connections.CHANNEL_NAME);
            Contract contract = network.getContract(Connections.CHAINCODE_NAME);

            AssetTransferBasic smartContract = new AssetTransferBasic(contract);

            TransactApp app = new TransactApp(smartContract);
            app.run();
        }
    }
}
