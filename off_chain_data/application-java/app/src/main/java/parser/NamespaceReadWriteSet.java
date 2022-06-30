/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package parser;

import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperledger.fabric.protos.ledger.rwset.NsReadWriteSet;
import org.hyperledger.fabric.protos.ledger.rwset.kvrwset.KVRWSet;

public interface NamespaceReadWriteSet {
    String getNamespace();
    KVRWSet getReadWriteSet() throws InvalidProtocolBufferException;
    NsReadWriteSet toProto();
}
