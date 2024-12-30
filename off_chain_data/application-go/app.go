/*
 * Copyright 2024 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package main

import (
	"errors"
	"fmt"
	"os"
	"strings"

	"google.golang.org/grpc"
)

var allCommands = map[string]func(clientConnection *grpc.ClientConn){
	"getAllAssets": getAllAssets,
	"transact":     transact,
	"listen":       listen,
}

func main() {
	commands := os.Args[1:]
	if len(commands) == 0 {
		printUsage()
		panic(errors.New("missing command"))
	}

	for _, name := range commands {
		if _, exists := allCommands[name]; !exists {
			printUsage()
			panic(fmt.Errorf("unknown command: %s", name))
		}
		fmt.Println("command:", name)
	}

	client := newGrpcConnection()
	defer client.Close()

	for _, name := range commands {
		command := allCommands[name]
		command(client)
	}
}

func printUsage() {
	fmt.Println("Arguments: <command1> [<command2> ...]")
	fmt.Println("Available commands:", availableCommands())
}

func availableCommands() string {
	result := make([]string, len(allCommands))
	i := 0
	for command := range allCommands {
		result[i] = command
		i++
	}

	return strings.Join(result, ", ")
}
