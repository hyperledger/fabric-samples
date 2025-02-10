package main

import (
	"context"
	"fmt"
	"offchaindata/parser"
	"os"
	"os/signal"
	"slices"
	"strings"
	"syscall"

	"github.com/hyperledger/fabric-gateway/pkg/client"
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
	if simulatedFailureCount > 0 {
		fmt.Printf("Simulating a write failure every %d transactions\n", simulatedFailureCount)
	}

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
			applyWritesToOffChainStore,
			channelName,
		}

		if err := aBlockProcessor.process(); err != nil {
			return err
		}
	}

	fmt.Println("\nShutting down listener gracefully...")
	return nil
}

type blockProcessor struct {
	parsedBlock  *parser.Block
	checkpointer *client.FileCheckpointer
	writeToStore writer
	channelName  string
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
			// TODO use reference to parent and get blockNumber, store and channelName from parent
			b.writeToStore,
			b.channelName,
		}
		if err := txProcessor.process(); err != nil {
			return err
		}

		channelHeader, err := validTransaction.ChannelHeader()
		if err != nil {
			return err
		}
		transactionID := channelHeader.GetTxId()
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
	result := []*parser.Transaction{}
	newTransactions, err := b.getNewTransactions()
	if err != nil {
		return nil, err
	}

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
		channelHeader, err := transaction.ChannelHeader()
		if err != nil {
			return 0, err
		}
		blockTransactionIDs = append(blockTransactionIDs, channelHeader.GetTxId())
	}

	lastTransactionID := b.checkpointer.TransactionID()
	lastProcessedIndex := -1
	for index, id := range blockTransactionIDs {
		if id == lastTransactionID {
			lastProcessedIndex = index
		}
	}

	if lastProcessedIndex < 0 {
		return lastProcessedIndex, newTxIDNotFoundError(
			lastTransactionID,
			b.parsedBlock.Number(),
			blockTransactionIDs,
		)
	}

	return lastProcessedIndex, nil
}

type transactionProcessor struct {
	blockNumber  uint64
	transaction  *parser.Transaction
	writeToStore writer
	channelName  string
}

func (t *transactionProcessor) process() error {
	channelHeader, err := t.transaction.ChannelHeader()
	if err != nil {
		return err
	}
	transactionID := channelHeader.GetTxId()

	writes, err := t.writes()
	if err != nil {
		return err
	}

	if len(writes) == 0 {
		fmt.Println("Skipping read-only or system transaction", transactionID)
		return nil
	}

	fmt.Println("Process transaction", transactionID)

	if err := t.writeToStore(ledgerUpdate{
		BlockNumber:   t.blockNumber,
		TransactionID: transactionID,
		Writes:        writes,
	}); err != nil {
		return err
	}

	return nil
}

func (t *transactionProcessor) writes() ([]write, error) {
	// TODO this entire code should live in the parser and just return the kvWrite which
	// we then map to write and return
	channelHeader, err := t.transaction.ChannelHeader()
	if err != nil {
		return nil, err
	}
	t.channelName = channelHeader.GetChannelId()

	nsReadWriteSets, err := t.transaction.NamespaceReadWriteSets()
	if err != nil {
		return nil, err
	}

	nonSystemCCReadWriteSets := []*parser.NamespaceReadWriteSet{}
	for _, nsReadWriteSet := range nsReadWriteSets {
		if !t.isSystemChaincode(nsReadWriteSet.Namespace()) {
			nonSystemCCReadWriteSets = append(nonSystemCCReadWriteSets, nsReadWriteSet)
		}
	}

	writes := []write{}
	for _, readWriteSet := range nonSystemCCReadWriteSets {
		namespace := readWriteSet.Namespace()

		kvReadWriteSet, err := readWriteSet.ReadWriteSet()
		if err != nil {
			return nil, err
		}

		for _, kvWrite := range kvReadWriteSet.GetWrites() {
			writes = append(writes, write{
				ChannelName: t.channelName,
				Namespace:   namespace,
				Key:         kvWrite.GetKey(),
				IsDelete:    kvWrite.GetIsDelete(),
				Value:       string(kvWrite.GetValue()), // Convert bytes to text, purely for readability in output
			})
		}
	}

	return writes, nil
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

type txIDNotFoundError struct {
	txID        string
	blockNumber uint64
	blockTxIDs  []string
}

func newTxIDNotFoundError(txID string, blockNumber uint64, blockTxIds []string) *txIDNotFoundError {
	return &txIDNotFoundError{
		txID, blockNumber, blockTxIds,
	}
}

func (t *txIDNotFoundError) Error() string {
	format := "checkpoint transaction ID %s not found in block %d containing transactions: %s"
	return fmt.Sprintf(format, t.txID, t.blockNumber, strings.Join(t.blockTxIDs, ", "))
}
