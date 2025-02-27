package parser

import (
	"github.com/hyperledger/fabric-protos-go-apiv2/common"
)

type Transaction struct {
	payload *payload
}

func newTransaction(payload *payload) *Transaction {
	return &Transaction{payload}
}

func (t *Transaction) ChannelHeader() *common.ChannelHeader {
	return t.payload.channelHeader
}

func (t *Transaction) NamespaceReadWriteSets() ([]*NamespaceReadWriteSet, error) {
	endorserTransaction, err := t.payload.endorserTransaction()
	if err != nil {
		return nil, err
	}

	txReadWriteSets, err := endorserTransaction.readWriteSets()
	if err != nil {
		return nil, err
	}

	var result []*NamespaceReadWriteSet
	for _, readWriteSet := range txReadWriteSets {
		result = append(result, readWriteSet.namespaceReadWriteSets()...)
	}
	return result, nil
}

func (t *Transaction) IsValid() bool {
	return t.payload.isValid()
}
