/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package parser;

import java.util.concurrent.atomic.AtomicReference;

import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.peer.TransactionPackage;

class ParsedPayload {
    private final Common.Payload payload;
    private final TransactionPackage.TxValidationCode statusCode;
    private final AtomicReference<Common.ChannelHeader> cachedChannelHeader = new AtomicReference<>();

    ParsedPayload(final Common.Payload payload, final TransactionPackage.TxValidationCode statusCode) {
        this.payload = payload;
        this.statusCode = statusCode;
    }

    public Common.ChannelHeader getChannelHeader() throws InvalidProtocolBufferException {
        return Utils.getCachedProto(cachedChannelHeader, () -> Common.ChannelHeader.parseFrom(payload.getHeader().getChannelHeader()));
    }

    public TransactionPackage.TxValidationCode getValidationCode() {
        return statusCode;
    }

    public boolean isValid() {
        return statusCode == TransactionPackage.TxValidationCode.VALID;
    }

    public Common.Payload toProto() {
        return payload;
    }

    public boolean isEndorserTransaction() throws InvalidProtocolBufferException {
        return getChannelHeader().getType() == Common.HeaderType.ENDORSER_TRANSACTION_VALUE;
    }
}
