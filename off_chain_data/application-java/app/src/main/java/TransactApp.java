/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import org.hyperledger.fabric.client.CommitException;
import org.hyperledger.fabric.client.CommitStatusException;
import org.hyperledger.fabric.client.EndorseException;
import org.hyperledger.fabric.client.SubmitException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

public final class TransactApp {
    private static final List<String> COLORS = List.of("red", "green", "blue");
    private static final List<String> OWNERS = List.of("alice", "bob", "charlie");
    private static final int MAX_INITIAL_VALUE = 1000;
    private static final int MAX_INITIAL_SIZE = 10;

    private final AssetTransferBasic smartContract;
    private final int batchSize = 10;

    public TransactApp(final AssetTransferBasic smartContract) {
        this.smartContract = smartContract;
    }

    public void run() {
        var futures = Stream.generate(this::newCompletableFuture)
                .limit(batchSize)
                .toArray(CompletableFuture[]::new);
        var allComplete = CompletableFuture.allOf(futures);
        allComplete.join();
    }

    private CompletableFuture<Void> newCompletableFuture() {
        return CompletableFuture.runAsync(() -> {
            try {
                transact();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });
    }

    private void transact() throws EndorseException, CommitException, SubmitException, CommitStatusException {
        var asset = newAsset();

        smartContract.createAsset(asset);
        System.out.println("Created new asset " + asset.getId());

        // Transfer randomly 1 in 2 assets to a new owner.
        if (Utils.randomInt(2) == 0) { // checkstyle:ignore-line:MagicNumber
            var newOwner = Utils.differentElement(OWNERS, asset.getOwner());
            var oldOwner = smartContract.transferAsset(asset.getId(), newOwner);
            System.out.println("Transferred asset " + asset.getId() + " from " + oldOwner + " to " + newOwner);
        }

        // Delete randomly 1 in 4 created assets.
        if (Utils.randomInt(4) == 0) { // checkstyle:ignore-line:MagicNumber
            smartContract.deleteAsset(asset.getId());
            System.out.println("Deleted asset " + asset.getId());
        }
    }

    private Asset newAsset() {
        var asset = new Asset(UUID.randomUUID().toString());
        asset.setColor(Utils.randomElement(COLORS));
        asset.setSize(Utils.randomInt(MAX_INITIAL_SIZE) + 1);
        asset.setOwner(Utils.randomElement(OWNERS));
        asset.setAppraisedValue(Utils.randomInt(MAX_INITIAL_VALUE) + 1);
        return asset;
    }
}
