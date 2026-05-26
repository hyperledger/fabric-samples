package e2e

import (
	"context"
	"os"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
)

var auditor *ClientWithResponses
var issuer *ClientWithResponses

var err error
var CODE string = "TEST"
var alice = Counterparty{
	Account: "alice",
	Node:    "owner1",
}
var bob = Counterparty{
	Account: "bob",
	Node:    "owner1",
}
var dan = Counterparty{
	Account: "dan",
	Node:    "owner2",
}

type ownerAPI struct {
	client *ClientWithResponses
}

var owner1 ownerAPI
var owner2 ownerAPI

func TestMain(t *testing.T) {
	auditor, err = NewClientWithResponses(getEnv("AUDITOR_URL", "http://localhost:9000/api/v1"))
	assert.NoError(t, err, "failed creating client")
	issuer, err = NewClientWithResponses(getEnv("ISSUER_URL", "http://localhost:9100/api/v1"))
	assert.NoError(t, err, "failed creating client")

	client1, err := NewClientWithResponses(getEnv("OWNER1_URL", "http://localhost:9200/api/v1"))
	assert.NoError(t, err, "failed creating client")
	owner1 = ownerAPI{client: client1}

	client2, err := NewClientWithResponses(getEnv("OWNER2_URL", "http://localhost:9300/api/v1"))
	assert.NoError(t, err, "failed creating client")
	owner2 = ownerAPI{client: client2}

	// we have to issue funds to alice first to be able to do the other tests
	testIssuance(t)
}

func testIssuance(t *testing.T) {
	accBefore := owner1.getAccounts(t)
	txBefore := owner1.getTransactions(t, "alice")

	id := issue(t, alice, 1000)
	acc2 := owner1.getAccounts(t)

	txAfter := owner1.getTransactions(t, "alice")
	assert.Equal(t, len(txBefore)+1, len(txAfter), "should have 1 issue transaction more", txAfter)
	assert.Equal(t, getValue(t, accBefore, "alice")+1000, getValue(t, acc2, "alice"), acc2)

	lastTx := txAfter[len(txAfter)-1]
	assert.Equal(t, id, lastTx.Id)
	assert.Equal(t, int64(1000), lastTx.Amount.Value)
	assert.Equal(t, "alice", lastTx.Recipient)
}

func TestTransfer(t *testing.T) {
	accBefore := owner1.getAccounts(t)
	txBefore := owner1.getTransactions(t, "alice")
	id := owner1.transfer(t, "alice", bob, 100)
	accAfter := owner1.getAccounts(t)
	txAfter := owner1.getTransactions(t, "alice")

	assert.Equal(t, getValue(t, accBefore, "alice")-100, getValue(t, accAfter, "alice"), accAfter)
	assert.Equal(t, getValue(t, accBefore, "bob")+100, getValue(t, accAfter, "bob"), accAfter)
	assert.Greater(t, len(txAfter), len(txBefore))

	// on the sender side there may be several transactions, so we check the recipient
	txBob := owner1.getTransactions(t, "bob")
	lastTx := txBob[len(txBob)-1]
	assert.Equal(t, id, lastTx.Id, txBob)
	assert.Equal(t, lastTx.Amount.Value, int64(100))
}

func TestTransferToSecondNode(t *testing.T) {
	// current state
	acc1Before := owner1.getAccounts(t)
	tx1Before := owner1.getTransactions(t, "alice")
	acc2Before := owner2.getAccounts(t)
	tx2Before := owner2.getTransactions(t, "dan")

	// transfer 100 from alice to dan
	id := owner1.transfer(t, "alice", dan, 100)

	// after: alice
	acc1After := owner1.getAccounts(t)
	assert.Equal(t, getValue(t, acc1Before, "alice")-100, getValue(t, acc1After, "alice"), acc1After) // -100 TEST
	tx1After := owner1.getTransactions(t, "alice")
	assert.Greater(t, len(tx1After), len(tx1Before)) // +1 tx

	// after: dan
	acc2After := owner2.getAccounts(t)
	assert.Equal(t, getValue(t, acc2Before, "dan")+100, getValue(t, acc2After, "dan"), acc2After) // +100 TEST
	tx2After := owner2.getTransactions(t, "dan")
	assert.Greater(t, len(tx2After), len(tx2Before)) // + 1 tx

	// on the sender side there may be several transactions, so we check the recipient
	txDan := owner2.getTransactions(t, "dan")
	lastTx := txDan[len(txDan)-1]
	assert.Equal(t, id, lastTx.Id, txDan)
	assert.Equal(t, lastTx.Amount.Value, int64(100))
	owner2.testIfAuditorMatchesOwnerHistory(t, []string{"dan"})
}

