/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

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
        .filter(reason => !!reason);

    if (failures.length > 0) {
        const failMessages = ' - ' + failures.join('\n - ');
        throw new Error(`${failures.length} failures:\n${failMessages}\n`);
    }
}

/**
 * Return the value if it is defined; otherwise thrown an error.
 * @param value A value that might not be defined.
 * @param message Error message if the value is not defined.
 */
export function assertDefined<T>(value: T | null | undefined, message: string): T {
    if (value == undefined) {
        throw new Error(message);
    }

    return value;
}

/**
 * Wrap a function call with a cache. On first call the wrapped function is invoked to obtain a result. Subsequent
 * calls return the cached result.
 * @param f A function whose result should be cached.
 */
export function cache<T>(f: () => T): () => T {
    let value: T | undefined;
    return () => {
        if (value === undefined) {
            value = f();
        }
        return value;
    };
}
