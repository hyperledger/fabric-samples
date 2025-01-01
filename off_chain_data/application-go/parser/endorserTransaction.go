package parser

import (
	"offChainData/utils"

	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset"
	"github.com/hyperledger/fabric-protos-go-apiv2/peer"
	"google.golang.org/protobuf/proto"
)

type endorserTransaction struct {
	transaction *peer.Transaction
}

func parseEndorserTransaction(transaction *peer.Transaction) *endorserTransaction {
	return &endorserTransaction{transaction}
}

func (p *endorserTransaction) readWriteSets() []*readWriteSet {
	return utils.Cache(func() []*readWriteSet {
		chaincodeActionPayloads := p.unmarshalChaincodeActionPayloads()

		chaincodeEndorsedActions := p.extractChaincodeEndorsedActionsFrom(chaincodeActionPayloads)

		proposalResponsePayloads := p.unmarshalProposalResponsePayloadsFrom(chaincodeEndorsedActions)

		chaincodeActions := p.unmarshalChaincodeActionsFrom(proposalResponsePayloads)

		txReadWriteSets := p.unmarshalTxReadWriteSetsFrom(chaincodeActions)

		return p.parseReadWriteSets(txReadWriteSets)
	})()
}

func (p *endorserTransaction) unmarshalChaincodeActionPayloads() []*peer.ChaincodeActionPayload {
	result := []*peer.ChaincodeActionPayload{}
	for _, transactionAction := range p.transaction.GetActions() {
		chaincodeActionPayload := &peer.ChaincodeActionPayload{}
		if err := proto.Unmarshal(transactionAction.GetPayload(), chaincodeActionPayload); err != nil {
			panic(err)
		}

		result = append(result, chaincodeActionPayload)
	}
	return result
}

func (*endorserTransaction) extractChaincodeEndorsedActionsFrom(chaincodeActionPayloads []*peer.ChaincodeActionPayload) []*peer.ChaincodeEndorsedAction {
	result := []*peer.ChaincodeEndorsedAction{}
	for _, payload := range chaincodeActionPayloads {
		result = append(
			result,
			utils.AssertDefined(
				payload.GetAction(),
				"missing chaincode endorsed action",
			),
		)
	}
	return result
}

func (*endorserTransaction) unmarshalProposalResponsePayloadsFrom(chaincodeEndorsedActions []*peer.ChaincodeEndorsedAction) []*peer.ProposalResponsePayload {
	result := []*peer.ProposalResponsePayload{}
	for _, endorsedAction := range chaincodeEndorsedActions {
		proposalResponsePayload := &peer.ProposalResponsePayload{}
		if err := proto.Unmarshal(endorsedAction.GetProposalResponsePayload(), proposalResponsePayload); err != nil {
			panic(err)
		}
		result = append(result, proposalResponsePayload)
	}
	return result
}

func (*endorserTransaction) unmarshalChaincodeActionsFrom(proposalResponsePayloads []*peer.ProposalResponsePayload) []*peer.ChaincodeAction {
	result := []*peer.ChaincodeAction{}
	for _, proposalResponsePayload := range proposalResponsePayloads {
		chaincodeAction := &peer.ChaincodeAction{}
		if err := proto.Unmarshal(proposalResponsePayload.GetExtension(), chaincodeAction); err != nil {
			panic(err)
		}
		result = append(result, chaincodeAction)
	}
	return result
}

func (*endorserTransaction) unmarshalTxReadWriteSetsFrom(chaincodeActions []*peer.ChaincodeAction) []*rwset.TxReadWriteSet {
	result := []*rwset.TxReadWriteSet{}
	for _, chaincodeAction := range chaincodeActions {
		txReadWriteSet := &rwset.TxReadWriteSet{}
		if err := proto.Unmarshal(chaincodeAction.GetResults(), txReadWriteSet); err != nil {
			continue
		}
		result = append(result, txReadWriteSet)
	}
	return result
}

func (*endorserTransaction) parseReadWriteSets(txReadWriteSets []*rwset.TxReadWriteSet) []*readWriteSet {
	result := []*readWriteSet{}
	for _, txReadWriteSet := range txReadWriteSets {
		parsedReadWriteSet := parseReadWriteSet(txReadWriteSet)
		result = append(result, parsedReadWriteSet)
	}
	return result
}
