/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package parser;

import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperledger.fabric.protos.ledger.rwset.NsReadWriteSet;
import org.hyperledger.fabric.protos.ledger.rwset.TxReadWriteSet;
import org.hyperledger.fabric.protos.ledger.rwset.kvrwset.KVRWSet;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

class ParsedReadWriteSet implements NamespaceReadWriteSet {
    private final NsReadWriteSet readWriteSet;
    private final AtomicReference<KVRWSet> cachedReadWriteSet = new AtomicReference<>();

    static List<ParsedReadWriteSet> fromTxReadWriteSet(final TxReadWriteSet readWriteSet) {
        var dataModel = readWriteSet.getDataModel();
        if (dataModel != TxReadWriteSet.DataModel.KV) {
            throw new IllegalArgumentException("Unexpected read/write set data model: " + dataModel.name());
        }

        return readWriteSet.getNsRwsetList().stream()
                .map(ParsedReadWriteSet::new)
                .collect(Collectors.toList());
    }

    ParsedReadWriteSet(final NsReadWriteSet readWriteSet) {
        this.readWriteSet = readWriteSet;
    }

    @Override
    public String getNamespace() {
        return readWriteSet.getNamespace();
    }

    @Override
    public KVRWSet getReadWriteSet() throws InvalidProtocolBufferException {
        return Utils.getCachedProto(cachedReadWriteSet, () -> KVRWSet.parseFrom(readWriteSet.getRwset()));
    }

    @Override
    public NsReadWriteSet toProto() {
        return readWriteSet;
    }
}
