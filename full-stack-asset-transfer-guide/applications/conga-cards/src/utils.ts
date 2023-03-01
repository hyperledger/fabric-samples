/*
 * Copyright contributors to the Hyperledgendary Full Stack Asset Transfer Guide project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { inspect, TextDecoder } from 'util';

const utf8Decoder = new TextDecoder();

/**
 * Pick a random element from an array.
 * @param values Candidate elements.
 */
export function randomElement<T>(values: T[]): T {
    return values[randomInt(values.length)];
}

/**
 * Generate a random integer in the range 0 to max - 1.
 * @param max Maximum value (exclusive).
 */
export function randomInt(max: number): number {
    return Math.floor(Math.random() * max);
}

/**
 * Pick a random element from an array, excluding the current value.
 * @param values Candidate elements.
 * @param currentValue Value to avoid.
 */
export function differentElement<T>(values: T[], currentValue: T): T {
    const candidateValues = values.filter(value => value !== currentValue);
    return randomElement(candidateValues);
}

/**
 * Wait for all promises to complete, then throw an Error only if any of the promises were rejected.
 * @param promises Promises to be awaited.
 */
export async function allFulfilled(promises: Promise<unknown>[]): Promise<void> {
    const results = await Promise.allSettled(promises);
    const failures = results
        .map(result => result.status === 'rejected' && result.reason as unknown)
        .filter(reason => !!reason)
        .map(reason => inspect(reason));

    if (failures.length > 0) {
        const failMessages = '- ' + failures.join('\n- ');
        throw new Error(`${failures.length} failures:\n${failMessages}\n`);
    }
}

export type PrintView<T> = {
    [K in keyof T]: T[K] extends Uint8Array ? string : T[K];
};

export function printable<T extends object>(event: T): PrintView<T> {
    return Object.fromEntries(
        Object.entries(event).map(([k, v]) => [k, v instanceof Uint8Array ? utf8Decoder.decode(v) : v])
    ) as PrintView<T>;
}

export function assertAllDefined<T>(values: (T | undefined)[], message: string | (() => string)): T[] {
    values.forEach(value => assertDefined(value, message));
    return values as T[];
}

export function assertDefined<T>(value: T | undefined, message: string | (() => string)): T {
    if (value == undefined) {
        throw new Error(typeof message === 'string' ? message : message());
    }

    return value;
}
