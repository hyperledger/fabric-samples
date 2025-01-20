package parser

import (
	"fmt"

	"github.com/hyperledger/fabric-protos-go-apiv2/common"
	"github.com/hyperledger/fabric-protos-go-apiv2/peer"
	"google.golang.org/protobuf/proto"
)

type payload struct {
	commonPayload       *common.Payload
	statusCode          int32
	cachedChannelHeader *common.ChannelHeader
}

func parsePayload(commonPayload *common.Payload, statusCode int32) *payload {
	return &payload{commonPayload, statusCode, nil}
}

func (p *payload) channelHeader() (*common.ChannelHeader, error) {
	if p.cachedChannelHeader != nil {
		return p.cachedChannelHeader, nil
	}

	p.cachedChannelHeader = &common.ChannelHeader{}
	if err := proto.Unmarshal(p.commonPayload.GetHeader().GetChannelHeader(), p.cachedChannelHeader); err != nil {
		return nil, fmt.Errorf("in channelHeader: %w", err)
	}

	return p.cachedChannelHeader, nil
}

func (p *payload) endorserTransaction() (*endorserTransaction, error) {
	funcName := "endorserTransaction"

	is, err := p.isEndorserTransaction()
	if err != nil {
		return nil, fmt.Errorf("in %s: %w", funcName, err)
	}
	if !is {
		channelHeader, err := p.channelHeader()
		if err != nil {
			return nil, fmt.Errorf("in %s: %w", funcName, err)
		}
		return nil, fmt.Errorf("unexpected payload type: %d", channelHeader.GetType())
	}

	result := &peer.Transaction{}
	if err := proto.Unmarshal(p.commonPayload.GetData(), result); err != nil {
		return nil, fmt.Errorf("in %s: %w", funcName, err)
	}

	return parseEndorserTransaction(result), nil
}

func (p *payload) isEndorserTransaction() (bool, error) {
	channelHeader, err := p.channelHeader()
	if err != nil {
		return false, fmt.Errorf("in isEndorserTransaction: %w", err)
	}

	return channelHeader.GetType() == int32(common.HeaderType_ENDORSER_TRANSACTION), nil
}

func (p *payload) isValid() bool {
	return p.statusCode == int32(peer.TxValidationCode_VALID)
}
