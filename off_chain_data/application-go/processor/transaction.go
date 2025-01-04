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
	funcName := "process"

	channelHeader, err := t.transaction.ChannelHeader()
	if err != nil {
		return fmt.Errorf("in %s: %w", funcName, err)
	}
	transactionId := channelHeader.GetTxId()

	writes, err := t.writes()
	if err != nil {
		return fmt.Errorf("in %s: %w", funcName, err)
	}

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
		return fmt.Errorf("in %s: %w", funcName, err)
	}

	return nil
}

func (t *transaction) writes() ([]store.Write, error) {
	funcName := "writes"
	// TODO this entire code should live in the parser and just return the kvWrite which
	// we then map to store.Write and return
	channelHeader, err := t.transaction.ChannelHeader()
	if err != nil {
		return nil, fmt.Errorf("in %s: %w", funcName, err)
	}
	t.channelName = channelHeader.GetChannelId()

	nsReadWriteSets, err := t.transaction.NamespaceReadWriteSets()
	if err != nil {
		return nil, fmt.Errorf("in %s: %w", funcName, err)
	}

	nonSystemCCReadWriteSets := []*parser.NamespaceReadWriteSet{}
	for _, nsReadWriteSet := range nsReadWriteSets {
		if !t.isSystemChaincode(nsReadWriteSet.Namespace()) {
			nonSystemCCReadWriteSets = append(nonSystemCCReadWriteSets, nsReadWriteSet)
		}
	}

	writes := []store.Write{}
	for _, readWriteSet := range nonSystemCCReadWriteSets {
		namespace := readWriteSet.Namespace()

		kvReadWriteSet, err := readWriteSet.ReadWriteSet()
		if err != nil {
			return nil, fmt.Errorf("in %s: %w", funcName, err)
		}

		for _, kvWrite := range kvReadWriteSet.GetWrites() {
			writes = append(writes, store.Write{
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
