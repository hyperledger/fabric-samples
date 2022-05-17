/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package parser;

import org.hyperledger.fabric.protos.common.Common;

public final class BlockParser {
    public static Block parseBlock(final Common.Block block) {
        return new ParsedBlock(block);
    }

    private BlockParser() { }
}
