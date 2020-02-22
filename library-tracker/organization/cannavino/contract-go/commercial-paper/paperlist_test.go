/*
 * SPDX-License-Identifier: Apache-2.0
 */

package commercialpaper

import (
	"errors"
	"testing"

	ledgerapi "github.com/hyperledger/fabric-samples/commercial-paper/organization/magnetocorp/contract-go/ledger-api"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
)

// #########
// HELPERS
// #########

type MockStateList struct {
	mock.Mock
}

func (msl *MockStateList) AddState(state ledgerapi.StateInterface) error {
	args := msl.Called(state)

	return args.Error(0)
}

func (msl *MockStateList) GetState(key string, state ledgerapi.StateInterface) error {
	args := msl.Called(key, state)

	state.(*CommercialPaper).PaperNumber = "somepaper"

	return args.Error(0)
}

func (msl *MockStateList) UpdateState(state ledgerapi.StateInterface) error {
	args := msl.Called(state)

	return args.Error(0)
}

// #########
// TESTS
// #########

func TestAddPaper(t *testing.T) {
	paper := new(CommercialPaper)

	list := new(list)
	msl := new(MockStateList)
	msl.On("AddState", paper).Return(errors.New("Called add state correctly"))
	list.stateList = msl

	err := list.AddPaper(paper)
	assert.EqualError(t, err, "Called add state correctly", "should call state list add state with paper")
}

func TestGetPaper(t *testing.T) {
	var cp *CommercialPaper
	var err error

	list := new(list)
	msl := new(MockStateList)
	msl.On("GetState", CreateCommercialPaperKey("someissuer", "somepaper"), mock.MatchedBy(func(state ledgerapi.StateInterface) bool { _, ok := state.(*CommercialPaper); return ok })).Return(nil)
	msl.On("GetState", CreateCommercialPaperKey("someotherissuer", "someotherpaper"), mock.MatchedBy(func(state ledgerapi.StateInterface) bool { _, ok := state.(*CommercialPaper); return ok })).Return(errors.New("GetState error"))
	list.stateList = msl

	cp, err = list.GetPaper("someissuer", "somepaper")
	assert.Nil(t, err, "should not error when get state on state list does not error")
	assert.Equal(t, cp.PaperNumber, "somepaper", "should use state list GetState to fill commercial paper")

	cp, err = list.GetPaper("someotherissuer", "someotherpaper")
	assert.EqualError(t, err, "GetState error", "should return error when state list get state errors")
	assert.Nil(t, cp, "should not return commercial paper on error")
}

func TestUpdatePaper(t *testing.T) {
	paper := new(CommercialPaper)

	list := new(list)
	msl := new(MockStateList)
	msl.On("UpdateState", paper).Return(errors.New("Called update state correctly"))
	list.stateList = msl

	err := list.UpdatePaper(paper)
	assert.EqualError(t, err, "Called update state correctly", "should call state list update state with paper")
}

func TestNewStateList(t *testing.T) {
	ctx := new(TransactionContext)
	list := newList(ctx)
	stateList, ok := list.stateList.(*ledgerapi.StateList)

	assert.True(t, ok, "should make statelist of type ledgerapi.StateList")
	assert.Equal(t, ctx, stateList.Ctx, "should set the context to passed context")
	assert.Equal(t, "org.papernet.commercialpaperlist", stateList.Name, "should set the name for the list")

	expectedErr := Deserialize([]byte("bad json"), new(CommercialPaper))
	err := stateList.Deserialize([]byte("bad json"), new(CommercialPaper))
	assert.EqualError(t, err, expectedErr.Error(), "should call Deserialize when stateList.Deserialize called")
}
