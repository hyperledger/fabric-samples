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
	return &Block{block, nil}
}

func (b *Block) Number() uint64 {
	header := utils.AssertDefined(b.block.GetHeader(), "missing block header")
	return header.GetNumber()
}

// TODO: needs cache; decompose
func (b *Block) Transactions() []*Transaction {
	envelopes := []*common.Envelope{}
	for _, blockData := range b.block.GetData().GetData() {
		envelope := &common.Envelope{}
		if err := proto.Unmarshal(blockData, envelope); err != nil {
			panic(err)
		}
		envelopes = append(envelopes, envelope)
	}

	commonPayloads := []*common.Payload{}
	for _, envelope := range envelopes {
		commonPayload := &common.Payload{}
		if err := proto.Unmarshal(envelope.GetPayload(), commonPayload); err != nil {
			panic(err)
		}
		commonPayloads = append(commonPayloads, commonPayload)
	}

	validationCodes := b.extractTransactionValidationCodes()
	payloads := []*PayloadImpl{}
	for i, commonPayload := range commonPayloads {
		payload := ParsePayload(
			commonPayload,
			int32(utils.AssertDefined(
				validationCodes[i],
				fmt.Sprint("missing validation code index", i),
			),
			),
		)
		if payload.IsEndorserTransaction() {
			payloads = append(payloads, payload)
		}
	}

	result := []*Transaction{}
	for _, payload := range payloads {
		result = append(result, NewTransaction(payload))
	}

	return result
}

func (b *Block) extractTransactionValidationCodes() []byte {
	metadata := utils.AssertDefined(
		b.block.GetMetadata(),
		"missing block metadata",
	)

	return utils.AssertDefined(
		metadata.GetMetadata()[common.BlockMetadataIndex_TRANSACTIONS_FILTER],
		"missing transaction validation code",
	)
}

// TODO remove unused?
func (b *Block) ToProto() *common.Block {
	return b.block
}
