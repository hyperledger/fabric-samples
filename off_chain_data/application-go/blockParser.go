package main

import (
	"fmt"

	"github.com/hyperledger/fabric-gateway/pkg/identity"
	"github.com/hyperledger/fabric-protos-go-apiv2/common"
	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset"
	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset/kvrwset"
	"github.com/hyperledger/fabric-protos-go-apiv2/msp"
	"github.com/hyperledger/fabric-protos-go-apiv2/peer"
	"google.golang.org/protobuf/proto"
)

type Block interface {
	GetNumber() uint64
	GetTransactions() []Transaction
	ToProto() *common.Block
}

type ParsedBlock struct {
	block           *common.Block
	validationCodes []byte
	transactions    []Transaction
}

func NewParsedBlock(block *common.Block) Block {
	validationCodes := getTransactionValidationCodes(block)

	return &ParsedBlock{block, validationCodes, nil}
}

func (pb *ParsedBlock) GetNumber() uint64 {
	header := assertDefined(pb.block.GetHeader(), "missing block header")
	return header.GetNumber()
}

// TODO: needs cache, getPayloads, parsePayload
func (pb *ParsedBlock) GetTransactions() []Transaction {
	return nil
}

func (pb *ParsedBlock) ToProto() *common.Block {
	return nil
}

// Implements identity.Identity Interface
type IdentityImpl struct {
	creator *msp.SerializedIdentity
}

func (i *IdentityImpl) MspID() string {
	return i.creator.GetMspid()
}

func (i *IdentityImpl) Credentials() []byte {
	return i.creator.GetIdBytes()
}

type Transaction interface {
	GetChannelHeader() *common.ChannelHeader
	GetCreator() identity.Identity
	GetValidationCode() int32
	IsValid() bool
	GetNamespaceReadWriteSets() []NamespaceReadWriteSet
	ToProto() *common.Payload
}

type TransactionImpl struct {
	payload Payload
}

func NewTransaction(payload Payload) Transaction {
	return &TransactionImpl{payload}
}

func (t *TransactionImpl) GetChannelHeader() *common.ChannelHeader {
	return t.payload.GetChannelHeader()
}

func (t *TransactionImpl) GetCreator() identity.Identity {
	creator := &msp.SerializedIdentity{}
	if err := proto.Unmarshal(t.payload.GetSignatureHeader().GetCreator(), creator); err != nil {
		panic(err)
	}

	return &IdentityImpl{creator}
}

func (t *TransactionImpl) GetNamespaceReadWriteSets() []NamespaceReadWriteSet {
	result := []NamespaceReadWriteSet{}
	for _, readWriteSet := range t.payload.GetEndorserTransaction().GetReadWriteSets() {
		result = append(result, readWriteSet.GetNamespaceReadWriteSets()...)
	}

	return result
}

func (t *TransactionImpl) GetValidationCode() int32 {
	return t.payload.GetTransactionValidationCode()
}

func (t *TransactionImpl) IsValid() bool {
	return t.payload.IsValid()
}

func (t *TransactionImpl) ToProto() *common.Payload {
	return t.payload.ToProto()
}

type EndorserTransaction interface {
	GetReadWriteSets() []ReadWriteSet
	ToProto() *peer.Transaction
}

type ParsedEndorserTransaction struct {
	transaction *peer.Transaction
}

func NewParsedEndorserTransaction(transaction *peer.Transaction) EndorserTransaction {
	return &ParsedEndorserTransaction{transaction}
}

// TODO add cache
func (p *ParsedEndorserTransaction) GetReadWriteSets() []ReadWriteSet {
	chaincodeActionPayloads := p.getChaincodeActionPayloads()

	chaincodeEndorsedActions := p.getChaincodeEndorsedActions(chaincodeActionPayloads)

	proposalResponsePayloads := p.getProposalResponsePayloads(chaincodeEndorsedActions)

	chaincodeActions := p.getChaincodeActions(proposalResponsePayloads)

	txReadWriteSets := p.getTxReadWriteSets(chaincodeActions)

	parsedReadWriteSets := p.parseReadWriteSets(txReadWriteSets)

	return parsedReadWriteSets
}

