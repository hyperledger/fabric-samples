package parser

import (
	"offChainData/utils"

	"github.com/hyperledger/fabric-protos-go-apiv2/common"
	"github.com/hyperledger/fabric-protos-go-apiv2/peer"
	"google.golang.org/protobuf/proto"
)

type Block struct {
	block           *common.Block
	validationCodes []byte
	transactions    []Transaction
}

func ParseBlock(block *common.Block) Block {
	validationCodes := extractTransactionValidationCodes(block)

	return Block{block, validationCodes, nil}
}

func (b *Block) Number() uint64 {
	header := utils.AssertDefined(b.block.GetHeader(), "missing block header")
	return header.GetNumber()
}

// TODO: needs cache, getPayloads, parsePayload
func (b *Block) Transactions() []Transaction {
	return nil
}

func (b *Block) ToProto() *common.Block {
	return nil
}

func (b *Block) payloads() []*common.Payload {
	var payloads []*common.Payload

	for _, envelopeBytes := range b.block.GetData().GetData() {
		envelope := &common.Envelope{}
		if err := proto.Unmarshal(envelopeBytes, envelope); err != nil {
			panic(err)
		}

		payload := &common.Payload{}
		if err := proto.Unmarshal(envelope.Payload, payload); err != nil {
			panic(err)
		}

		payloads = append(payloads, payload)
	}

	return payloads
}

// TODO not sure about this
func (pb *Block) statusCode(txIndex int) peer.TxValidationCode {
	blockMetadata := utils.AssertDefined(
		pb.block.GetMetadata(),
		"missing block metadata",
	)

	metadata := blockMetadata.GetMetadata()
	if int(common.BlockMetadataIndex_TRANSACTIONS_FILTER) >= len(metadata) {
		return peer.TxValidationCode_INVALID_OTHER_REASON
	}

	statusCodes := metadata[common.BlockMetadataIndex_TRANSACTIONS_FILTER]
	if txIndex >= len(statusCodes) {
		return peer.TxValidationCode_INVALID_OTHER_REASON
	}

	return peer.TxValidationCode(statusCodes[txIndex])
}

func extractTransactionValidationCodes(block *common.Block) []byte {
	metadata := utils.AssertDefined(
		block.GetMetadata(),
		"missing block metadata",
	)

	return utils.AssertDefined(
		metadata.GetMetadata()[common.BlockMetadataIndex_TRANSACTIONS_FILTER],
		"missing transaction validation code",
	)
}
