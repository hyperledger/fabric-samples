package commands

import "os"

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
