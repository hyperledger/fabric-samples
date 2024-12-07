package parser

import (
	"offChainData/utils"

	"github.com/hyperledger/fabric-gateway/pkg/identity"
	"github.com/hyperledger/fabric-protos-go-apiv2/common"
	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset"
	"github.com/hyperledger/fabric-protos-go-apiv2/msp"
	"github.com/hyperledger/fabric-protos-go-apiv2/peer"
	"google.golang.org/protobuf/proto"
)

type Transaction struct {
	payload Payload
}

func NewTransaction(payload Payload) *Transaction {
	return &Transaction{payload}
}

func (t *Transaction) ChannelHeader() *common.ChannelHeader {
	return t.payload.ChannelHeader()
}

// TODO remove unused?
func (t *Transaction) Creator() identity.Identity {
	creator := &msp.SerializedIdentity{}
	if err := proto.Unmarshal(t.payload.SignatureHeader().GetCreator(), creator); err != nil {
		panic(err)
	}

	return &identityImpl{creator}
}

func (t *Transaction) NamespaceReadWriteSets() []NamespaceReadWriteSet {
	result := []NamespaceReadWriteSet{}
	for _, readWriteSet := range t.payload.EndorserTransaction().ReadWriteSets() {
		result = append(result, readWriteSet.NamespaceReadWriteSets()...)
	}

	return result
}

// TODO remove unused?
func (t *Transaction) ValidationCode() int32 {
	return t.payload.TransactionValidationCode()
}

// TODO remove unused?
func (t *Transaction) IsValid() bool {
	return t.payload.IsValid()
}

// TODO remove unused?
func (t *Transaction) ToProto() *common.Payload {
	return t.payload.ToProto()
}

// TODO remove interface, use struct; encapsulate; extract into file
type EndorserTransaction interface {
	ReadWriteSets() []ReadWriteSet
	ToProto() *peer.Transaction
}

type EndorserTransactionImpl struct {
	transaction *peer.Transaction
}

func ParseEndorserTransaction(transaction *peer.Transaction) *EndorserTransactionImpl {
	return &EndorserTransactionImpl{transaction}
}

// TODO add cache
func (p *EndorserTransactionImpl) ReadWriteSets() []ReadWriteSet {
	chaincodeActionPayloads := p.unmarshalChaincodeActionPayloads()

	chaincodeEndorsedActions := p.extractChaincodeEndorsedActionsFrom(chaincodeActionPayloads)

	proposalResponsePayloads := p.unmarshalProposalResponsePayloadsFrom(chaincodeEndorsedActions)

	chaincodeActions := p.unmarshalChaincodeActionsFrom(proposalResponsePayloads)

	txReadWriteSets := p.unmarshalTxReadWriteSetsFrom(chaincodeActions)

	parsedReadWriteSets := p.parseReadWriteSets(txReadWriteSets)

	return parsedReadWriteSets
}

func (p *EndorserTransactionImpl) unmarshalChaincodeActionPayloads() []*peer.ChaincodeActionPayload {
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

func (*EndorserTransactionImpl) extractChaincodeEndorsedActionsFrom(chaincodeActionPayloads []*peer.ChaincodeActionPayload) []*peer.ChaincodeEndorsedAction {
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

func (*EndorserTransactionImpl) unmarshalProposalResponsePayloadsFrom(chaincodeEndorsedActions []*peer.ChaincodeEndorsedAction) []*peer.ProposalResponsePayload {
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

func (*EndorserTransactionImpl) unmarshalChaincodeActionsFrom(proposalResponsePayloads []*peer.ProposalResponsePayload) []*peer.ChaincodeAction {
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

func (*EndorserTransactionImpl) unmarshalTxReadWriteSetsFrom(chaincodeActions []*peer.ChaincodeAction) []*rwset.TxReadWriteSet {
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

func (*EndorserTransactionImpl) parseReadWriteSets(txReadWriteSets []*rwset.TxReadWriteSet) []ReadWriteSet {
	result := []ReadWriteSet{}
	for _, txReadWriteSet := range txReadWriteSets {
		parsedReadWriteSet := ParseReadWriteSet(txReadWriteSet)
		result = append(result, parsedReadWriteSet)
	}
	return result
}

// TODO remove unused
func (p *EndorserTransactionImpl) ToProto() *peer.Transaction {
	return p.transaction
}
