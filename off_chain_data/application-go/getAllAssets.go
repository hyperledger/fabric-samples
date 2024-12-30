/*
 * Copyright 2024 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	atb "offChainData/contract"

	"github.com/hyperledger/fabric-gateway/pkg/client"
	"google.golang.org/grpc"
)

func getAllAssets(clientConnection *grpc.ClientConn) {
	id, options := newConnectOptions(clientConnection)
	gateway, err := client.Connect(id, options...)
	if err != nil {
		panic((err))
	}
	defer gateway.Close()

	contract := gateway.GetNetwork(channelName).GetContract(chaincodeName)
	smartContract := atb.NewAssetTransferBasic(contract)
	assets := smartContract.GetAllAssets()

	fmt.Println(formatJSON(assets))
}

func formatJSON(data []byte) string {
	var result bytes.Buffer
	if err := json.Indent(&result, data, "", "  "); err != nil {
		panic(fmt.Errorf("failed to parse JSON: %w", err))
	}
	return result.String()
}
