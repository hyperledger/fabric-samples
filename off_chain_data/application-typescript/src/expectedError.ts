/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

export class ExpectedError extends Error {
    constructor(message?: string) {
        super(message);
        this.name = ExpectedError.name;
    }
}
