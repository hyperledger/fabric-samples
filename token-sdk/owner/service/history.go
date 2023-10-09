/*
Copyright IBM Corp. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package service

import (
	"time"

	"github.com/hyperledger-labs/fabric-token-sdk/token"
	"github.com/hyperledger-labs/fabric-token-sdk/token/services/ttx"
	"github.com/hyperledger-labs/fabric-token-sdk/token/services/ttxdb"
	"github.com/pkg/errors"
)

// SERVICE

type TransactionHistoryItem struct {
	// TxID is the transaction ID
	TxID string
	// ActionType is the type of action performed by this transaction record
	ActionType int
	// SenderEID is the enrollment ID of the account that is sending tokens
	Sender string
	// RecipientEID is the enrollment ID of the account that is receiving tokens
	Recipient string
	// TokenType is the type of token
	TokenType string
	// Amount is positive if tokens are received. Negative otherwise
	Amount int64
	// Timestamp is the time the transaction was submitted to the db
	Timestamp time.Time
	// Status is the status of the transaction
	Status string
	// Message is the user message sent with the transaction. It comes from
	// the ApplicationMetadata and is sent in the transient field
	Message string
}

// GetHistory returns the full transaction history for an owner.
func (s TokenService) GetHistory(wallet string) (txs []TransactionHistoryItem, err error) {
	// Get query executor
	owner := ttx.NewOwner(s.FSC, token.GetManagementService(s.FSC))
	aqe := owner.NewQueryExecutor()
	defer aqe.Done()
	it, err := aqe.Transactions(ttxdb.QueryTransactionsParams{
		SenderWallet:    wallet,
		RecipientWallet: wallet,
	})
	if err != nil {
		return txs, errors.Wrap(err, "failed querying transactions from db")
	}
	defer it.Close()

	// we need transaction info to get the transient field (application metadata)
	tip := ttx.NewTransactionInfoProvider(s.FSC, token.GetManagementService(s.FSC))
	if tip == nil {
		return txs, errors.New("failed to get transactionInfoProvider")
	}

	// Return the list of accepted transactions
	for {
		tx, err := it.Next()
		if err != nil {
			return txs, errors.Wrap(err, "failed iterating over transactions")
		}
		if tx == nil {
			break
		}
		transaction := TransactionHistoryItem{
			TxID:       tx.TxID,
			ActionType: int(tx.ActionType),
			Sender:     tx.SenderEID,
			Recipient:  tx.RecipientEID,
			TokenType:  tx.TokenType,
			Amount:     tx.Amount.Int64(),
			Timestamp:  tx.Timestamp.UTC(),
			Status:     string(tx.Status),
		}
		// set user provided message from transient field
		ti, err := tip.TransactionInfo(tx.TxID)
		if err != nil {
			return txs, errors.Wrapf(err, "cannot get transaction info for %s", tx.TxID)
		}
		if ti.ApplicationMetadata != nil && string(ti.ApplicationMetadata["message"]) != "" {
			transaction.Message = string(ti.ApplicationMetadata["message"])
		}
		txs = append(txs, transaction)
	}
	return
}
