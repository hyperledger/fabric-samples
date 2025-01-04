package processor

import (
	"fmt"
	"offChainData/parser"
	"offChainData/store"

	"github.com/hyperledger/fabric-gateway/pkg/client"
)

type block struct {
	parsedBlock  *parser.Block
	checkpointer *client.FileCheckpointer
	writeToStore store.Writer
	channelName  string
}

func NewBlock(
	parsedBlock *parser.Block,
	checkpointer *client.FileCheckpointer,
	writeToStore store.Writer,
	channelName string,
) *block {
	return &block{
		parsedBlock,
		checkpointer,
		writeToStore,
		channelName,
	}
}

func (b *block) Process() error {
	funcName := "Process"

	blockNumber, err := b.parsedBlock.Number()
	if err != nil {
		return fmt.Errorf("in %s: %w", funcName, err)
	}

	fmt.Println("\nReceived block", blockNumber)

	validTransactions, err := b.validTransactions()
	if err != nil {
		return fmt.Errorf("in %s: %w", funcName, err)
	}

	for _, validTransaction := range validTransactions {
		aTransaction := transaction{
			blockNumber,
			validTransaction,
			// TODO use pointer to parent and get blockNumber, store and channelName from parent
			b.writeToStore,
			b.channelName,
		}
		if err := aTransaction.process(); err != nil {
			return fmt.Errorf("in %s: %w", funcName, err)
		}

		channelHeader, err := validTransaction.ChannelHeader()
		if err != nil {
			return fmt.Errorf("in %s: %w", funcName, err)
		}
		transactionId := channelHeader.GetTxId()
		b.checkpointer.CheckpointTransaction(blockNumber, transactionId)
	}

	b.checkpointer.CheckpointBlock(blockNumber)

	return nil
}

func (b *block) validTransactions() ([]*parser.Transaction, error) {
	result := []*parser.Transaction{}
	newTransactions, err := b.getNewTransactions()
	if err != nil {
		return nil, fmt.Errorf("in validTransactions: %w", err)
	}

	for _, transaction := range newTransactions {
		if transaction.IsValid() {
			result = append(result, transaction)
		}
	}
	return result, nil
}

func (b *block) getNewTransactions() ([]*parser.Transaction, error) {
	funcName := "getNewTransactions"

	transactions, err := b.parsedBlock.Transactions()
	if err != nil {
		return nil, fmt.Errorf("in %s: %w", funcName, err)
	}

	lastTransactionId := b.checkpointer.TransactionID()
	if lastTransactionId == "" {
		// No previously processed transactions within this block so all are new
		return transactions, nil
	}

	// Ignore transactions up to the last processed transaction ID
	lastProcessedIndex, err := b.findLastProcessedIndex()
	if err != nil {
		return nil, fmt.Errorf("in %s: %w", funcName, err)
	}
	return transactions[lastProcessedIndex+1:], nil
}

func (b *block) findLastProcessedIndex() (int, error) {
	funcName := "findLastProcessedIndex"

	transactions, err := b.parsedBlock.Transactions()
	if err != nil {
		return 0, fmt.Errorf("in %s: %w", funcName, err)
	}

	blockTransactionIds := []string{}
	for _, transaction := range transactions {
		channelHeader, err := transaction.ChannelHeader()
		if err != nil {
			return 0, fmt.Errorf("in %s: %w", funcName, err)
		}
		blockTransactionIds = append(blockTransactionIds, channelHeader.GetTxId())
	}

	lastTransactionId := b.checkpointer.TransactionID()
	lastProcessedIndex := -1
	for index, id := range blockTransactionIds {
		if id == lastTransactionId {
			lastProcessedIndex = index
		}
	}

	if lastProcessedIndex < 0 {
		blockNumber, err := b.parsedBlock.Number()
		if err != nil {
			return 0, fmt.Errorf("in %s: %w", funcName, err)
		}
		return lastProcessedIndex, newTxIdNotFoundError(
			lastTransactionId,
			blockNumber,
			blockTransactionIds,
		)
	}

	return lastProcessedIndex, nil
}
