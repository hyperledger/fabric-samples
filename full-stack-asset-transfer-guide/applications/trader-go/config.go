package main

import (
	"fmt"
	"os"
)

func gatewayEndpoint() (string, error) {
	return requireEnv("ENDPOINT")
}

func mspID() (string, error) {
	return requireEnv("MSP_ID")
}

func clientCertPath() (string, error) {
	return requireEnv("CERTIFICATE")
}

func privateKeyPath() (string, error) {
	return requireEnv("PRIVATE_KEY")
}

func tlsCertPath() string {
	return os.Getenv("TLS_CERT")
}

func channelName() string {
	if v := os.Getenv("CHANNEL_NAME"); v != "" {
		return v
	}
	return "mychannel"
}

func chaincodeName() string {
	if v := os.Getenv("CHAINCODE_NAME"); v != "" {
		return v
	}
	return "asset-transfer"
}

func hostAlias() string {
	return os.Getenv("HOST_ALIAS")
}

func requireEnv(name string) (string, error) {
	v := os.Getenv(name)
	if v == "" {
		printEnvUsage()
		return "", fmt.Errorf("environment variable %s not set", name)
	}
	return v, nil
}

func printEnvUsage() {
	fmt.Fprintln(os.Stderr, "The following environment variables must be set:")
	fmt.Fprintln(os.Stderr, "    ENDPOINT       - Endpoint address of the gateway service")
	fmt.Fprintln(os.Stderr, "    MSP_ID         - User's organization Member Services Provider ID")
	fmt.Fprintln(os.Stderr, "    CERTIFICATE    - User's certificate file")
	fmt.Fprintln(os.Stderr, "    PRIVATE_KEY    - User's private key file")
	fmt.Fprintln(os.Stderr, "")
	fmt.Fprintln(os.Stderr, "The following environment variables are optional:")
	fmt.Fprintln(os.Stderr, "    CHANNEL_NAME   - Channel to which the chaincode is deployed")
	fmt.Fprintln(os.Stderr, "    CHAINCODE_NAME - Chaincode deployed to the channel")
	fmt.Fprintln(os.Stderr, "    TLS_CERT       - TLS CA root certificate (only if using TLS and private CA)")
	fmt.Fprintln(os.Stderr, "    HOST_ALIAS     - TLS hostname override (only if TLS cert does not match endpoint)")
}
