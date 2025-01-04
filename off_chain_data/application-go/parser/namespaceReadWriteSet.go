package parser

import (
	"fmt"
	"offChainData/utils"

	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset"
	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset/kvrwset"
	"google.golang.org/protobuf/proto"
)

type NamespaceReadWriteSet struct {
	nsReadWriteSet *rwset.NsReadWriteSet
}

func parseNamespaceReadWriteSet(nsRwSet *rwset.NsReadWriteSet) *NamespaceReadWriteSet {
	return &NamespaceReadWriteSet{nsRwSet}
}

func (p *NamespaceReadWriteSet) Namespace() string {
	return p.nsReadWriteSet.GetNamespace()
}

func (p *NamespaceReadWriteSet) ReadWriteSet() (*kvrwset.KVRWSet, error) {
	return utils.Cache(func() (*kvrwset.KVRWSet, error) {
		result := kvrwset.KVRWSet{}
		if err := proto.Unmarshal(p.nsReadWriteSet.GetRwset(), &result); err != nil {
			return nil, fmt.Errorf("in ReadWriteSet: %w", err)
		}

		return &result, nil
	})()
}
