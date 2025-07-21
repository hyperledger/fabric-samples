package parser

import (
	"sync"

	"github.com/hyperledger/fabric-protos-go-apiv2/common"
	"google.golang.org/protobuf/proto"
)

type Block struct {
	block        *common.Block
	transactions func() ([]*Transaction, error)
}

func ParseBlock(block *common.Block) *Block {
	result := &Block{block, nil}
	result.transactions = sync.OnceValues(result.unmarshalTransactions)
	return result
}

func (b *Block) Number() uint64 {
	return b.block.GetHeader().GetNumber()
}

func (b *Block) Transactions() ([]*Transaction, error) {
	return b.transactions()
}

func (b *Block) ToProto() *common.Block {
	return b.block
}

func (b *Block) unmarshalTransactions() ([]*Transaction, error) {
	envelopes, err := b.unmarshalEnvelopes()
	if err != nil {
		return nil, err
	}

	commonPayloads, err := b.unmarshalPayloadsFrom(envelopes)
	if err != nil {
		return nil, err
	}

	payloads, err := b.parse(commonPayloads)
	if err != nil {
		return nil, err
	}

	return b.createTransactionsFrom(payloads), nil
}

func (b *Block) unmarshalEnvelopes() ([]*common.Envelope, error) {
	var result []*common.Envelope
	for _, blockData := range b.block.GetData().GetData() {
		envelope := &common.Envelope{}
		if err := proto.Unmarshal(blockData, envelope); err != nil {
			return nil, err
		}
		result = append(result, envelope)
	}
	return result, nil
}

func (*Block) unmarshalPayloadsFrom(envelopes []*common.Envelope) ([]*common.Payload, error) {
	var result []*common.Payload
	for _, envelope := range envelopes {
		commonPayload := &common.Payload{}
		if err := proto.Unmarshal(envelope.GetPayload(), commonPayload); err != nil {
			return nil, err
		}
		result = append(result, commonPayload)
	}
	return result, nil
}

func (b *Block) parse(commonPayloads []*common.Payload) ([]*payload, error) {
	validationCodes := b.block.GetMetadata().GetMetadata()[common.BlockMetadataIndex_TRANSACTIONS_FILTER]

	var result []*payload
	for i, commonPayload := range commonPayloads {
		statusCode := validationCodes[i]

		payload, err := parsePayload(commonPayload, int32(statusCode))
		if err != nil {
			return nil, err
		}

		if payload.isEndorserTransaction() {
			result = append(result, payload)
		}
	}

	return result, nil
}

func (*Block) createTransactionsFrom(payloads []*payload) []*Transaction {
	var result []*Transaction
	for _, payload := range payloads {
		result = append(result, newTransaction(payload))
	}
	return result
}
