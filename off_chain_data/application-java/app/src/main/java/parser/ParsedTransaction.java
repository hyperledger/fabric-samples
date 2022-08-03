/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package parser;

import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperledger.fabric.protos.common.ChannelHeader;
import org.hyperledger.fabric.protos.common.Payload;
import org.hyperledger.fabric.protos.peer.TxValidationCode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

final class ParsedTransaction implements Transaction {
    private final ParsedPayload payload;
    private final AtomicReference<List<NamespaceReadWriteSet>> cachedNamespaceReadWriteSets = new AtomicReference<>();

    ParsedTransaction(final ParsedPayload payload) {
        this.payload = payload;
    }

    @Override
    public ChannelHeader getChannelHeader() throws InvalidProtocolBufferException {
        return payload.getChannelHeader();
    }

    @Override
    public byte[] getCreator() throws InvalidProtocolBufferException {
        return payload.getSignatureHeader().getCreator().toByteArray();
    }

    @Override
    public TxValidationCode getValidationCode() {
        return payload.getValidationCode();
    }

    @Override
    public boolean isValid() {
        return payload.isValid();
    }

    @Override
    public List<NamespaceReadWriteSet> getNamespaceReadWriteSets() throws InvalidProtocolBufferException {
        return Utils.getCachedProto(cachedNamespaceReadWriteSets, () -> new ArrayList<>(getReadWriteSets()));
    }

    @Override
    public Payload toProto() {
        return payload.toProto();
    }

    private List<ParsedReadWriteSet> getReadWriteSets() throws InvalidProtocolBufferException {
        var results = new ArrayList<ParsedReadWriteSet>();
        for (var action : getTransactionActions()) {
            results.addAll(action.getReadWriteSets());
        }

        return results;
    }

    private List<ParsedTransactionAction> getTransactionActions() throws InvalidProtocolBufferException {
        return getTransaction().getActionsList().stream()
                .map(ParsedTransactionAction::new)
                .collect(Collectors.toList());
    }

    private org.hyperledger.fabric.protos.peer.Transaction getTransaction() throws InvalidProtocolBufferException {
        return org.hyperledger.fabric.protos.peer.Transaction.parseFrom(payload.toProto().getData());
    }
}
