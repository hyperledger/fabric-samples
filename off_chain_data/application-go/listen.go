package main

import (
	"context"
	"fmt"
	"offchaindata/parser"
	"os"
	"os/signal"
	"slices"
	"strconv"
	"strings"
	"syscall"

	"github.com/hyperledger/fabric-gateway/pkg/client"
	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset/kvrwset"
	"google.golang.org/grpc"
)

func listen(clientConnection grpc.ClientConnInterface) error {
	id, options := newConnectOptions(clientConnection)
	gateway, err := client.Connect(id, options...)
	if err != nil {
		return err
	}
	defer func() {
		gateway.Close()
		fmt.Println("Gateway closed.")
	}()

	checkpointFile := envOrDefault("CHECKPOINT_FILE", "checkpoint.json")
	checkpointer, err := client.NewFileCheckpointer(checkpointFile)
	if err != nil {
		return err
	}
	defer func() {
		checkpointer.Close()
		fmt.Println("Checkpointer closed.")
	}()
	fmt.Println("Start event listening from block", checkpointer.BlockNumber())
	fmt.Println("Last processed transaction ID within block:", checkpointer.TransactionID())

	simulatedFailureCount := initSimulatedFailureCount()
	if simulatedFailureCount > 0 {
		fmt.Printf("Simulating a write failure every %d transactions\n", simulatedFailureCount)
	}
	storeFile := envOrDefault("STORE_FILE", "store.log")
	offChainStore := newOffChainStore(storeFile, simulatedFailureCount)

	ctx, close := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer func() {
		close()
		fmt.Println("Context closed.")
	}()

	network := gateway.GetNetwork(channelName)
	blocks, err := network.BlockEvents(
		ctx,
		// Used only if there is no checkpoint block number.
		// Order matters. WithStartBlock must be set before
		// WithCheckpoint to work.
		client.WithStartBlock(0),
		client.WithCheckpoint(checkpointer),
	)
	if err != nil {
		return err
	}

	for blockProto := range blocks {
		aBlockProcessor := blockProcessor{
			parser.ParseBlock(blockProto),
			checkpointer,
			offChainStore,
		}

		if err := aBlockProcessor.process(); err != nil {
			return err
		}
	}

	fmt.Println("\nShutting down listener gracefully...")
	return nil
}

func initSimulatedFailureCount() uint {
	valueAsString := envOrDefault("SIMULATED_FAILURE_COUNT", "0")
	result, err := strconv.ParseUint(valueAsString, 10, 0)
	if err != nil {
		panic(fmt.Errorf("invalid SIMULATED_FAILURE_COUNT value: %s", valueAsString))
	}

	return uint(result)
}

// Apply writes for a given transaction to off-chain data store, ideally in a single operation for fault tolerance.
type store interface {
	write(ledgerUpdate) error
}

// Ledger update made by a specific transaction.
type ledgerUpdate struct {
	BlockNumber   uint64
	TransactionID string
	Writes        []write
}

// Description of a ledger Write that can be applied to an off-chain data store.
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

type blockProcessor struct {
	parsedBlock  *parser.Block
	checkpointer *client.FileCheckpointer
	store        store
}

func (b *blockProcessor) process() error {
	fmt.Println("\nReceived block", b.parsedBlock.Number())

	validTransactions, err := b.validTransactions()
	if err != nil {
		return err
	}

	for _, validTransaction := range validTransactions {
		txProcessor := transactionProcessor{
			b.parsedBlock.Number(),
			validTransaction,
			b.store,
		}
		if err := txProcessor.process(); err != nil {
			return err
		}

		transactionID := validTransaction.ChannelHeader().GetTxId()
		if err := b.checkpointer.CheckpointTransaction(b.parsedBlock.Number(), transactionID); err != nil {
			return err
		}
	}

	if err := b.checkpointer.CheckpointBlock(b.parsedBlock.Number()); err != nil {
		return err
	}

	return nil
}

