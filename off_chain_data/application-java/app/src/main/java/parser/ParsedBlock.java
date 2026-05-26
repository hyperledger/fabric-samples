/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package parser;

import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperledger.fabric.protos.common.BlockMetadataIndex;
import org.hyperledger.fabric.protos.common.Envelope;
import org.hyperledger.fabric.protos.common.Payload;
import org.hyperledger.fabric.protos.peer.TxValidationCode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

class ParsedBlock implements Block {
    private final org.hyperledger.fabric.protos.common.Block block;
    private final AtomicReference<List<Transaction>> cachedTransactions = new AtomicReference<>();

    ParsedBlock(final org.hyperledger.fabric.protos.common.Block block) {
        this.block = block;
    }

    @Override
    public long getNumber() {
        return block.getHeader().getNumber();
    }

    @Override
    public List<Transaction> getTransactions() throws InvalidProtocolBufferException {
        return Utils.getCachedProto(cachedTransactions, () -> {
            var validationCodes = getTransactionValidationCodes();
            var payloads = getPayloads();

            var transactions = new ArrayList<Transaction>();
            for (int i = 0; i < payloads.size(); i++) {
                var payload = new ParsedPayload(payloads.get(i), validationCodes.get(i));
                if (payload.isEndorserTransaction()) {
                    transactions.add(new ParsedTransaction(payload));
                }
            }

            return transactions;
        });
    }

    @Override
    public org.hyperledger.fabric.protos.common.Block toProto() {
        return block;
    }

    private List<Payload> getPayloads() throws InvalidProtocolBufferException {
        var payloads = new ArrayList<Payload>();

        for (var envelopeBytes : block.getData().getDataList()) {
            var envelope = Envelope.parseFrom(envelopeBytes);
            var payload = Payload.parseFrom(envelope.getPayload());
            payloads.add(payload);
        }

        return payloads;
    }

    private List<TxValidationCode> getTransactionValidationCodes() {
        var transactionsFilter = block.getMetadata().getMetadataList().get(BlockMetadataIndex.TRANSACTIONS_FILTER.getNumber());
        return StreamSupport.stream(transactionsFilter.spliterator(), false)
                .map(Byte::intValue)
                .map(TxValidationCode::forNumber)
                .collect(Collectors.toList());
    }
}
