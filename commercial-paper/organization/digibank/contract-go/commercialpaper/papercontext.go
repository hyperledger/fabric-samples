/*
 * SPDX-License-Identifier: Apache-2.0
 */

package commercialpaper

import (
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

// TransactionContextInterface an interface to
// describe the minimum required functions for
// a transaction context in the commercial
// paper
type TransactionContextInterface interface {
	contractapi.TransactionContextInterface
	GetPaperList() ListInterface
}

// TransactionContext implementation of
// TransactionContextInterface for use with
// commercial paper contract
type TransactionContext struct {
	contractapi.TransactionContext
	paperList *list
}

// GetPaperList return paper list
func (tc *TransactionContext) GetPaperList() ListInterface {
	if tc.paperList == nil {
		tc.paperList = newList(tc)
	}

	return tc.paperList
}
