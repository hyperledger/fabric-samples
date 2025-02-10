package parser

import (
	"fmt"
	"sync"

	"github.com/hyperledger/fabric-protos-go-apiv2/common"
	"github.com/hyperledger/fabric-protos-go-apiv2/peer"
	"google.golang.org/protobuf/proto"
)

type payload struct {
	commonPayload *common.Payload
	statusCode    int32
	channelHeader func() (*common.ChannelHeader, error)
}

func parsePayload(commonPayload *common.Payload, statusCode int32) *payload {
	result := &payload{commonPayload, statusCode, nil}
	result.channelHeader = sync.OnceValues(result.unmarshalChannelHeader)
	return result
}

func (p *payload) unmarshalChannelHeader() (*common.ChannelHeader, error) {
	result := &common.ChannelHeader{}
	if err := proto.Unmarshal(p.commonPayload.GetHeader().GetChannelHeader(), result); err != nil {
		return nil, err
	}

	return result, nil
}

func (p *payload) endorserTransaction() (*endorserTransaction, error) {
	is, err := p.isEndorserTransaction()
	if err != nil {
		return nil, err
	}
	if !is {
		channelHeader, err := p.channelHeader()
		if err != nil {
			return nil, err
		}
		return nil, fmt.Errorf("unexpected payload type: %d", channelHeader.GetType())
	}

	result := &peer.Transaction{}
	if err := proto.Unmarshal(p.commonPayload.GetData(), result); err != nil {
		return nil, err
	}

	return parseEndorserTransaction(result), nil
}

func (p *payload) isEndorserTransaction() (bool, error) {
	channelHeader, err := p.channelHeader()
	if err != nil {
		return false, err
	}

	return channelHeader.GetType() == int32(common.HeaderType_ENDORSER_TRANSACTION), nil
}

func (p *payload) isValid() bool {
	return p.statusCode == int32(peer.TxValidationCode_VALID)
}
