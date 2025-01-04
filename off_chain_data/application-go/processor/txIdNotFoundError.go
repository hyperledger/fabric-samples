package processor

import "fmt"

type txIdNotFoundError struct {
	txId        string
	blockNumber uint64
	blockTxIds  []string
}

func newTxIdNotFoundError(txId string, blockNumber uint64, blockTxIds []string) *txIdNotFoundError {
	return &txIdNotFoundError{
		txId, blockNumber, blockTxIds,
	}
}

func (t *txIdNotFoundError) Error() string {
	format := "checkpoint transaction ID %s not found in block %d containing transactions: %s"
	return fmt.Sprintf(format, t.txId, t.blockNumber, t.blockTxIdsJoinedByComma())
}

func (t *txIdNotFoundError) blockTxIdsJoinedByComma() string {
	result := ""
	for index, item := range t.blockTxIds {
		if len(t.blockTxIds)-1 == index {
			result += item
		} else {
			result += item + ", "
		}
	}
	return result
}
