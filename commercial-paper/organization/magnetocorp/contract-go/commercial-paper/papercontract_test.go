/*
 * SPDX-License-Identifier: Apache-2.0
 */

package commercialpaper

import (
	"errors"
	"testing"

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

// #########
// HELPERS
// #########
type MockPaperList struct {
	mock.Mock
}

func (mpl *MockPaperList) AddPaper(paper *CommercialPaper) error {
	args := mpl.Called(paper)

	return args.Error(0)
}

func (mpl *MockPaperList) GetPaper(issuer string, papernumber string) (*CommercialPaper, error) {
	args := mpl.Called(issuer, papernumber)

	return args.Get(0).(*CommercialPaper), args.Error(1)
}

func (mpl *MockPaperList) UpdatePaper(paper *CommercialPaper) error {
	args := mpl.Called(paper)

	return args.Error(0)
}

type MockTransactionContext struct {
	contractapi.TransactionContext
	paperList *MockPaperList
}

func (mtc *MockTransactionContext) GetPaperList() ListInterface {
	return mtc.paperList
}

func resetPaper(paper *CommercialPaper) {
	paper.Owner = "someowner"
	paper.SetTrading()
}

// #########
// TESTS
// #########

func TestIssue(t *testing.T) {
	var paper *CommercialPaper
	var err error

	mpl := new(MockPaperList)
	ctx := new(MockTransactionContext)
	ctx.paperList = mpl

	contract := new(Contract)

	var sentPaper *CommercialPaper

	mpl.On("AddPaper", mock.MatchedBy(func(paper *CommercialPaper) bool { sentPaper = paper; return paper.Issuer == "someissuer" })).Return(nil)
	mpl.On("AddPaper", mock.MatchedBy(func(paper *CommercialPaper) bool { sentPaper = paper; return paper.Issuer == "someotherissuer" })).Return(errors.New("AddPaper error"))

	expectedPaper := CommercialPaper{PaperNumber: "somepaper", Issuer: "someissuer", IssueDateTime: "someissuedate", FaceValue: 1000, MaturityDateTime: "somematuritydate", Owner: "someissuer", state: 1}
	paper, err = contract.Issue(ctx, "someissuer", "somepaper", "someissuedate", "somematuritydate", 1000)
	assert.Nil(t, err, "should not error when add paper does not error")
	assert.Equal(t, sentPaper, paper, "should send the same paper as it returns to add paper")
	assert.Equal(t, expectedPaper, *paper, "should correctly configure paper")

	paper, err = contract.Issue(ctx, "someotherissuer", "somepaper", "someissuedate", "somematuritydate", 1000)
	assert.EqualError(t, err, "AddPaper error", "should return error when add paper fails")
	assert.Nil(t, paper, "should not return paper when fails")
}

func TestBuy(t *testing.T) {
	var paper *CommercialPaper
	var err error

	mpl := new(MockPaperList)
	ctx := new(MockTransactionContext)
	ctx.paperList = mpl

	contract := new(Contract)

	wsPaper := new(CommercialPaper)
	resetPaper(wsPaper)

	var sentPaper *CommercialPaper
	var emptyPaper *CommercialPaper
	shouldError := false

	mpl.On("GetPaper", "someissuer", "somepaper").Return(wsPaper, nil)
	mpl.On("GetPaper", "someotherissuer", "someotherpaper").Return(emptyPaper, errors.New("GetPaper error"))
	mpl.On("UpdatePaper", mock.MatchedBy(func(paper *CommercialPaper) bool { return shouldError })).Return(errors.New("UpdatePaper error"))
	mpl.On("UpdatePaper", mock.MatchedBy(func(paper *CommercialPaper) bool { sentPaper = paper; return !shouldError })).Return(nil)

	paper, err = contract.Buy(ctx, "someotherissuer", "someotherpaper", "someowner", "someotherowner", 100, "2019-12-10:10:00")
	assert.EqualError(t, err, "GetPaper error", "should return error when GetPaper errors")
	assert.Nil(t, paper, "should return nil for paper when GetPaper errors")

	paper, err = contract.Buy(ctx, "someissuer", "somepaper", "someotherowner", "someowner", 100, "2019-12-10:10:00")
	assert.EqualError(t, err, "Paper someissuer:somepaper is not owned by someotherowner", "should error when sent owner not correct")
	assert.Nil(t, paper, "should not return paper for bad owner error")

	resetPaper(wsPaper)
	wsPaper.SetRedeemed()
	paper, err = contract.Buy(ctx, "someissuer", "somepaper", "someowner", "someotherowner", 100, "2019-12-10:10:00")
	assert.EqualError(t, err, "Paper someissuer:somepaper is not trading. Current state = REDEEMED")
	assert.Nil(t, paper, "should not return paper for bad state error")

	resetPaper(wsPaper)
	shouldError = true
	paper, err = contract.Buy(ctx, "someissuer", "somepaper", "someowner", "someotherowner", 100, "2019-12-10:10:00")
	assert.EqualError(t, err, "UpdatePaper error", "should error when update paper fails")
	assert.Nil(t, paper, "should not return paper for bad state error")
	shouldError = false

	resetPaper(wsPaper)
	wsPaper.SetIssued()
	paper, err = contract.Buy(ctx, "someissuer", "somepaper", "someowner", "someotherowner", 100, "2019-12-10:10:00")
	assert.Nil(t, err, "should not error when good paper and owner")
	assert.Equal(t, "someotherowner", paper.Owner, "should update the owner of the paper")
	assert.True(t, paper.IsTrading(), "should mark issued paper as trading")
	assert.Equal(t, sentPaper, paper, "should update same paper as it returns in the world state")
}

func TestRedeem(t *testing.T) {
	var paper *CommercialPaper
	var err error

	mpl := new(MockPaperList)
	ctx := new(MockTransactionContext)
	ctx.paperList = mpl

	contract := new(Contract)

	var sentPaper *CommercialPaper
	wsPaper := new(CommercialPaper)
	resetPaper(wsPaper)

	var emptyPaper *CommercialPaper
	shouldError := false

	mpl.On("GetPaper", "someissuer", "somepaper").Return(wsPaper, nil)
	mpl.On("GetPaper", "someotherissuer", "someotherpaper").Return(emptyPaper, errors.New("GetPaper error"))
	mpl.On("UpdatePaper", mock.MatchedBy(func(paper *CommercialPaper) bool { return shouldError })).Return(errors.New("UpdatePaper error"))
	mpl.On("UpdatePaper", mock.MatchedBy(func(paper *CommercialPaper) bool { sentPaper = paper; return !shouldError })).Return(nil)

	paper, err = contract.Redeem(ctx, "someotherissuer", "someotherpaper", "someowner", "2021-12-10:10:00")
	assert.EqualError(t, err, "GetPaper error", "should error when GetPaper errors")
	assert.Nil(t, paper, "should not return paper when GetPaper errors")

	paper, err = contract.Redeem(ctx, "someissuer", "somepaper", "someotherowner", "2021-12-10:10:00")
	assert.EqualError(t, err, "Paper someissuer:somepaper is not owned by someotherowner", "should error when paper owned by someone else")
	assert.Nil(t, paper, "should not return paper when errors as owned by someone else")

	resetPaper(wsPaper)
	wsPaper.SetRedeemed()
	paper, err = contract.Redeem(ctx, "someissuer", "somepaper", "someowner", "2021-12-10:10:00")
	assert.EqualError(t, err, "Paper someissuer:somepaper is already redeemed", "should error when paper already redeemed")
	assert.Nil(t, paper, "should not return paper when errors as already redeemed")

	shouldError = true
	resetPaper(wsPaper)
	paper, err = contract.Redeem(ctx, "someissuer", "somepaper", "someowner", "2021-12-10:10:00")
	assert.EqualError(t, err, "UpdatePaper error", "should error when update paper errors")
	assert.Nil(t, paper, "should not return paper when UpdatePaper errors")
	shouldError = false

	resetPaper(wsPaper)
	paper, err = contract.Redeem(ctx, "someissuer", "somepaper", "someowner", "2021-12-10:10:00")
	assert.Nil(t, err, "should not error on good redeem")
	assert.True(t, paper.IsRedeemed(), "should return redeemed paper")
	assert.Equal(t, sentPaper, paper, "should update same paper as it returns in the world state")
}
