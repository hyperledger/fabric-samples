/*
 * SPDX-License-Identifier: Apache-2.0
 */

package chaincode

// Nft defines the non-fungible token struct used by chaincode.
type Nft struct {
	TokenID  string `json:"tokenId"`
	Owner    string `json:"owner"`
	TokenURI string `json:"tokenURI"`
	Approved string `json:"approved"`
}

type Approval struct {
	Owner    string `json:"owner"`
	Operator string `json:"operator"`
	Approved bool   `json:"approved"`
}

type Transfer struct {
	From    string `json:"from"`
	To      string `json:"to"`
	TokenID string `json:"tokenId"`
}
