/*
 * Copyright 2024 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package main

import (
	"strconv"

	"github.com/google/uuid"
	"github.com/hyperledger/fabric-gateway/pkg/client"
)

type Asset struct {
	ID             string
	Color          string
	Size           uint64
	Owner          string
	AppraisedValue uint64
}

func NewAsset() Asset {
	id, err := uuid.NewRandom()
	if err != nil {
		panic(err)
	}

	return Asset{
		ID:             id.String(),
		Color:          randomElement(colors),
		Size:           uint64(randomInt(maxInitialSize) + 1),
		Owner:          randomElement(owners),
		AppraisedValue: uint64(randomInt(maxInitialValue) + 1),
	}
}

type assetTransferBasic struct {
	contract *client.Contract
}

func newAssetTransferBasic(contract *client.Contract) *assetTransferBasic {
	return &assetTransferBasic{contract}
}

func (atb *assetTransferBasic) createAsset(anAsset Asset) {
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

func (atb *assetTransferBasic) transferAsset(id, newOwner string) string {
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

func (atb *assetTransferBasic) deleteAsset(id string) {
	if _, err := atb.contract.Submit(
		"DeleteAsset",
		client.WithArguments(
			id,
		),
	); err != nil {
		panic(err)
	}
}

func (atb *assetTransferBasic) getAllAssets() []byte {
	result, err := atb.contract.Evaluate("GetAllAssets")
	if err != nil {
		panic(err)
	}
	return result
}
