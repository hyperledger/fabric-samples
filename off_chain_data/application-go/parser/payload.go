package parser

import (
	"fmt"
	"offChainData/utils"

	"github.com/hyperledger/fabric-protos-go-apiv2/common"
	"github.com/hyperledger/fabric-protos-go-apiv2/peer"
	"google.golang.org/protobuf/proto"
)

// TODO remove interface, use struct; encapsulate
type Payload interface {
	ChannelHeader() *common.ChannelHeader
	EndorserTransaction() EndorserTransaction
	SignatureHeader() *common.SignatureHeader
	TransactionValidationCode() int32
	IsEndorserTransaction() bool
	IsValid() bool
	ToProto() *common.Payload
}

type PayloadImpl struct {
	payload    *common.Payload
	statusCode int32
}

func ParsePayload(payload *common.Payload, statusCode int32) *PayloadImpl {
	return &PayloadImpl{payload, statusCode}
}

func (p *PayloadImpl) ChannelHeader() *common.ChannelHeader {
	header := utils.AssertDefined(p.payload.GetHeader(), "missing payload header")

	// TODO add cache, return cachedChannelHeader like in blockParser.ts:77
	result := &common.ChannelHeader{}
	if err := proto.Unmarshal(header.GetChannelHeader(), result); err != nil {
		panic(err)
	}

	return result
}

func (p *PayloadImpl) EndorserTransaction() EndorserTransaction {
	if !p.IsEndorserTransaction() {
		panic(fmt.Errorf("unexpected payload type: %d", p.ChannelHeader().GetType()))
	}

	result := &peer.Transaction{}
	if err := proto.Unmarshal(p.payload.GetData(), result); err != nil {
		panic(err)
	}

	return ParseEndorserTransaction(result)
}

func (p *PayloadImpl) SignatureHeader() *common.SignatureHeader {
	header := utils.AssertDefined(p.payload.GetHeader(), "missing payload header")

	// TODO add cache, return cachedSignatureHeader like in blockParser.ts:77
	result := &common.SignatureHeader{}
	if err := proto.Unmarshal(header.GetSignatureHeader(), result); err != nil {
		panic(err)
	}

	return result
}

func (p *PayloadImpl) TransactionValidationCode() int32 {
	return p.statusCode
}

func (p *PayloadImpl) IsEndorserTransaction() bool {
	return p.ChannelHeader().GetType() == int32(common.HeaderType_ENDORSER_TRANSACTION)
}

func (p *PayloadImpl) IsValid() bool {
	return p.statusCode == int32(peer.TxValidationCode_VALID)
}

// TODO remove unused
func (p *PayloadImpl) ToProto() *common.Payload {
	return p.payload
}
