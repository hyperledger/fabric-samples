/*
 * SPDX-License-Identifier: Apache-2.0
 */

package commercialpaper

import ledgerapi "github.com/hyperledger/fabric-samples/commercial-paper/organization/magnetocorp/contract-go/ledger-api"

// ListInterface defines functionality needed
// to interact with the world state on behalf
// of a commercial paper
type ListInterface interface {
	AddPaper(*CommercialPaper) error
	GetPaper(string, string) (*CommercialPaper, error)
	UpdatePaper(*CommercialPaper) error
}

type list struct {
	stateList ledgerapi.StateListInterface
}

func (cpl *list) AddPaper(paper *CommercialPaper) error {
	return cpl.stateList.AddState(paper)
}

func (cpl *list) GetPaper(issuer string, paperNumber string) (*CommercialPaper, error) {
	cp := new(CommercialPaper)

	err := cpl.stateList.GetState(CreateCommercialPaperKey(issuer, paperNumber), cp)

	if err != nil {
		return nil, err
	}

	return cp, nil
}

func (cpl *list) UpdatePaper(paper *CommercialPaper) error {
	return cpl.stateList.UpdateState(paper)
}

// NewList create a new list from context
func newList(ctx TransactionContextInterface) *list {
	stateList := new(ledgerapi.StateList)
	stateList.Ctx = ctx
	stateList.Name = "org.papernet.commercialpaperlist"
	stateList.Deserialize = func(bytes []byte, state ledgerapi.StateInterface) error {
		return Deserialize(bytes, state.(*CommercialPaper))
	}

	list := new(list)
	list.stateList = stateList

	return list
}
