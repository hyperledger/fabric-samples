package processor

import (
	"fmt"
	"offChainData/parser"
	"offChainData/store"
	"slices"
)

type transaction struct {
	blockNumber  uint64
	transaction  *parser.Transaction
	writeToStore store.Writer
	channelName  string
}

func (t *transaction) process() error {
	transactionId := t.transaction.ChannelHeader().GetTxId()

	writes := t.writes()
	if len(writes) == 0 {
		fmt.Println("Skipping read-only or system transaction", transactionId)
		return nil
	}

	fmt.Println("Process transaction", transactionId)

	if err := t.writeToStore(store.LedgerUpdate{
		BlockNumber:   t.blockNumber,
		TransactionId: transactionId,
		Writes:        writes,
	}); err != nil {
		return err
	}
	return nil
}

func (t *transaction) writes() []store.Write {
	// TODO this entire code should live in the parser and just return the kvWrite which
	// we then map to store.Write and return
	t.channelName = t.transaction.ChannelHeader().GetChannelId()

	nonSystemCCReadWriteSets := []*parser.NamespaceReadWriteSet{}
	for _, nsReadWriteSet := range t.transaction.NamespaceReadWriteSets() {
		if !t.isSystemChaincode(nsReadWriteSet.Namespace()) {
			nonSystemCCReadWriteSets = append(nonSystemCCReadWriteSets, nsReadWriteSet)
		}
	}

	writes := []store.Write{}
	for _, readWriteSet := range nonSystemCCReadWriteSets {
		namespace := readWriteSet.Namespace()

		for _, kvWrite := range readWriteSet.ReadWriteSet().GetWrites() {
			writes = append(writes, store.Write{
				ChannelName: t.channelName,
				Namespace:   namespace,
				Key:         kvWrite.GetKey(),
				IsDelete:    kvWrite.GetIsDelete(),
				Value:       string(kvWrite.GetValue()), // Convert bytes to text, purely for readability in output
			})
		}
	}

	return writes
}

func (t *transaction) isSystemChaincode(chaincodeName string) bool {
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
