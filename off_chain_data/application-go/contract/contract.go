/*
 * Copyright 2024 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package contract

import (
	"strconv"

	"github.com/hyperledger/fabric-gateway/pkg/client"
)

type AssetTransferBasic struct {
	contract *client.Contract
}

func NewAssetTransferBasic(contract *client.Contract) *AssetTransferBasic {
	return &AssetTransferBasic{contract}
}

func (atb *AssetTransferBasic) CreateAsset(anAsset Asset) {
	if _, err := atb.contract.Submit(
		"CreateAsset",
		client.WithArguments(
			anAsset.ID,
			anAsset.Color,
			strconv.FormatUint(anAsset.Size, 10),
			anAsset.Owner,
			strconv.FormatUint(anAsset.AppraisedValue, 10),
		)); err != nil {
		panic(err)
	}
}

func (atb *AssetTransferBasic) TransferAsset(id, newOwner string) string {
	result, err := atb.contract.Submit(
		"TransferAsset",
		client.WithArguments(
			id,
			newOwner,
		),
	)
	if err != nil {
		panic(err)
	}

	return string(result)
}

func (atb *AssetTransferBasic) DeleteAsset(id string) {
	if _, err := atb.contract.Submit(
		"DeleteAsset",
		client.WithArguments(
			id,
		),
	); err != nil {
		panic(err)
	}
}

func (atb *AssetTransferBasic) GetAllAssets() []byte {
	result, err := atb.contract.Evaluate("GetAllAssets")
	if err != nil {
		panic(err)
	}
	return result
}
