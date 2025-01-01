package parser

import (
	"fmt"
	"offChainData/utils"

	"github.com/hyperledger/fabric-protos-go-apiv2/common"
	"github.com/hyperledger/fabric-protos-go-apiv2/peer"
	"google.golang.org/protobuf/proto"
)

type payload struct {
	commonPayload *common.Payload
	statusCode    int32
}

func parsePayload(commonPayload *common.Payload, statusCode int32) *payload {
	return &payload{commonPayload, statusCode}
}

func (p *payload) channelHeader() *common.ChannelHeader {
	return utils.Cache(func() *common.ChannelHeader {
		header := utils.AssertDefined(p.commonPayload.GetHeader(), "missing payload header")

		result := &common.ChannelHeader{}
		if err := proto.Unmarshal(header.GetChannelHeader(), result); err != nil {
			panic(err)
		}

		return result
	})()
}

func (p *payload) endorserTransaction() *endorserTransaction {
	if !p.isEndorserTransaction() {
		panic(fmt.Errorf("unexpected payload type: %d", p.channelHeader().GetType()))
	}

	result := &peer.Transaction{}
	if err := proto.Unmarshal(p.commonPayload.GetData(), result); err != nil {
		panic(err)
	}

	return parseEndorserTransaction(result)
}

func (p *payload) isEndorserTransaction() bool {
	return p.channelHeader().GetType() == int32(common.HeaderType_ENDORSER_TRANSACTION)
}

func (p *payload) isValid() bool {
	return p.statusCode == int32(peer.TxValidationCode_VALID)
}
