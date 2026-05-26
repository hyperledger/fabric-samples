/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package parser;

public final class BlockParser {
    public static Block parseBlock(final org.hyperledger.fabric.protos.common.Block block) {
        return new ParsedBlock(block);
    }

    private BlockParser() { }
}
