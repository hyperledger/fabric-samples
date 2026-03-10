/*
 * SPDX-License-Identifier: Apache-2.0
 */

package main

// OwnerIdentifier represents the owner of an asset with their organisation MSP ID and user identifier.
// Fields are lowercase to match the TypeScript serialisation format.
type OwnerIdentifier struct {
	Org  string `json:"org"`
	User string `json:"user"`
}

// Asset describes the details of an asset stored in the world state.
// Fields are defined in alphabetical order to produce deterministic JSON serialisation.
type Asset struct {
	AppraisedValue int    `json:"AppraisedValue"`
	Color          string `json:"Color"`
	ID             string `json:"ID"`
	Owner          string `json:"Owner"` // JSON-encoded OwnerIdentifier
	Size           int    `json:"Size"`
}
