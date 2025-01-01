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

func (b *block) Process() {
	blockNumber := b.parsedBlock.Number()

	fmt.Println("\nReceived block", blockNumber)

	for _, validTransaction := range b.validTransactions() {
		aTransaction := transaction{
			blockNumber,
			validTransaction,
			// TODO use pointer to parent and get blockNumber, store and channelName from parent
			b.writeToStore,
			b.channelName,
		}
		aTransaction.process()

		transactionId := validTransaction.ChannelHeader().GetTxId()
		b.checkpointer.CheckpointTransaction(blockNumber, transactionId)
	}

	b.checkpointer.CheckpointBlock(b.parsedBlock.Number())
}

func (b *block) validTransactions() []*parser.Transaction {
	result := []*parser.Transaction{}
	for _, transaction := range b.getNewTransactions() {
		if transaction.IsValid() {
			result = append(result, transaction)
		}
	}
	return result
}

func (b *block) getNewTransactions() []*parser.Transaction {
	transactions := b.parsedBlock.Transactions()

	lastTransactionId := b.checkpointer.TransactionID()
	if lastTransactionId == "" {
		// No previously processed transactions within this block so all are new
		return transactions
	}

	// Ignore transactions up to the last processed transaction ID
	lastProcessedIndex := b.findLastProcessedIndex()
	return transactions[lastProcessedIndex+1:]
}

func (b *block) findLastProcessedIndex() int {
	blockTransactionIds := []string{}
	for _, transaction := range b.parsedBlock.Transactions() {
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
				b.parsedBlock.Number(),
				b.joinByComma(blockTransactionIds),
			),
		)
	}
	return lastProcessedIndex
}

func (b *block) joinByComma(list []string) string {
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
