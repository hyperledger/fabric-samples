/*
 * SPDX-License-Identifier: Apache-2.0
 */

package chaincode

// Define structs to be used by chaincode
type Nft struct {
	TokenId  string `json:"tokenId"`
	Owner    string `json:"owner"`
	TokenURI string `json:"tokenURI"`
	Approved string `json:"approved"`
	Locked   bool   `json:"locked"`
}

type Approval struct {
	Owner    string `json:"owner"`
	Operator string `json:"operator"`
	Approved bool   `json:"approved"`
}

type Transfer struct {
	From    string `json:"from"`
	To      string `json:"to"`
	TokenId string `json:"tokenId"`
}

type LockUnlock struct {
    Owner   string `json:"owner"`
    TokenId string `json:"tokenId"`
}

type Burn struct {
   Owner   string `json:"owner"`
   TokenId string `json:"tokenId"`
}
