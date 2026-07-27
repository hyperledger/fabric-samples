package commands

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"math/big"
	"strings"
	"sync"

	"github.com/hyperledger/fabric-gateway/pkg/client"
)

var (
	colors         = []string{"red", "green", "blue"}
	maxInitialSize = 10
	maxInitialVal  = 1000
)

// cmdTransact runs a batch of concurrent create/update/delete transactions to demonstrate
func cmdTransact(gw *client.Gateway, _ []string) error {
	network := gw.GetNetwork(channelName())
	contract := network.GetContract(chaincodeName())

	smartContract := NewAssetTransfer(contract)
	app := &transactApp{smartContract: smartContract, batchSize: 6}
	return app.run()
}

type transactApp struct {
	smartContract *AssetTransfer
	batchSize     int
}

func (a *transactApp) run() error {
	var wg sync.WaitGroup
	errCh := make(chan error, a.batchSize)

	for i := 0; i < a.batchSize; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if err := a.transact(); err != nil {
				errCh <- err
			}
		}()
	}

	wg.Wait()
	close(errCh)

	var failures []string
	for err := range errCh {
		failures = append(failures, err.Error())
	}
	if len(failures) > 0 {
		return fmt.Errorf("%d failures:\n- %s", len(failures), strings.Join(failures, "\n- "))
	}
	return nil
}

func (a *transactApp) transact() error {
	asset := a.newAsset()

	if err := a.smartContract.CreateAsset(asset); err != nil {
		return err
	}
	fmt.Printf("Created asset %s\n", asset.ID)

	if randomInt(2) == 0 {
		oldColor := asset.Color
		asset.Color = differentElement(colors, oldColor)
		if err := a.smartContract.UpdateAsset(asset); err != nil {
			return err
		}
		fmt.Printf("Updated color of asset %s from %s to %s\n", asset.ID, oldColor, asset.Color)
	}

	if randomInt(4) == 0 {
		if err := a.smartContract.DeleteAsset(asset.ID); err != nil {
			return err
		}
		fmt.Printf("Deleted asset %s\n", asset.ID)
	}

	return nil
}

func (a *transactApp) newAsset() Asset {
	return Asset{
		ID:             randomHexString(8),
		Color:          randomElement(colors),
		Size:           randomInt(maxInitialSize) + 1,
		AppraisedValue: float64(randomInt(maxInitialVal) + 1),
	}
}

func randomHexString(length int) string {
	b := make([]byte, (length+1)/2)
	if _, err := rand.Read(b); err != nil {
		panic(fmt.Sprintf("failed to generate random bytes: %v", err))
	}
	return hex.EncodeToString(b)[:length]
}

func randomInt(max int) int {
	n, err := rand.Int(rand.Reader, big.NewInt(int64(max)))
	if err != nil {
		panic(fmt.Sprintf("failed to generate random int: %v", err))
	}
	return int(n.Int64())
}

func randomElement(values []string) string {
	return values[randomInt(len(values))]
}

func differentElement(values []string, currentValue string) string {
	var candidates []string
	for _, v := range values {
		if v != currentValue {
			candidates = append(candidates, v)
		}
	}
	return randomElement(candidates)
}
