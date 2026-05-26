/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import java.io.IOException;
import java.util.List;

@FunctionalInterface
public interface Store {
    void store(long blockNumber, String transactionId, List<Write> writes) throws IOException;
}
