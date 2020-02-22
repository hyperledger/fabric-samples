/*
SPDX-License-Identifier: Apache-2.0
*/

package org.example.ledgerapi;

@FunctionalInterface
public interface StateDeserializer {
    State deserialize(byte[] buffer);
}
