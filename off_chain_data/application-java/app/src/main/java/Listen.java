/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.util.List;

import com.google.gson.Gson;
import io.grpc.Channel;
import org.hyperledger.fabric.client.CloseableIterator;
import org.hyperledger.fabric.client.FileCheckpointer;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.Network;
import org.hyperledger.fabric.protos.common.Common;
import parser.Block;
import parser.BlockParser;

public final class Listen implements Command {
    private static final Path CHECKPOINT_FILE = Paths.get(Utils.getEnvOrDefault("CHECKPOINT_FILE", "checkpoint.json"));
    private static final Path STORE_FILE = Paths.get(Utils.getEnvOrDefault("STORE_FILE", "store.log"));
    private static final int SIMULATED_FAILURE_COUNT = Utils.getEnvOrDefault("SIMULATED_FAILURE_COUNT", Integer::parseUnsignedInt, 0);

    private static final long START_BLOCK = 0L;
    private static final Gson GSON = new Gson();

    private int transactionCount = 0; // Used only to simulate failures

    @Override
    public void run(final Channel grpcChannel)
            throws CertificateException, IOException, InvalidKeyException {
        try (Gateway gateway = Connections.newGatewayBuilder(grpcChannel).connect();
             FileCheckpointer checkpointer = new FileCheckpointer(CHECKPOINT_FILE)) {
            Network network = gateway.getNetwork(Connections.CHANNEL_NAME);

            System.out.println("Starting event listening from block " + Long.toUnsignedString(checkpointer.getBlockNumber().orElse(START_BLOCK)));
            System.out.println(checkpointer.getTransactionId()
                    .map(transactionId -> "Last processed transaction ID within block: " + transactionId)
                    .orElse("No last processed transaction ID"));
            if (SIMULATED_FAILURE_COUNT > 0) {
                System.out.println("Simulating a write failure every " + SIMULATED_FAILURE_COUNT + " transactions");
            }

            try (CloseableIterator<Common.Block> blocks = network.newBlockEventsRequest()
                    .startBlock(START_BLOCK) // Used only if there is no checkpoint block number
                    .checkpoint(checkpointer)
                    .build()
                    .getEvents()) {
                blocks.forEachRemaining(blockProto -> {
                    Block block = BlockParser.parseBlock(blockProto);
                    BlockProcessor processor = new BlockProcessor(block, checkpointer, this::applyWritesToOffChainStore);
                    processor.process();
                });
            }
        }
    }

    private void applyWritesToOffChainStore(final long blockNumber, final String transactionId, final List<Write> writes) throws IOException {
        simulateFailureIfRequired();

        try (StringWriter writer = new StringWriter()) {
            for (Write write : writes) {
                GSON.toJson(write, writer);
                writer.append('\n');
            }

            Files.writeString(STORE_FILE, writer.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    private void simulateFailureIfRequired() {
        if (SIMULATED_FAILURE_COUNT > 0 && transactionCount++ >= SIMULATED_FAILURE_COUNT) {
            transactionCount = 0;
            throw new ExpectedException("Simulated write failure");
        }
    }
}
