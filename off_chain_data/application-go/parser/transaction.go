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
	return t.payload.channelHeader()
}

func (t *Transaction) NamespaceReadWriteSets() []*NamespaceReadWriteSet {
	result := []*NamespaceReadWriteSet{}
	for _, readWriteSet := range t.payload.endorserTransaction().readWriteSets() {
		result = append(result, readWriteSet.namespaceReadWriteSets()...)
	}

	return result
}

func (t *Transaction) IsValid() bool {
	return t.payload.isValid()
}
