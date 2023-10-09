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

// GetHistory returns the full transaction history for an auditor.
func (s TokenService) GetHistory(wallet string) (txs []TransactionHistoryItem, err error) {
	// get auditor wallet
	w := ttx.MyAuditorWallet(s.FSC)
	if w == nil {
		err = errors.New("failed getting default auditor wallet")
		logger.Error(err.Error())
		return txs, err
	}
	auditor := ttx.NewAuditor(s.FSC, w)

	// Get query executor
	aqe := auditor.NewQueryExecutor()
	defer aqe.Done()

	// This retrieves all transactions to *or* from the provided wallet.
	// See QueryTransactionsParams interface for additional filters.
	it, err := aqe.Transactions(ttxdb.QueryTransactionsParams{
		SenderWallet:    wallet,
		RecipientWallet: wallet,
	})
	if err != nil {
		return txs, errors.New("failed querying transactions")
	}
	defer it.Close()

	// we need transaction info to get the transient field (application metadata)
	tip := ttx.NewTransactionInfoProvider(s.FSC, token.GetManagementService(s.FSC))
	if tip == nil {
		return txs, errors.New("failed to get transactionInfoProvider")
	}

	// Return the list of audited transactions
	for {
		tx, err := it.Next()
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
		if err != nil {
			return txs, errors.New("failed iterating over transactions")
		}

		// set user provided message from transient field
		ti, err := tip.TransactionInfo(transaction.TxID)
		if err != nil {
			return txs, err
		}
		if ti.ApplicationMetadata != nil && string(ti.ApplicationMetadata["message"]) != "" {
			transaction.Message = string(ti.ApplicationMetadata["message"])
		}
		txs = append(txs, transaction)
	}
	return
}
