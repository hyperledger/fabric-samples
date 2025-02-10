package parser

import (
	"sync"

	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset"
	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset/kvrwset"
	"google.golang.org/protobuf/proto"
)

type NamespaceReadWriteSet struct {
	nsReadWriteSet *rwset.NsReadWriteSet
	readWriteSet   func() (*kvrwset.KVRWSet, error)
}

func parseNamespaceReadWriteSet(nsRwSet *rwset.NsReadWriteSet) *NamespaceReadWriteSet {
	result := &NamespaceReadWriteSet{nsRwSet, nil}
	result.readWriteSet = sync.OnceValues(result.unmarshalReadWriteSet)
	return result
}

func (p *NamespaceReadWriteSet) Namespace() string {
	return p.nsReadWriteSet.GetNamespace()
}

func (p *NamespaceReadWriteSet) ReadWriteSet() (*kvrwset.KVRWSet, error) {
	return p.readWriteSet()
}

func (p *NamespaceReadWriteSet) unmarshalReadWriteSet() (*kvrwset.KVRWSet, error) {
	result := &kvrwset.KVRWSet{}
	if err := proto.Unmarshal(p.nsReadWriteSet.GetRwset(), result); err != nil {
		return nil, err
	}

	return result, nil
}
