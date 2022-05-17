/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import java.nio.charset.StandardCharsets;

import org.hyperledger.fabric.protos.ledger.rwset.kvrwset.KvRwset;

/**
 * Description of a ledger write that can be applied to an off-chain data store.
 */
public final class Write {
    private final String channelName;
    private final String namespace;
    private final String key;
    private final boolean isDelete;
    private final String value; // Store as String for readability when serialized to JSON.

    public Write(final String channelName, final String namespace, final KvRwset.KVWrite write) {
        this.channelName = channelName;
        this.namespace = namespace;
        this.key = write.getKey();
        this.isDelete = write.getIsDelete();
        this.value = write.getValue().toString(StandardCharsets.UTF_8);
    }

    /**
     * Channel whose ledger is being updated.
     * @return A channel name.
     */
    public String getChannelName() {
        return channelName;
    }

    /**
     * Key name within the ledger namespace.
     * @return A ledger key.
     */
    public String getKey() {
        return key;
    }

    /**
     * Whether the key and associated value are being deleted.
     * @return {@code true} if the ledger key is being deleted; otherwise {@code false}.
     */
    public boolean isDelete() {
        return isDelete;
    }

    /**
     * Namespace within the ledger.
     * @return A ledger namespace.
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * If {@link #isDelete()}` is {@code false}, the value written to the key; otherwise ignored.
     * @return A ledger value.
     */
    public byte[] getValue() {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
