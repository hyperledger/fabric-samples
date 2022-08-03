/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package parser;

import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperledger.fabric.protos.common.ChannelHeader;
import org.hyperledger.fabric.protos.common.HeaderType;
import org.hyperledger.fabric.protos.common.Payload;
import org.hyperledger.fabric.protos.common.SignatureHeader;
import org.hyperledger.fabric.protos.peer.TxValidationCode;

import java.util.concurrent.atomic.AtomicReference;

class ParsedPayload {
    private final Payload payload;
    private final TxValidationCode statusCode;
    private final AtomicReference<ChannelHeader> cachedChannelHeader = new AtomicReference<>();
    private final AtomicReference<SignatureHeader> cachedSignatureHeader = new AtomicReference<>();

    ParsedPayload(final Payload payload, final TxValidationCode statusCode) {
        this.payload = payload;
        this.statusCode = statusCode;
    }

    public ChannelHeader getChannelHeader() throws InvalidProtocolBufferException {
        return Utils.getCachedProto(cachedChannelHeader, () -> ChannelHeader.parseFrom(payload.getHeader().getChannelHeader()));
    }

    public SignatureHeader getSignatureHeader() throws InvalidProtocolBufferException {
        return Utils.getCachedProto(cachedSignatureHeader, () -> SignatureHeader.parseFrom(payload.getHeader().getSignatureHeader()));
    }

    public TxValidationCode getValidationCode() {
        return statusCode;
    }

    public boolean isValid() {
        return statusCode == TxValidationCode.VALID;
    }

    public Payload toProto() {
        return payload;
    }

    public boolean isEndorserTransaction() throws InvalidProtocolBufferException {
        return getChannelHeader().getType() == HeaderType.ENDORSER_TRANSACTION_VALUE;
    }
}
