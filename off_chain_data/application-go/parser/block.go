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

func (b *Block) Transactions() []*Transaction {
	return utils.Cache(func() []*Transaction {
		envelopes := b.unmarshalEnvelopesFromBlockData()

		commonPayloads := b.unmarshalPayloadsFrom(envelopes)

		payloads := b.parse(commonPayloads)

		return b.createTransactionsFrom(payloads)
	})()
}

func (b *Block) unmarshalEnvelopesFromBlockData() []*common.Envelope {
	result := []*common.Envelope{}
	for _, blockData := range b.block.GetData().GetData() {
		envelope := &common.Envelope{}
		if err := proto.Unmarshal(blockData, envelope); err != nil {
			panic(err)
		}
		result = append(result, envelope)
	}
	return result
}

func (*Block) unmarshalPayloadsFrom(envelopes []*common.Envelope) []*common.Payload {
	result := []*common.Payload{}
	for _, envelope := range envelopes {
		commonPayload := &common.Payload{}
		if err := proto.Unmarshal(envelope.GetPayload(), commonPayload); err != nil {
			panic(err)
		}
		result = append(result, commonPayload)
	}
	return result
}

func (b *Block) parse(commonPayloads []*common.Payload) []*PayloadImpl {
	validationCodes := b.extractTransactionValidationCodes()
	result := []*PayloadImpl{}
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
			result = append(result, payload)
		}
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

func (*Block) createTransactionsFrom(payloads []*PayloadImpl) []*Transaction {
	result := []*Transaction{}
	for _, payload := range payloads {
		result = append(result, NewTransaction(payload))
	}
	return result
}

// TODO remove unused?
func (b *Block) ToProto() *common.Block {
	return b.block
}
