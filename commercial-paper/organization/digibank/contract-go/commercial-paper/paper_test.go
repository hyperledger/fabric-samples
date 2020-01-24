/*
 * SPDX-License-Identifier: Apache-2.0
 */

package commercialpaper

import (
	"testing"

	ledgerapi "github.com/hyperledger/fabric-samples/commercial-paper/organization/digibank/contract-go/ledger-api"
	"github.com/stretchr/testify/assert"
)

func TestString(t *testing.T) {
	assert.Equal(t, "ISSUED", ISSUED.String(), "should return string for issued")
	assert.Equal(t, "TRADING", TRADING.String(), "should return string for issued")
	assert.Equal(t, "REDEEMED", REDEEMED.String(), "should return string for issued")
	assert.Equal(t, "UNKNOWN", State(REDEEMED+1).String(), "should return unknown when not one of constants")
}

func TestCreateCommercialPaperKey(t *testing.T) {
	assert.Equal(t, ledgerapi.MakeKey("someissuer", "somepaper"), CreateCommercialPaperKey("someissuer", "somepaper"), "should return key comprised of passed values")
}

func TestGetState(t *testing.T) {
	cp := new(CommercialPaper)
	cp.state = ISSUED

	assert.Equal(t, ISSUED, cp.GetState(), "should return set state")
}

func TestSetIssued(t *testing.T) {
	cp := new(CommercialPaper)
	cp.SetIssued()
	assert.Equal(t, ISSUED, cp.state, "should set state to trading")
}

func TestSetTrading(t *testing.T) {
	cp := new(CommercialPaper)
	cp.SetTrading()
	assert.Equal(t, TRADING, cp.state, "should set state to trading")
}

func TestSetRedeemed(t *testing.T) {
	cp := new(CommercialPaper)
	cp.SetRedeemed()
	assert.Equal(t, REDEEMED, cp.state, "should set state to trading")
}

func TestIsIssued(t *testing.T) {
	cp := new(CommercialPaper)

	cp.SetIssued()
	assert.True(t, cp.IsIssued(), "should be true when status set to issued")

	cp.SetTrading()
	assert.False(t, cp.IsIssued(), "should be false when status not set to issued")
}

func TestIsTrading(t *testing.T) {
	cp := new(CommercialPaper)

	cp.SetTrading()
	assert.True(t, cp.IsTrading(), "should be true when status set to trading")

	cp.SetRedeemed()
	assert.False(t, cp.IsTrading(), "should be false when status not set to trading")
}

func TestIsRedeemed(t *testing.T) {
	cp := new(CommercialPaper)

	cp.SetRedeemed()
	assert.True(t, cp.IsRedeemed(), "should be true when status set to redeemed")

	cp.SetIssued()
	assert.False(t, cp.IsRedeemed(), "should be false when status not set to redeemed")
}

func TestGetSplitKey(t *testing.T) {
	cp := new(CommercialPaper)
	cp.PaperNumber = "somepaper"
	cp.Issuer = "someissuer"

	assert.Equal(t, []string{"someissuer", "somepaper"}, cp.GetSplitKey(), "should return issuer and paper number as split key")
}

func TestSerialize(t *testing.T) {
	cp := new(CommercialPaper)
	cp.PaperNumber = "somepaper"
	cp.Issuer = "someissuer"
	cp.IssueDateTime = "sometime"
	cp.FaceValue = 1000
	cp.MaturityDateTime = "somelatertime"
	cp.Owner = "someowner"
	cp.state = TRADING

	bytes, err := cp.Serialize()
	assert.Nil(t, err, "should not error on serialize")
	assert.Equal(t, `{"paperNumber":"somepaper","issuer":"someissuer","issueDateTime":"sometime","faceValue":1000,"maturityDateTime":"somelatertime","owner":"someowner","currentState":2,"class":"org.papernet.commercialpaper","key":"someissuer:somepaper"}`, string(bytes), "should return JSON formatted value")
}

func TestDeserialize(t *testing.T) {
	var cp *CommercialPaper
	var err error

	goodJSON := `{"paperNumber":"somepaper","issuer":"someissuer","issueDateTime":"sometime","faceValue":1000,"maturityDateTime":"somelatertime","owner":"someowner","currentState":2,"class":"org.papernet.commercialpaper","key":"someissuer:somepaper"}`
	expectedCp := new(CommercialPaper)
	expectedCp.PaperNumber = "somepaper"
	expectedCp.Issuer = "someissuer"
	expectedCp.IssueDateTime = "sometime"
	expectedCp.FaceValue = 1000
	expectedCp.MaturityDateTime = "somelatertime"
	expectedCp.Owner = "someowner"
	expectedCp.state = TRADING
	cp = new(CommercialPaper)
	err = Deserialize([]byte(goodJSON), cp)
	assert.Nil(t, err, "should not return error for deserialize")
	assert.Equal(t, expectedCp, cp, "should create expected commercial paper")

	badJSON := `{"paperNumber":"somepaper","issuer":"someissuer","issueDateTime":"sometime","faceValue":"NaN","maturityDateTime":"somelatertime","owner":"someowner","currentState":2,"class":"org.papernet.commercialpaper","key":"someissuer:somepaper"}`
	cp = new(CommercialPaper)
	err = Deserialize([]byte(badJSON), cp)
	assert.EqualError(t, err, "Error deserializing commercial paper. json: cannot unmarshal string into Go struct field jsonCommercialPaper.faceValue of type int", "should return error for bad data")
}