func TestRedeem(t *testing.T) {
	accBefore := owner1.getAccounts(t)
	id := owner1.redeem(t, "alice", 10, "test redeem")
	accAfter := owner1.getAccounts(t)

	transactions := owner1.getTransactions(t, "alice")
	assert.Equal(t, getValue(t, accBefore, "alice")-10, getValue(t, accAfter, "alice"), accAfter)
	lastTx := transactions[len(transactions)-1]
	assert.Equal(t, id, lastTx.Id, transactions)
	assert.Equal(t, lastTx.Amount.Value, int64(10))
	assert.Equal(t, "alice", lastTx.Sender)
	assert.Equal(t, "test redeem", lastTx.Message)
}

func TestIfAuditorMatchesOwnerHistory(t *testing.T) {
	owner1.testIfAuditorMatchesOwnerHistory(t, []string{"alice", "bob"})
	owner2.testIfAuditorMatchesOwnerHistory(t, []string{"carlos", "dan"})
}

func issue(t *testing.T, counterparty Counterparty, value int64) string {
	res, err := issuer.IssueWithResponse(context.TODO(), IssueJSONRequestBody{
		Amount: Amount{
			Code:  CODE,
			Value: value,
		},
		Counterparty: alice,
		Message:      new(string),
	})
	assert.NoError(t, err)
	assert.Nil(t, res.JSONDefault)
	assert.NotNil(t, res.JSON200)
	t.Logf(res.JSON200.Message)
	return res.JSON200.Payload
}

func getAuditorTransactions(t *testing.T, wallet string) []TransactionRecord {
	res, err := auditor.AuditorTransactionsWithResponse(context.TODO(), wallet)
	assert.NoError(t, err)
	assert.Nil(t, res.JSONDefault)
	assert.NotNil(t, res.JSON200)
	t.Logf(res.JSON200.Message)
	return res.JSON200.Payload
}

func (o *ownerAPI) testIfAuditorMatchesOwnerHistory(t *testing.T, accounts []string) {
	for _, w := range accounts {
		tx := o.getTransactions(t, w)
		audittx := getAuditorTransactions(t, w)
		assert.Equal(t, len(tx), len(audittx), w)

		// Timestamp is the time of storing the tx in the database
		// so it's not the same on both sides.
		for i := 0; i < len(tx); i++ {
			tx[i].Timestamp = time.Time{}
			audittx[i].Timestamp = time.Time{}
		}
		assert.Equal(t, tx, audittx)
	}
}

func (o *ownerAPI) getTransactions(t *testing.T, wallet string) []TransactionRecord {
	res, err := o.client.OwnerTransactionsWithResponse(context.TODO(), wallet)
	assert.NoError(t, err)
	assert.Nil(t, res.JSONDefault)
	assert.NotNil(t, res.JSON200)
	t.Logf(res.JSON200.Message)
	return res.JSON200.Payload
}

func (o *ownerAPI) transfer(t *testing.T, sender string, counterparty Counterparty, value int64) string {
	res, err := o.client.TransferWithResponse(context.TODO(), sender, TransferJSONRequestBody{
		Amount: Amount{
			Code:  CODE,
			Value: value,
		},
		Counterparty: counterparty,
		Message:      new(string),
	})
	assert.NoError(t, err)
	assert.Nil(t, res.JSONDefault)
	assert.NotNil(t, res.JSON200)
	t.Logf(res.JSON200.Message)
	return res.JSON200.Payload
}

func (o *ownerAPI) redeem(t *testing.T, wallet string, value int64, message string) string {
	res, err := o.client.RedeemWithResponse(context.TODO(), wallet, RedeemJSONRequestBody{
		Amount: Amount{
			Code:  CODE,
			Value: value,
		},
		Message: &message,
	})
	assert.NoError(t, err)
	assert.Nil(t, res.JSONDefault)
	assert.NotNil(t, res.JSON200)
	t.Logf(res.JSON200.Message)
	return res.JSON200.Payload
}

func (o *ownerAPI) getAccounts(t *testing.T) []Account {
	res, err := o.client.OwnerAccountsWithResponse(context.TODO())
	assert.NoError(t, err)
	assert.Nil(t, res.JSONDefault)
	assert.NotNil(t, res.JSON200)
	t.Logf(res.JSON200.Message)
	return res.JSON200.Payload
}

func getValue(t *testing.T, acc []Account, wallet string) int64 {
	for _, a := range acc {
		if a.Id == wallet {
			for _, b := range a.Balance {
				if b.Code == CODE {
					return b.Value
				}
			}
		}
	}
	t.Logf("%s value not found for wallet %s in %v", CODE, wallet, acc)
	return 0
}

// getEnv returns an environment variable or the fallback
func getEnv(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	return fallback
}