func (b *blockProcessor) validTransactions() ([]*parser.Transaction, error) {
	newTransactions, err := b.getNewTransactions()
	if err != nil {
		return nil, err
	}

	result := []*parser.Transaction{}
	for _, transaction := range newTransactions {
		if transaction.IsValid() {
			result = append(result, transaction)
		}
	}
	return result, nil
}

func (b *blockProcessor) getNewTransactions() ([]*parser.Transaction, error) {
	transactions, err := b.parsedBlock.Transactions()
	if err != nil {
		return nil, err
	}

	lastTransactionID := b.checkpointer.TransactionID()
	if lastTransactionID == "" {
		// No previously processed transactions within this block so all are new
		return transactions, nil
	}

	// Ignore transactions up to the last processed transaction ID
	lastProcessedIndex, err := b.findLastProcessedIndex()
	if err != nil {
		return nil, err
	}
	return transactions[lastProcessedIndex+1:], nil
}

func (b *blockProcessor) findLastProcessedIndex() (int, error) {
	transactions, err := b.parsedBlock.Transactions()
	if err != nil {
		return 0, err
	}

	blockTransactionIDs := []string{}
	for _, transaction := range transactions {
		blockTransactionIDs = append(blockTransactionIDs, transaction.ChannelHeader().GetTxId())
	}

	lastTransactionID := b.checkpointer.TransactionID()
	lastProcessedIndex := -1
	for index, id := range blockTransactionIDs {
		if id == lastTransactionID {
			lastProcessedIndex = index
		}
	}

	if lastProcessedIndex < 0 {
		err = fmt.Errorf(
			"checkpoint transaction ID %s not found in block %d containing transactions: %s",
			lastTransactionID,
			b.parsedBlock.Number(),
			strings.Join(blockTransactionIDs, ", "),
		)
		return lastProcessedIndex, err
	}

	return lastProcessedIndex, nil
}

type transactionProcessor struct {
	blockNumber uint64
	transaction *parser.Transaction
	store       store
}

func (t *transactionProcessor) process() error {
	transactionID := t.transaction.ChannelHeader().GetTxId()

	writes, err := t.writes()
	if err != nil {
		return err
	}

	if len(writes) == 0 {
		fmt.Println("Skipping read-only or system transaction", transactionID)
		return nil
	}

	fmt.Println("Process transaction", transactionID)
	if err := t.store.write(ledgerUpdate{
		BlockNumber:   t.blockNumber,
		TransactionID: transactionID,
		Writes:        writes,
	}); err != nil {
		return err
	}

	return nil
}

func (t *transactionProcessor) writes() ([]write, error) {
	nsReadWriteSets, err := t.nonSystemCCReadWriteSets()
	if err != nil {
		return nil, err
	}

	result := []write{}
	for _, nsReadWriteSet := range nsReadWriteSets {
		kvReadWriteSet, err := nsReadWriteSet.ReadWriteSet()
		if err != nil {
			return nil, err
		}

		result = t.newWrites(kvReadWriteSet, nsReadWriteSet.Namespace())
	}

	return result, nil
}

func (t *transactionProcessor) nonSystemCCReadWriteSets() ([]*parser.NamespaceReadWriteSet, error) {
	nsReadWriteSets, err := t.transaction.NamespaceReadWriteSets()
	if err != nil {
		return nil, err
	}

	return slices.DeleteFunc(nsReadWriteSets, func(nsReadWriteSet *parser.NamespaceReadWriteSet) bool {
		return t.isSystemChaincode(nsReadWriteSet.Namespace())
	}), nil
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

func (t *transactionProcessor) newWrites(kvReadWriteSet *kvrwset.KVRWSet, namespace string) []write {
	result := []write{}
	for _, kvWrite := range kvReadWriteSet.GetWrites() {
		result = append(result, write{
			ChannelName: t.transaction.ChannelHeader().GetChannelId(),
			Namespace:   namespace,
			Key:         kvWrite.GetKey(),
			IsDelete:    kvWrite.GetIsDelete(),
			Value:       string(kvWrite.GetValue()), // Convert bytes to text, purely for readability in output
		})
	}

	return result
}
