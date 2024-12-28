package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"math"
	"offChainData/parser"
	"os"
	"slices"
	"strconv"
	"strings"

	"github.com/hyperledger/fabric-gateway/pkg/client"
	"google.golang.org/grpc"
)

var checkpointFile = envOrDefault("CHECKPOINT_FILE", "checkpoint.json")
var storeFile = envOrDefault("STORE_FILE", "store.log")
var simulatedFailureCount = getSimulatedFailureCount()

const startBlock = 0

var transactionCount uint = 0 // Used only to simulate failures

// Apply writes for a given transaction to off-chain data store, ideally in a single operation for fault tolerance.
type store = func(data ledgerUpdate)

// Ledger update made by a specific transaction.
type ledgerUpdate struct {
	blockNumber   uint64
	transactionId string
	writes        []write
}

// Description of a ledger write that can be applied to an off-chain data store.
type write struct {
	// Channel whose ledger is being updated.
	ChannelName string `json:"channelName"`
	// Namespace within the ledger.
	Namespace string `json:"namespace"`
	// Key name within the ledger namespace.
	Key string `json:"key"`
	// Whether the key and associated value are being deleted.
	IsDelete bool `json:"isDelete"`
	// If `isDelete` is false, the Value written to the key; otherwise ignored.
	Value string `json:"value"`
}

// Apply writes for a given transaction to off-chain data store, ideally in a single operation for fault tolerance.
// This implementation just writes to a file.
var applyWritesToOffChainStore = func(data ledgerUpdate) {
	if err := simulateFailureIfRequired(); err != nil {
		fmt.Println("[expected error]: " + err.Error())
		return
	}

	writes := []string{}
	for _, write := range data.writes {
		marshaled, err := json.Marshal(write)
		if err != nil {
			panic(err)
		}

		writes = append(writes, string(marshaled))
	}

	f, err := os.OpenFile(storeFile, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		panic(err)
	}

	if _, err := f.Write([]byte(strings.Join(writes, "\n") + "\n")); err != nil {
		f.Close()
		panic(err)
	}

	if err := f.Close(); err != nil {
		panic(err)
	}
}

func simulateFailureIfRequired() error {
	if simulatedFailureCount > 0 && transactionCount >= simulatedFailureCount {
		transactionCount = 0
		return errors.New("simulated write failure")
	}

	transactionCount += 1

	return nil
}

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
		fmt.Printf("Simulating a write failure every %d transactions\n", simulatedFailureCount)
	}

	// TODO put into infinite loop like in public docs example
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	blocks, err := network.BlockEvents(
		ctx,
		client.WithCheckpoint(checkpointer),
		client.WithStartBlock(startBlock), // Used only if there is no checkpoint block number
	)
	if err != nil {
		panic(err)
	}

	for blockProto := range blocks {
		aBlockProcessor := blockProcessor{
			parser.ParseBlock(blockProto),
			checkpointer,
			applyWritesToOffChainStore,
		}
		aBlockProcessor.process()
	}
}

type blockProcessor struct {
	block        *parser.Block
	checkpointer *client.FileCheckpointer
	storeWrites  store
}

func (b *blockProcessor) process() {
	blockNumber := b.block.Number()

	fmt.Println("Received block", blockNumber)

	for _, transaction := range b.validTransactions() {
		aTransactionProcessor := transactionProcessor{
			blockNumber,
			transaction,
			b.storeWrites,
		}
		aTransactionProcessor.process()

		transactionId := transaction.ChannelHeader().GetTxId()
		b.checkpointer.CheckpointTransaction(blockNumber, transactionId)
	}

	b.checkpointer.CheckpointBlock(b.block.Number())
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

type transactionProcessor struct {
	blockNumber uint64
	transaction *parser.Transaction
	storeWrites store
}

func (t *transactionProcessor) process() {
	transactionId := t.transaction.ChannelHeader().GetTxId()

	writes := t.writes()
	if len(writes) == 0 {
		fmt.Println("Skipping read-only or system transaction", transactionId)
		return
	}

	fmt.Println("Process transaction", transactionId)

	t.storeWrites(ledgerUpdate{
		t.blockNumber,
		transactionId,
		writes,
	})
}

func (t *transactionProcessor) writes() []write {
	channelName = t.transaction.ChannelHeader().GetChannelId()

	nonSystemCCReadWriteSets := []parser.NamespaceReadWriteSet{}
	for _, nsReadWriteSet := range t.transaction.NamespaceReadWriteSets() {
		if !t.isSystemChaincode(nsReadWriteSet.Namespace()) {
			nonSystemCCReadWriteSets = append(nonSystemCCReadWriteSets, nsReadWriteSet)
		}
	}

	writes := []write{}
	for _, readWriteSet := range nonSystemCCReadWriteSets {
		namespace := readWriteSet.Namespace()

		for _, kvWrite := range readWriteSet.ReadWriteSet().GetWrites() {
			writes = append(writes, write{
				channelName,
				namespace,
				kvWrite.GetKey(),
				kvWrite.GetIsDelete(),
				string(kvWrite.GetValue()), // Convert bytes to text, purely for readability in output
			})
		}
	}

	return writes
}

func (t *transactionProcessor) isSystemChaincode(chaincodeName string) bool {
	systemChaincodeNames := []string{
		"_lifecycle",
		"cscc",
		"escc",
		"lscc",
		"qscc",
		"vscc",
	}
	return slices.Contains(systemChaincodeNames, chaincodeName)
}
