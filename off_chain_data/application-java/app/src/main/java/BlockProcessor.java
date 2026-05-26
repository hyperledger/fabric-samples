/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperledger.fabric.client.Checkpointer;
import parser.Block;
import parser.Transaction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class BlockProcessor {
    private final Block block;
    private final Checkpointer checkpointer;
    private final Store store;

    public BlockProcessor(final Block block, final Checkpointer checkpointer, final Store store) {
        this.block = block;
        this.checkpointer = checkpointer;
        this.store = store;
    }

    public void process() {
        var blockNumber = block.getNumber();
        System.out.println("\nReceived block " + Long.toUnsignedString(blockNumber));

        try {
            var validTransactions = getNewTransactions().stream()
                    .filter(Transaction::isValid)
                    .collect(Collectors.toList());

            for (var transaction : validTransactions) {
                new TransactionProcessor(transaction, blockNumber, store).process();

                var transactionId = transaction.getChannelHeader().getTxId();
                checkpointer.checkpointTransaction(blockNumber, transactionId);
            }

            checkpointer.checkpointBlock(blockNumber);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<Transaction> getNewTransactions() throws InvalidProtocolBufferException {
        var transactions = block.getTransactions();

        var lastTransactionId = checkpointer.getTransactionId();
        if (lastTransactionId.isEmpty()) {
            // No previously processed transactions within this block so all are new
            return transactions;
        }

        var transactionIds = new ArrayList<>();
        for (var transaction : transactions) {
            transactionIds.add(transaction.getChannelHeader().getTxId());
        }

        // Ignore transactions up to the last processed transaction ID
        var lastProcessedIndex = transactionIds.indexOf(lastTransactionId.get());
        if (lastProcessedIndex < 0) {
            throw new IllegalArgumentException("Checkpoint transaction ID " + lastTransactionId + " not found in block "
                    + Long.toUnsignedString(block.getNumber()) + " containing transactions: " + transactionIds);
        }

        return transactions.subList(lastProcessedIndex + 1, transactions.size());
    }
}
