package parser

import (
	"fmt"

	"github.com/hyperledger/fabric-protos-go-apiv2/common"
	"google.golang.org/protobuf/proto"
)

type Block struct {
	block              *common.Block
	cachedTransactions []*Transaction
}

func ParseBlock(block *common.Block) *Block {
	return &Block{block, nil}
}

func (b *Block) Number() uint64 {
	return b.block.GetHeader().GetNumber()
}

func (b *Block) Transactions() ([]*Transaction, error) {
	if b.cachedTransactions != nil {
		return b.cachedTransactions, nil
	}

	funcName := "Transactions"
	envelopes, err := b.unmarshalEnvelopesFromBlockData()
	if err != nil {
		return nil, fmt.Errorf("in %s: %w", funcName, err)
	}

	commonPayloads, err := b.unmarshalPayloadsFrom(envelopes)
	if err != nil {
		return nil, fmt.Errorf("in %s: %w", funcName, err)
	}

	payloads, err := b.parse(commonPayloads)
	if err != nil {
		return nil, fmt.Errorf("in %s: %w", funcName, err)
	}

	b.cachedTransactions = b.createTransactionsFrom(payloads)

	return b.cachedTransactions, nil
}

func (b *Block) unmarshalEnvelopesFromBlockData() ([]*common.Envelope, error) {
	result := []*common.Envelope{}
	for _, blockData := range b.block.GetData().GetData() {
		envelope := &common.Envelope{}
		if err := proto.Unmarshal(blockData, envelope); err != nil {
			return nil, fmt.Errorf("in unmarshalEnvelopesFromBlockData: %w", err)
		}
		result = append(result, envelope)
	}
	return result, nil
}

func (*Block) unmarshalPayloadsFrom(envelopes []*common.Envelope) ([]*common.Payload, error) {
	result := []*common.Payload{}
	for _, envelope := range envelopes {
		commonPayload := &common.Payload{}
		if err := proto.Unmarshal(envelope.GetPayload(), commonPayload); err != nil {
			return nil, fmt.Errorf("in unmarshalPayloadsFrom: %w", err)
		}
		result = append(result, commonPayload)
	}
	return result, nil
}

func (b *Block) parse(commonPayloads []*common.Payload) ([]*payload, error) {
	funcName := "parse"

	validationCodes := b.block.GetMetadata().GetMetadata()[common.BlockMetadataIndex_TRANSACTIONS_FILTER]

	result := []*payload{}
	for i, commonPayload := range commonPayloads {
		statusCode := validationCodes[i]

		payload := parsePayload(commonPayload, int32(statusCode))
		is, err := payload.isEndorserTransaction()
		if err != nil {
			return nil, fmt.Errorf("in %s: %w", funcName, err)
		}
		if is {
			result = append(result, payload)
		}
	}

	return result, nil
}

func (*Block) createTransactionsFrom(payloads []*payload) []*Transaction {
	result := []*Transaction{}
	for _, payload := range payloads {
		result = append(result, newTransaction(payload))
	}
	return result
}
