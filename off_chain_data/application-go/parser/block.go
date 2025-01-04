package parser

import (
	"fmt"
	"offChainData/utils"

	"github.com/hyperledger/fabric-protos-go-apiv2/common"
	"google.golang.org/protobuf/proto"
)

type Block struct {
	block        *common.Block
	transactions []*Transaction
}

func ParseBlock(block *common.Block) *Block {
	return &Block{block, []*Transaction{}}
}

func (b *Block) Number() (uint64, error) {
	header, err := utils.AssertDefined(b.block.GetHeader(), "missing block header")
	if err != nil {
		return 0, fmt.Errorf("in Number: %w", err)
	}
	return header.GetNumber(), nil
}

func (b *Block) Transactions() ([]*Transaction, error) {
	return utils.Cache(func() ([]*Transaction, error) {
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

		return b.createTransactionsFrom(payloads), nil
	})()
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

	validationCodes, err := b.extractTransactionValidationCodes()
	if err != nil {
		return nil, fmt.Errorf("in %s: %w", funcName, err)
	}

	result := []*payload{}
	for i, commonPayload := range commonPayloads {
		statusCode, err := utils.AssertDefined(
			validationCodes[i],
			fmt.Sprint("missing validation code index", i),
		)
		if err != nil {
			return nil, fmt.Errorf("in %s: %w", funcName, err)
		}

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

func (b *Block) extractTransactionValidationCodes() ([]byte, error) {
	metadata, err := utils.AssertDefined(
		b.block.GetMetadata(),
		"missing block metadata",
	)
	if err != nil {
		return nil, fmt.Errorf("in extractTransactionValidationCodes: %w", err)
	}

	return utils.AssertDefined(
		metadata.GetMetadata()[common.BlockMetadataIndex_TRANSACTIONS_FILTER],
		"missing transaction validation code",
	)
}

func (*Block) createTransactionsFrom(payloads []*payload) []*Transaction {
	result := []*Transaction{}
	for _, payload := range payloads {
		result = append(result, newTransaction(payload))
	}
	return result
}
