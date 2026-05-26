/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package parser;

import com.google.protobuf.InvalidProtocolBufferException;

import java.util.List;

public interface Block {
    long getNumber();
    List<Transaction> getTransactions() throws InvalidProtocolBufferException;
    org.hyperledger.fabric.protos.common.Block toProto();
}
