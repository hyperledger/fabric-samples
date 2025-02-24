package parser

import (
	"sync"

	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset"
	"github.com/hyperledger/fabric-protos-go-apiv2/peer"
	"google.golang.org/protobuf/proto"
)

type endorserTransaction struct {
	transaction   *peer.Transaction
	readWriteSets func() ([]*readWriteSet, error)
}

func parseEndorserTransaction(transaction *peer.Transaction) *endorserTransaction {
	result := &endorserTransaction{transaction, nil}
	result.readWriteSets = sync.OnceValues(result.unmarshalReadWriteSets)
	return result
}

func (p *endorserTransaction) unmarshalReadWriteSets() ([]*readWriteSet, error) {
	chaincodeActionPayloads, err := p.unmarshalChaincodeActionPayloads()
	if err != nil {
		return nil, err
	}

	chaincodeEndorsedActions, err := p.extractChaincodeEndorsedActionsFrom(chaincodeActionPayloads)
	if err != nil {
		return nil, err
	}

	proposalResponsePayloads, err := p.unmarshalProposalResponsePayloadsFrom(chaincodeEndorsedActions)
	if err != nil {
		return nil, err
	}

	chaincodeActions, err := p.unmarshalChaincodeActionsFrom(proposalResponsePayloads)
	if err != nil {
		return nil, err
	}

	txReadWriteSets, err := p.unmarshalTxReadWriteSetsFrom(chaincodeActions)
	if err != nil {
		return nil, err
	}

	return p.parseReadWriteSets(txReadWriteSets), nil
}

func (p *endorserTransaction) unmarshalChaincodeActionPayloads() ([]*peer.ChaincodeActionPayload, error) {
	result := []*peer.ChaincodeActionPayload{}
	for _, transactionAction := range p.transaction.GetActions() {
		chaincodeActionPayload := &peer.ChaincodeActionPayload{}
		if err := proto.Unmarshal(transactionAction.GetPayload(), chaincodeActionPayload); err != nil {
			return nil, err
		}

		result = append(result, chaincodeActionPayload)
	}
	return result, nil
}

func (*endorserTransaction) extractChaincodeEndorsedActionsFrom(chaincodeActionPayloads []*peer.ChaincodeActionPayload) ([]*peer.ChaincodeEndorsedAction, error) {
	result := []*peer.ChaincodeEndorsedAction{}
	for _, payload := range chaincodeActionPayloads {
		result = append(result, payload.GetAction())
	}
	return result, nil
}

func (*endorserTransaction) unmarshalProposalResponsePayloadsFrom(chaincodeEndorsedActions []*peer.ChaincodeEndorsedAction) ([]*peer.ProposalResponsePayload, error) {
	result := []*peer.ProposalResponsePayload{}
	for _, endorsedAction := range chaincodeEndorsedActions {
		proposalResponsePayload := &peer.ProposalResponsePayload{}
		if err := proto.Unmarshal(endorsedAction.GetProposalResponsePayload(), proposalResponsePayload); err != nil {
			return nil, err
		}
		result = append(result, proposalResponsePayload)
	}
	return result, nil
}

func (*endorserTransaction) unmarshalChaincodeActionsFrom(proposalResponsePayloads []*peer.ProposalResponsePayload) ([]*peer.ChaincodeAction, error) {
	result := []*peer.ChaincodeAction{}
	for _, proposalResponsePayload := range proposalResponsePayloads {
		chaincodeAction := &peer.ChaincodeAction{}
		if err := proto.Unmarshal(proposalResponsePayload.GetExtension(), chaincodeAction); err != nil {
			return nil, err
		}
		result = append(result, chaincodeAction)
	}
	return result, nil
}

func (*endorserTransaction) unmarshalTxReadWriteSetsFrom(chaincodeActions []*peer.ChaincodeAction) ([]*rwset.TxReadWriteSet, error) {
	result := []*rwset.TxReadWriteSet{}
	for _, chaincodeAction := range chaincodeActions {
		txReadWriteSet := &rwset.TxReadWriteSet{}
		if err := proto.Unmarshal(chaincodeAction.GetResults(), txReadWriteSet); err != nil {
			return nil, err
		}
		result = append(result, txReadWriteSet)
	}
	return result, nil
}

func (*endorserTransaction) parseReadWriteSets(txReadWriteSets []*rwset.TxReadWriteSet) []*readWriteSet {
	result := []*readWriteSet{}
	for _, txReadWriteSet := range txReadWriteSets {
		parsedReadWriteSet := parseReadWriteSet(txReadWriteSet)
		result = append(result, parsedReadWriteSet)
	}
	return result
}
