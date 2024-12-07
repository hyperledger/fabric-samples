package parser

import (
	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset"
	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset/kvrwset"
	"google.golang.org/protobuf/proto"
)

// TODO remove interface, use struct; encapsulate; extract into file
type ReadWriteSet interface {
	NamespaceReadWriteSets() []NamespaceReadWriteSet
	ToProto() *rwset.TxReadWriteSet
}

type ReadWriteSetImpl struct {
	readWriteSet *rwset.TxReadWriteSet
}

func ParseReadWriteSet(rwSet *rwset.TxReadWriteSet) *ReadWriteSetImpl {
	return &ReadWriteSetImpl{rwSet}
}

func (p *ReadWriteSetImpl) NamespaceReadWriteSets() []NamespaceReadWriteSet {
	result := []NamespaceReadWriteSet{}
	for _, nsReadWriteSet := range p.readWriteSet.GetNsRwset() {
		parsedNamespaceReadWriteSet := ParseNamespaceReadWriteSet(nsReadWriteSet)
		result = append(result, parsedNamespaceReadWriteSet)
	}
	return result
}

// TODO remove unused
func (p *ReadWriteSetImpl) ToProto() *rwset.TxReadWriteSet {
	return p.readWriteSet
}

// TODO remove interface, use struct
type NamespaceReadWriteSet interface {
	Namespace() string
	ReadWriteSet() *kvrwset.KVRWSet
	ToProto() *rwset.NsReadWriteSet
}

type NamespaceReadWriteSetImpl struct {
	nsReadWriteSet *rwset.NsReadWriteSet
}

func ParseNamespaceReadWriteSet(nsRwSet *rwset.NsReadWriteSet) *NamespaceReadWriteSetImpl {
	return &NamespaceReadWriteSetImpl{nsRwSet}
}

func (p *NamespaceReadWriteSetImpl) Namespace() string {
	return p.nsReadWriteSet.GetNamespace()
}

// TODO add cache
func (p *NamespaceReadWriteSetImpl) ReadWriteSet() *kvrwset.KVRWSet {
	result := kvrwset.KVRWSet{}
	if err := proto.Unmarshal(p.nsReadWriteSet.GetRwset(), &result); err != nil {
		panic(err)
	}

	return &result
}

// TODO remove unused
func (p *NamespaceReadWriteSetImpl) ToProto() *rwset.NsReadWriteSet {
	return p.nsReadWriteSet
}
