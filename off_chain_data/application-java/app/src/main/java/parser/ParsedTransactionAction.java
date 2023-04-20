/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package parser;

import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperledger.fabric.protos.ledger.rwset.TxReadWriteSet;
import org.hyperledger.fabric.protos.peer.ChaincodeAction;
import org.hyperledger.fabric.protos.peer.ChaincodeActionPayload;
import org.hyperledger.fabric.protos.peer.ProposalResponsePayload;
import org.hyperledger.fabric.protos.peer.TransactionAction;

import java.util.List;

final class ParsedTransactionAction {
    private final TransactionAction transactionAction;

    ParsedTransactionAction(final TransactionAction transactionAction) {
        this.transactionAction = transactionAction;
    }

    public List<ParsedReadWriteSet> getReadWriteSets() throws InvalidProtocolBufferException {
        return ParsedReadWriteSet.fromTxReadWriteSet(getTxReadWriteSet());
    }

    private TxReadWriteSet getTxReadWriteSet() throws InvalidProtocolBufferException {
        return TxReadWriteSet.parseFrom(getChaincodeAction().getResults());
    }

    private ChaincodeAction getChaincodeAction() throws InvalidProtocolBufferException {
        return ChaincodeAction.parseFrom(getProposalResponsePayload().getExtension());
    }

    private ProposalResponsePayload getProposalResponsePayload() throws InvalidProtocolBufferException {
        return ProposalResponsePayload.parseFrom(getChaincodeActionPayload().getAction().getProposalResponsePayload());
    }

    private ChaincodeActionPayload getChaincodeActionPayload() throws InvalidProtocolBufferException {
        return ChaincodeActionPayload.parseFrom(transactionAction.getPayload());
    }

    public TransactionAction toProto() {
        return transactionAction;
    }
}
