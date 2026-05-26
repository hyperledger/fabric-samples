/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import io.grpc.Channel;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;

public final class Transact implements Command {
    @Override
    public void run(final Channel grpcChannel)
            throws CertificateException, IOException, InvalidKeyException {
        try (var gateway = Connections.newGatewayBuilder(grpcChannel).connect()) {
            var network = gateway.getNetwork(Connections.CHANNEL_NAME);
            var contract = network.getContract(Connections.CHAINCODE_NAME);

            var smartContract = new AssetTransferBasic(contract);

            var app = new TransactApp(smartContract);
            app.run();
        }
    }
}
