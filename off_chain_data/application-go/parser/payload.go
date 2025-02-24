package parser

import (
	"fmt"

	"github.com/hyperledger/fabric-protos-go-apiv2/common"
	"github.com/hyperledger/fabric-protos-go-apiv2/peer"
	"google.golang.org/protobuf/proto"
)

type payload struct {
	commonPayload *common.Payload
	statusCode    int32
	channelHeader *common.ChannelHeader
}

func parsePayload(commonPayload *common.Payload, statusCode int32) (*payload, error) {
	channelHeader, err := unmarshalChannelHeaderFrom(commonPayload)
	if err != nil {
		return nil, err
	}
	return &payload{commonPayload, statusCode, channelHeader}, nil
}

func unmarshalChannelHeaderFrom(commonPayload *common.Payload) (*common.ChannelHeader, error) {
	result := &common.ChannelHeader{}
	if err := proto.Unmarshal(commonPayload.GetHeader().GetChannelHeader(), result); err != nil {
		return nil, err
	}
	return result, nil
}

func (p *payload) endorserTransaction() (*endorserTransaction, error) {
	if !p.isEndorserTransaction() {
		return nil, fmt.Errorf("unexpected payload type: %d", p.channelHeader.GetType())
	}

	result := &peer.Transaction{}
	if err := proto.Unmarshal(p.commonPayload.GetData(), result); err != nil {
		return nil, err
	}

	return parseEndorserTransaction(result), nil
}

func (p *payload) isEndorserTransaction() bool {
	return p.channelHeader.GetType() == int32(common.HeaderType_ENDORSER_TRANSACTION)
}

func (p *payload) isValid() bool {
	return p.statusCode == int32(peer.TxValidationCode_VALID)
}
