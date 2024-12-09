package main

import (
	"context"
	"fmt"
	"math"
	"offChainData/parser"
	"strconv"

	"github.com/hyperledger/fabric-gateway/pkg/client"
	"google.golang.org/grpc"
)

var checkpointFile = envOrDefault("CHECKPOINT_FILE", "checkpoint.json")
var simulatedFailureCount = getSimulatedFailureCount()

func listen(clientConnection *grpc.ClientConn) {
	id, options := newConnectOptions(clientConnection)
	gateway, err := client.Connect(id, options...)
	if err != nil {
		panic(err)
	}
	defer gateway.Close()

	network := gateway.GetNetwork(channelName)

	checkpointer, err := client.NewFileCheckpointer(checkpointFile)
	if err != nil {
		panic(err)
	}
	defer checkpointer.Close()

	fmt.Printf("Start event listening from block %d\n", checkpointer.BlockNumber())
	fmt.Printf("Last processed transaction ID within block: %s\n", checkpointer.TransactionID())
	if simulatedFailureCount > 0 {
		fmt.Printf("Simulating a write failure every %d transactions", simulatedFailureCount)
	}

	// TODO put into infinite loop like in public docs example
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	blocks, err := network.BlockEvents(
		ctx,
		client.WithStartBlock(0),
		client.WithCheckpoint(checkpointer),
	)
	if err != nil {
		panic(err)
	}

	for blockProto := range blocks {
		blockPr := blockProcessor{
			parser.ParseBlock(blockProto),
			checkpointer,
			func(data ledgerUpdate) {},
		}
		blockPr.process()
	}
}

type store = func(data ledgerUpdate)

type ledgerUpdate struct {
	blockNumber   uint64
	transactionId string
	writes        []write
}

type write struct {
	channelName string
	namespace   string
	key         string
	isDelete    bool
	value       []byte
}

type blockProcessor struct {
	block        *parser.Block
	checkpointer *client.FileCheckpointer
	_store       store
}

func (b *blockProcessor) process() {
	blockNumber := b.block.Number()

	fmt.Println("Received block", blockNumber)

	for _, transaction := range b.validTransactions() {
		fmt.Println("CONTINUE HERE:", transaction.ChannelHeader().GetTxId())
	}
}

func (b blockProcessor) validTransactions() []*parser.Transaction {
	result := []*parser.Transaction{}
	for _, transaction := range b.getNewTransactions() {
		if transaction.IsValid() {
			result = append(result, transaction)
		}
	}
	return result
}

func (b *blockProcessor) getNewTransactions() []*parser.Transaction {
	transactions := b.block.Transactions()

	lastTransactionId := b.checkpointer.TransactionID()
	if lastTransactionId == "" {
		// No previously processed transactions within this block so all are new
		return transactions
	}

	// Ignore transactions up to the last processed transaction ID
	lastProcessedIndex := b.findLastProcessedIndex()
	return transactions[lastProcessedIndex+1:]
}

func (b blockProcessor) findLastProcessedIndex() int {
	blockTransactionIds := []string{}
	for _, transaction := range b.block.Transactions() {
		blockTransactionIds = append(blockTransactionIds, transaction.ChannelHeader().GetTxId())
	}

	lastTransactionId := b.checkpointer.TransactionID()
	lastProcessedIndex := -1
	for index, id := range blockTransactionIds {
		if id == lastTransactionId {
			lastProcessedIndex = index
		}
	}
	if lastProcessedIndex < 0 {
		panic(
			fmt.Errorf(
				"checkpoint transaction ID %s not found in block %d containing transactions: %s",
				lastTransactionId,
				b.block.Number(),
				joinByComma(blockTransactionIds),
			),
		)
	}
	return lastProcessedIndex
}

func joinByComma(list []string) string {
	result := ""
	for index, item := range list {
		if len(list)-1 == index {
			result += item
		} else {
			result += item + ", "
		}
	}
	return result
}

func getSimulatedFailureCount() uint {
	valueAsString := envOrDefault("SIMULATED_FAILURE_COUNT", "0")
	valueAsFloat, err := strconv.ParseFloat(valueAsString, 64)
	if err != nil {
		panic(err)
	}

	result := math.Floor(valueAsFloat)
	if valueAsFloat < 0 {
		panic(fmt.Errorf("invalid SIMULATED_FAILURE_COUNT value: %s", valueAsString))
	}

	return uint(result)
}
