package commands

import "github.com/hyperledger/fabric-gateway/pkg/client"

type Command func(gw *client.Gateway, args []string) error

var Commands = map[string]Command{
	"create":       cmdCreate,
	"delete":       cmdDelete,
	"getAllAssets": cmdGetAllAssets,
	"listen":       cmdListen,
	"read":         cmdRead,
	"transact":     cmdTransact,
	"transfer":     cmdTransfer,
}
