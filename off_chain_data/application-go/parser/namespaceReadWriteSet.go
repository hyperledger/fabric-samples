package parser

import (
	"fmt"

	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset"
	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset/kvrwset"
	"google.golang.org/protobuf/proto"
)

type NamespaceReadWriteSet struct {
	nsReadWriteSet     *rwset.NsReadWriteSet
	cachedReadWriteSet *kvrwset.KVRWSet
}

func parseNamespaceReadWriteSet(nsRwSet *rwset.NsReadWriteSet) *NamespaceReadWriteSet {
	return &NamespaceReadWriteSet{nsRwSet, nil}
}

func (p *NamespaceReadWriteSet) Namespace() string {
	return p.nsReadWriteSet.GetNamespace()
}

func (p *NamespaceReadWriteSet) ReadWriteSet() (*kvrwset.KVRWSet, error) {
	if p.cachedReadWriteSet != nil {
		return p.cachedReadWriteSet, nil
	}

	p.cachedReadWriteSet = &kvrwset.KVRWSet{}
	if err := proto.Unmarshal(p.nsReadWriteSet.GetRwset(), p.cachedReadWriteSet); err != nil {
		return nil, fmt.Errorf("in ReadWriteSet: %w", err)
	}

	return p.cachedReadWriteSet, nil
}
