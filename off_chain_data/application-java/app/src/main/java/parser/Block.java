/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package parser;

import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperledger.fabric.protos.common.Common;

public interface Block {
    long getNumber();
    List<Transaction> getTransactions() throws InvalidProtocolBufferException;
    Common.Block toProto();
}
