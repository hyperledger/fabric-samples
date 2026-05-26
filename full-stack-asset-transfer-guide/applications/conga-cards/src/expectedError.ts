/*
 * Copyright contributors to the Hyperledgendary Full Stack Asset Transfer Guide project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

export class ExpectedError extends Error {
    constructor(message?: string) {
        super(message);
        this.name = ExpectedError.name;
    }
}
