/*
 * SPDX-License-Identifier: Apache-2.0
 */

package commercialpaper

import (
	"testing"

	ledgerapi "github.com/hyperledger/fabric-samples/commercial-paper/organization/digibank/contract-go/ledger-api"
	"github.com/stretchr/testify/assert"
)

func TestGetPaperList(t *testing.T) {
	var tc *TransactionContext
	var expectedPaperList *list

	tc = new(TransactionContext)
	expectedPaperList = newList(tc)
	actualList := tc.GetPaperList().(*list)
	assert.Equal(t, expectedPaperList.stateList.(*ledgerapi.StateList).Name, actualList.stateList.(*ledgerapi.StateList).Name, "should configure paper list when one not already configured")

	tc = new(TransactionContext)
	expectedPaperList = new(list)
	expectedStateList := new(ledgerapi.StateList)
	expectedStateList.Ctx = tc
	expectedStateList.Name = "existing paper list"
	expectedPaperList.stateList = expectedStateList
	tc.paperList = expectedPaperList
	assert.Equal(t, expectedPaperList, tc.GetPaperList(), "should return set paper list when already set")
}
