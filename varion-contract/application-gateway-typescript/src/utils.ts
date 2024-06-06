/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

export const RED = '\x1b[31m\n';
export const GREEN = '\x1b[32m\n';
export const RESET = '\x1b[0m';

export function parse<T>(data: string): T {
    return JSON.parse(data);
}
