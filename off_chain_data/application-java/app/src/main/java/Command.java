/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import io.grpc.Channel;

public interface Command {
    void run(Channel grpcChannel) throws Exception;
}
