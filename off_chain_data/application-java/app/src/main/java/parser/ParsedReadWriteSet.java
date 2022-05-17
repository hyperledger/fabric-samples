/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package parser;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperledger.fabric.protos.ledger.rwset.Rwset;
import org.hyperledger.fabric.protos.ledger.rwset.kvrwset.KvRwset;

class ParsedReadWriteSet implements NamespaceReadWriteSet {
    private final Rwset.NsReadWriteSet readWriteSet;
    private final AtomicReference<KvRwset.KVRWSet> cachedReadWriteSet = new AtomicReference<>();

    static List<ParsedReadWriteSet> fromTxReadWriteSet(final Rwset.TxReadWriteSet readWriteSet) {
        Rwset.TxReadWriteSet.DataModel dataModel = readWriteSet.getDataModel();
        if (dataModel != Rwset.TxReadWriteSet.DataModel.KV) {
            throw new IllegalArgumentException("Unexpected read/write set data model: " + dataModel.name());
        }

        return readWriteSet.getNsRwsetList().stream()
                .map(ParsedReadWriteSet::new)
                .collect(Collectors.toList());
    }

    ParsedReadWriteSet(final Rwset.NsReadWriteSet readWriteSet) {
        this.readWriteSet = readWriteSet;
    }

    @Override
    public String getNamespace() {
        return readWriteSet.getNamespace();
    }

    @Override
    public KvRwset.KVRWSet getReadWriteSet() throws InvalidProtocolBufferException {
        return Utils.getCachedProto(cachedReadWriteSet, () -> KvRwset.KVRWSet.parseFrom(readWriteSet.getRwset()));
    }

    @Override
    public Rwset.NsReadWriteSet toProto() {
        return readWriteSet;
    }
}
