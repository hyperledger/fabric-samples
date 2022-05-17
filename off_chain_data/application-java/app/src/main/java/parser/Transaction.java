/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package parser;

import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperledger.fabric.protos.common.Common;
import org.hyperledger.fabric.protos.peer.TransactionPackage;

public interface Transaction {
    Common.ChannelHeader getChannelHeader() throws InvalidProtocolBufferException;
    TransactionPackage.TxValidationCode getValidationCode();
    boolean isValid();
    List<NamespaceReadWriteSet> getNamespaceReadWriteSets() throws InvalidProtocolBufferException;
    Common.Payload toProto();
}
