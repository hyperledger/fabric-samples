/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package parser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.peer.TransactionPackage;

class ParsedBlock implements Block {
    private final Common.Block block;
    private final AtomicReference<List<Transaction>> cachedTransactions = new AtomicReference<>();

    ParsedBlock(final Common.Block block) {
        this.block = block;
    }

    @Override
    public long getNumber() {
        return block.getHeader().getNumber();
    }

    @Override
    public List<Transaction> getTransactions() throws InvalidProtocolBufferException {
        return Utils.getCachedProto(cachedTransactions, () -> {
            List<TransactionPackage.TxValidationCode> validationCodes = getTransactionValidationCodes();
            List<Common.Payload> payloads = getPayloads();

            List<Transaction> transactions = new ArrayList<>();
            for (int i = 0; i < payloads.size(); i++) {
                ParsedPayload payload = new ParsedPayload(payloads.get(i), validationCodes.get(i));
                if (payload.isEndorserTransaction()) {
                    transactions.add(new ParsedTransaction(payload));
                }
            }

            return transactions;
        });
    }

    @Override
    public Common.Block toProto() {
        return block;
    }

    private List<Common.Payload> getPayloads() throws InvalidProtocolBufferException {
        List<Common.Payload> payloads = new ArrayList<>();

        for (ByteString envelopeBytes : block.getData().getDataList()) {
            Common.Envelope envelope = Common.Envelope.parseFrom(envelopeBytes);
            Common.Payload payload = Common.Payload.parseFrom(envelope.getPayload());
            payloads.add(payload);
        }

        return payloads;
    }

    private List<TransactionPackage.TxValidationCode> getTransactionValidationCodes() {
        ByteString transactionsFilter = block.getMetadata().getMetadataList().get(Common.BlockMetadataIndex.TRANSACTIONS_FILTER.getNumber());
        return StreamSupport.stream(transactionsFilter.spliterator(), false)
                .map(Byte::intValue)
                .map(TransactionPackage.TxValidationCode::forNumber)
                .collect(Collectors.toList());
    }
}
