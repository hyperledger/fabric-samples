/*
 * Copyright 2024 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package contract

import (
	"offChainData/utils"

	"github.com/google/uuid"
)

var (
	colors = []string{"red", "green", "blue"}
	Owners = []string{"alice", "bob", "charlie"}
)

const (
	maxInitialValue = 1000
	maxInitialSize  = 10
)

type Asset struct {
	ID             string `json:"ID"`
	Color          string `json:"Color"`
	Size           uint64 `json:"Size"`
	Owner          string `json:"Owner"`
	AppraisedValue uint64 `json:"AppraisedValue"`
}

func NewAsset() Asset {
	id, err := uuid.NewRandom()
	if err != nil {
		panic(err)
	}

	return Asset{
		ID:             id.String(),
		Color:          utils.RandomElement(colors),
		Size:           uint64(utils.RandomInt(maxInitialSize) + 1),
		Owner:          utils.RandomElement(Owners),
		AppraisedValue: uint64(utils.RandomInt(maxInitialValue) + 1),
	}
}
