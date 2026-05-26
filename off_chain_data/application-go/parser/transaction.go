package parser

import (
	"github.com/hyperledger/fabric-gateway/pkg/identity"
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

func (t *Transaction) Creator() identity.Identity {
	return t.payload.creator
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

func (t *Transaction) ToProto() *common.Payload {
	return t.payload.commonPayload
}

func (t *Transaction) ValidationCode() int32 {
	return t.payload.statusCode
}
