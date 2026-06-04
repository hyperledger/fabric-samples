/*
SPDX-License-Identifier: Apache-2.0
*/

package smartcontract

// Asset represents a tradeable asset in the world state.
type Asset struct {
	ID             string `json:"ID"`
	Color          string `json:"Color"`
	Owner          string `json:"Owner"`
	AppraisedValue int    `json:"AppraisedValue"`
	Size           int    `json:"Size"`
}
