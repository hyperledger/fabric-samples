package parser

import (
	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset"
)

type readWriteSet struct {
	readWriteSet *rwset.TxReadWriteSet
}

func parseReadWriteSet(rwSet *rwset.TxReadWriteSet) *readWriteSet {
	return &readWriteSet{rwSet}
}

func (p *readWriteSet) namespaceReadWriteSets() []*NamespaceReadWriteSet {
	result := []*NamespaceReadWriteSet{}
	for _, nsReadWriteSet := range p.readWriteSet.GetNsRwset() {
		parsedNamespaceReadWriteSet := parseNamespaceReadWriteSet(nsReadWriteSet)
		result = append(result, parsedNamespaceReadWriteSet)
	}
	return result
}
