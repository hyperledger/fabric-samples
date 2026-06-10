package main

import (
	"errors"
	"fmt"
	"os"
	"sort"

	"trader-go/commands"
)

func main() {
	if err := run(); err != nil {
		var expectedErr *commands.ExpectedError
		if errors.As(err, &expectedErr) {
			fmt.Println(err)
		} else {
			fmt.Fprintf(os.Stderr, "\nUnexpected application error: %v\n", err)
			os.Exit(1)
		}
	}
}

func run() error {
	args := os.Args[1:]
	if len(args) == 0 {
		printUsage()
		return fmt.Errorf("no command specified")
	}

	commandName := args[0]
	commandArgs := args[1:]

	command, ok := commands.Commands[commandName]
	if !ok {
		printUsage()
		return fmt.Errorf("unknown command: %s", commandName)
	}

	grpcConn, err := newGrpcConnection()
	if err != nil {
		return fmt.Errorf("failed to create gRPC connection: %w", err)
	}
	defer grpcConn.Close()

	gw, err := newGatewayConnection(grpcConn)
	if err != nil {
		return fmt.Errorf("failed to connect to gateway: %w", err)
	}
	defer gw.Close()

	return command(gw, commandArgs)
}

func printUsage() {
	names := make([]string, 0, len(commands.Commands))
	for name := range commands.Commands {
		names = append(names, name)
	}
	sort.Strings(names)

	fmt.Println("Arguments: <command> [<arg1> ...]")
	fmt.Println("Available commands:")
	for _, name := range names {
		fmt.Printf("\t%s\n", name)
	}
}
