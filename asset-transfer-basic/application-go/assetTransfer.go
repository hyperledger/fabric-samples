/*
Copyright 2020 IBM All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package main

import (
	"errors"
	"fmt"
	"io/ioutil"
	"os"
	"path/filepath"

	"github.com/hyperledger/fabric-sdk-go/pkg/core/config"
	"github.com/hyperledger/fabric-sdk-go/pkg/gateway"
)

func main() {
	fmt.Println("============ application-golang starts ============")

	os.Setenv("DISCOVERY_AS_LOCALHOST", "true")
	wallet, err := gateway.NewFileSystemWallet("wallet")
	if err != nil {
		fmt.Printf("failed to create wallet: %v\n", err)
		os.Exit(1)
	}

	if !wallet.Exists("appUser") {
		err = populateWallet(wallet)
		if err != nil {
			fmt.Printf("failed to populate wallet contents: %v\n", err)
			os.Exit(1)
		}
	}

	ccpPath := filepath.Join(
		"..",
		"..",
		"test-network",
		"organizations",
		"peerOrganizations",
		"org1.example.com",
		"connection-org1.yaml",
	)

	gw, err := gateway.Connect(
		gateway.WithConfig(config.FromFile(filepath.Clean(ccpPath))),
		gateway.WithIdentity(wallet, "appUser"),
	)
	if err != nil {
		fmt.Printf("failed to connect to gateway: %v\n", err)
		os.Exit(1)
	}
	defer gw.Close()

	network, err := gw.GetNetwork("mychannel")
	if err != nil {
		fmt.Printf("failed to get network: %v\n", err)
		os.Exit(1)
	}

	contract := network.GetContract("basic")

	result, err := contract.EvaluateTransaction("GetAllAssets")
	if err != nil {
		fmt.Printf("failed to evaluate transaction: %v\n", err)
		os.Exit(1)
	}
	fmt.Println(string(result))

	result, err = contract.SubmitTransaction("CreateAsset", "asset13", "yellow", "Tom", "5", "1300")
	if err != nil {
		fmt.Printf("failed to submit transaction: %v\n", err)
		os.Exit(1)
	}
	fmt.Println(string(result))

	result, err = contract.EvaluateTransaction("ReadAsset", "asset4")
	if err != nil {
		fmt.Printf("failed to evaluate transaction: %v\n", err)
		os.Exit(1)
	}
	fmt.Println(string(result))

	_, err = contract.SubmitTransaction("TransferAsset", "asset1", "Tom")
	if err != nil {
		fmt.Printf("Failed to submit transaction: %v\n", err)
		os.Exit(1)
	}

	result, err = contract.EvaluateTransaction("ReadAsset", "asset1")
	if err != nil {
		fmt.Printf("failed to evaluate transaction: %v\n", err)
		os.Exit(1)
	}
	fmt.Println(string(result))
	fmt.Println("============ application-golang ends ============")
}

func populateWallet(wallet *gateway.Wallet) error {
	fmt.Println("============ populate wallet starts ============")
	credPath := filepath.Join(
		"..",
		"..",
		"test-network",
		"organizations",
		"peerOrganizations",
		"org1.example.com",
		"users",
		"User1@org1.example.com",
		"msp",
	)

	certPath := filepath.Join(credPath, "signcerts", "cert.pem")
	// read the certificate pem
	cert, err := ioutil.ReadFile(filepath.Clean(certPath))
	if err != nil {
		return err
	}

	keyDir := filepath.Join(credPath, "keystore")
	// there's a single file in this dir containing the private key
	files, err := ioutil.ReadDir(keyDir)
	if err != nil {
		return err
	}
	if len(files) != 1 {
		return errors.New("keystore folder should have contain one file")
	}
	keyPath := filepath.Join(keyDir, files[0].Name())
	key, err := ioutil.ReadFile(filepath.Clean(keyPath))
	if err != nil {
		return err
	}

	identity := gateway.NewX509Identity("Org1MSP", string(cert), string(key))

	err = wallet.Put("appUser", identity)
	if err != nil {
		return err
	}
	fmt.Println("============ populate wallet ends ============")
	return nil
}