func (p *ParsedEndorserTransaction) getChaincodeActionPayloads() []*peer.ChaincodeActionPayload {
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

func (*ParsedEndorserTransaction) getChaincodeEndorsedActions(chaincodeActionPayloads []*peer.ChaincodeActionPayload) []*peer.ChaincodeEndorsedAction {
	result := []*peer.ChaincodeEndorsedAction{}
	for _, payload := range chaincodeActionPayloads {
		result = append(
			result,
			assertDefined(
				payload.GetAction(),
				"missing chaincode endorsed action",
			),
		)
	}
	return result
}

func (*ParsedEndorserTransaction) getProposalResponsePayloads(chaincodeEndorsedActions []*peer.ChaincodeEndorsedAction) []*peer.ProposalResponsePayload {
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

func (*ParsedEndorserTransaction) getChaincodeActions(proposalResponsePayloads []*peer.ProposalResponsePayload) []*peer.ChaincodeAction {
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

func (*ParsedEndorserTransaction) getTxReadWriteSets(chaincodeActions []*peer.ChaincodeAction) []*rwset.TxReadWriteSet {
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

func (*ParsedEndorserTransaction) parseReadWriteSets(txReadWriteSets []*rwset.TxReadWriteSet) []ReadWriteSet {
	result := []ReadWriteSet{}
	for _, txReadWriteSet := range txReadWriteSets {
		parsedReadWriteSet := NewParsedReadWriteSet(txReadWriteSet)
		result = append(result, parsedReadWriteSet)
	}
	return result
}

func (p *ParsedEndorserTransaction) ToProto() *peer.Transaction {
	return p.transaction
}

type ReadWriteSet interface {
	GetNamespaceReadWriteSets() []NamespaceReadWriteSet
	ToProto() *rwset.TxReadWriteSet
}

type ParsedReadWriteSet struct {
	readWriteSet *rwset.TxReadWriteSet
}

func NewParsedReadWriteSet(rwSet *rwset.TxReadWriteSet) ReadWriteSet {
	return &ParsedReadWriteSet{rwSet}
}

func (p *ParsedReadWriteSet) GetNamespaceReadWriteSets() []NamespaceReadWriteSet {
	result := []NamespaceReadWriteSet{}
	for _, nsReadWriteSet := range p.readWriteSet.GetNsRwset() {
		parsedNamespaceReadWriteSet := NewParsedNamespaceReadWriteSet(nsReadWriteSet)
		result = append(result, parsedNamespaceReadWriteSet)
	}
	return result
}

func (p *ParsedReadWriteSet) ToProto() *rwset.TxReadWriteSet {
	return p.readWriteSet
}

type NamespaceReadWriteSet interface {
	GetNamespace() string
	GetReadWriteSet() *kvrwset.KVRWSet
	ToProto() *rwset.NsReadWriteSet
}

type ParsedNamespaceReadWriteSet struct {
	nsReadWriteSet *rwset.NsReadWriteSet
}

func NewParsedNamespaceReadWriteSet(nsRwSet *rwset.NsReadWriteSet) NamespaceReadWriteSet {
	return &ParsedNamespaceReadWriteSet{nsRwSet}
}

func (p *ParsedNamespaceReadWriteSet) GetNamespace() string {
	return p.nsReadWriteSet.GetNamespace()
}

// TODO add cache
func (p *ParsedNamespaceReadWriteSet) GetReadWriteSet() *kvrwset.KVRWSet {
	result := kvrwset.KVRWSet{}
	if err := proto.Unmarshal(p.nsReadWriteSet.GetRwset(), &result); err != nil {
		panic(err)
	}

	return &result
}

func (p *ParsedNamespaceReadWriteSet) ToProto() *rwset.NsReadWriteSet {
	return p.nsReadWriteSet
}

func (pb *ParsedBlock) payloads() []*common.Payload {
	var payloads []*common.Payload

	for _, envelopeBytes := range pb.block.GetData().GetData() {
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
func (pb *ParsedBlock) statusCode(txIndex int) peer.TxValidationCode {
	blockMetadata := assertDefined(
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

type Payload interface {
	GetChannelHeader() *common.ChannelHeader
	GetEndorserTransaction() EndorserTransaction
	GetSignatureHeader() *common.SignatureHeader
	GetTransactionValidationCode() int32
	IsEndorserTransaction() bool
	IsValid() bool
	ToProto() *common.Payload
}

type PayloadImpl struct {
	payload    *common.Payload
	statusCode int32
}

func NewPayloadImpl(payload *common.Payload, statusCode int32) Payload {
	return &PayloadImpl{payload, statusCode}
}

func (p *PayloadImpl) GetChannelHeader() *common.ChannelHeader {
	header := assertDefined(p.payload.GetHeader(), "missing payload header")

	// TODO add cache, return cachedChannelHeader like in blockParser.ts:77
	result := &common.ChannelHeader{}
	if err := proto.Unmarshal(header.GetChannelHeader(), result); err != nil {
		panic(err)
	}

	return result
}

func (p *PayloadImpl) GetEndorserTransaction() EndorserTransaction {
	if !p.IsEndorserTransaction() {
		panic(fmt.Errorf("unexpected payload type: %d", p.GetChannelHeader().GetType()))
	}

	result := &peer.Transaction{}
	if err := proto.Unmarshal(p.payload.GetData(), result); err != nil {
		panic(err)
	}

	return NewParsedEndorserTransaction(result)
}

func (p *PayloadImpl) GetSignatureHeader() *common.SignatureHeader {
	header := assertDefined(p.payload.GetHeader(), "missing payload header")

	// TODO add cache, return cachedSignatureHeader like in blockParser.ts:77
	result := &common.SignatureHeader{}
	if err := proto.Unmarshal(header.GetSignatureHeader(), result); err != nil {
		panic(err)
	}

	return result
}

func (p *PayloadImpl) GetTransactionValidationCode() int32 {
	return p.statusCode
}

func (p *PayloadImpl) IsEndorserTransaction() bool {
	return p.GetChannelHeader().GetType() == int32(common.HeaderType_ENDORSER_TRANSACTION)
}

func (p *PayloadImpl) IsValid() bool {
	return p.statusCode == int32(peer.TxValidationCode_VALID)
}

func (p *PayloadImpl) ToProto() *common.Payload {
	return p.payload
}

func getTransactionValidationCodes(block *common.Block) []byte {
	metadata := assertDefined(
		block.GetMetadata(),
		"missing block metadata",
	)

	return assertDefined(
		metadata.GetMetadata()[common.BlockMetadataIndex_TRANSACTIONS_FILTER],
		"missing transaction validation code",
	)
}
