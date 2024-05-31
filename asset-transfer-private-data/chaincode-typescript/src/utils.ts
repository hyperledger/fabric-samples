/*
  SPDX-License-Identifier: Apache-2.0
*/

export function nonEmptyString(arg: unknown, errorMessage: string): string {
    if (typeof arg !== "string" || arg.length === 0) {
        throw new Error(errorMessage);
    }

    return arg;
}

export function positiveNumber(arg: unknown, errorMessage: string): number {
    if (typeof arg !== "number" || arg < 1) {
        throw new Error(errorMessage);
    }

    return arg;
}
