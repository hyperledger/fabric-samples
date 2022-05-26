/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package parser;

import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperledger.fabric.protos.ledger.rwset.Rwset;
import org.hyperledger.fabric.protos.peer.ProposalPackage;
import org.hyperledger.fabric.protos.peer.ProposalResponsePackage;
import org.hyperledger.fabric.protos.peer.TransactionPackage;

final class ParsedTransactionAction {
    private final TransactionPackage.TransactionAction transactionAction;

    ParsedTransactionAction(final TransactionPackage.TransactionAction transactionAction) {
        this.transactionAction = transactionAction;
    }

    public List<ParsedReadWriteSet> getReadWriteSets() throws InvalidProtocolBufferException {
        return ParsedReadWriteSet.fromTxReadWriteSet(getTxReadWriteSet());
    }

    private Rwset.TxReadWriteSet getTxReadWriteSet() throws InvalidProtocolBufferException {
        return Rwset.TxReadWriteSet.parseFrom(getChaincodeAction().getResults());
    }

    private ProposalPackage.ChaincodeAction getChaincodeAction() throws InvalidProtocolBufferException {
        return ProposalPackage.ChaincodeAction.parseFrom(getProposalResponsePayload().getExtension());
    }

    private ProposalResponsePackage.ProposalResponsePayload getProposalResponsePayload() throws InvalidProtocolBufferException {
        return ProposalResponsePackage.ProposalResponsePayload.parseFrom(getChaincodeActionPayload().getAction().getProposalResponsePayload());
    }

    private TransactionPackage.ChaincodeActionPayload getChaincodeActionPayload() throws InvalidProtocolBufferException {
        return TransactionPackage.ChaincodeActionPayload.parseFrom(transactionAction.getPayload());
    }

    public TransactionPackage.TransactionAction toProto() {
        return transactionAction;
    }
}
