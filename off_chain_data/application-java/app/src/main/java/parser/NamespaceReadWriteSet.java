/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package parser;

import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperledger.fabric.protos.ledger.rwset.Rwset;
import org.hyperledger.fabric.protos.ledger.rwset.kvrwset.KvRwset;

public interface NamespaceReadWriteSet {
    String getNamespace();
    KvRwset.KVRWSet getReadWriteSet() throws InvalidProtocolBufferException;
    Rwset.NsReadWriteSet toProto();
}
