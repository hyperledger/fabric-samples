package main

import (
	"net/http"
	"os"
	"os/signal"
	"syscall"

	"github.com/hyperledger/fabric-samples/token-sdk/auditor/routes"
	"github.com/hyperledger/fabric-samples/token-sdk/auditor/service"

	"github.com/hyperledger-labs/fabric-smart-client/pkg/api"
	"github.com/hyperledger-labs/fabric-smart-client/pkg/node"
	fabric "github.com/hyperledger-labs/fabric-smart-client/platform/fabric/sdk"
	viewregistry "github.com/hyperledger-labs/fabric-smart-client/platform/view"
	"github.com/hyperledger-labs/fabric-smart-client/platform/view/services/flogging"
	tokensdk "github.com/hyperledger-labs/fabric-token-sdk/token/sdk"
	"github.com/hyperledger-labs/fabric-token-sdk/token/services/ttx"
)

var logger = flogging.MustGetLogger("main")

func main() {
	dir := getEnv("CONF_DIR", "./conf")
	port := getEnv("PORT", "9000")

	fsc := startFabricSmartClient(dir)
	// Tell the service how to respond to other nodes when they initiate an action
	registry := viewregistry.GetRegistry(fsc)
	succeedOrPanic(registry.RegisterResponder(&service.AuditView{}, &ttx.AuditingViewInitiator{}))

	controller := routes.Controller{Service: service.TokenService{FSC: fsc}}
	err := routes.StartWebServer(port, controller, logger)
	if err != nil {
		if err == http.ErrServerClosed {
			logger.Infof("Webserver closing, exiting...", err.Error())
			fsc.Stop()
		} else {
			logger.Fatalf("echo error - %s", err.Error())
			fsc.Stop()
			os.Exit(1)
		}
	}
}

type Node interface {
	api.ServiceProvider
	Stop()
}

func startFabricSmartClient(confDir string) Node {
	logger.Infof("Initializing Fabric Smart Client and Token SDK...")
	fsc := node.NewFromConfPath(confDir)
	succeedOrPanic(fsc.InstallSDK(fabric.NewSDK(fsc)))
	succeedOrPanic(fsc.InstallSDK(tokensdk.NewSDK(fsc)))
	succeedOrPanic(fsc.Start())

	// Stop gracefully
	go handleSignals((map[os.Signal]func(){
		syscall.SIGINT: func() {
			logger.Info("Stopping FSC node...")
			fsc.Stop()
			os.Exit(130)
		},
		syscall.SIGTERM: func() {
			logger.Info("Stopping FSC node...")
			fsc.Stop()
			os.Exit(143)
		},
		syscall.SIGSTOP: func() {
			logger.Info("Stopping FSC node...")
			fsc.Stop()
			os.Exit(145)
		},
		syscall.SIGHUP: func() {
			logger.Info("Stopping FSC node...")
			fsc.Stop()
			os.Exit(129)
		},
	}))
	logger.Infof("FSC node is ready!")

	return fsc
}

// getEnv returns an environment variable or the fallback
func getEnv(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	return fallback
}

func succeedOrPanic(err error) {
	if err != nil {
		logger.Fatalf("Failed initializing Token SDK - %s", err.Error())
		os.Exit(1)
	}
}

func handleSignals(handlers map[os.Signal]func()) {
	var signals []os.Signal
	for sig := range handlers {
		signals = append(signals, sig)
	}

	signalChan := make(chan os.Signal, 1)
	signal.Notify(signalChan, signals...)

	for sig := range signalChan {
		logger.Infof("Received signal: %d (%s)", sig, sig)
		handlers[sig]()
	}
}
