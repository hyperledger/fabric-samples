/*
Copyright IBM Corp. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package routes

import (
	"context"
	"fmt"

	"github.com/hyperledger/fabric-samples/token-sdk/auditor/service"
)

type Controller struct {
	Service service.TokenService
}

// Get an account and their balance of a certain type
// (GET /auditor/accounts/{id})
func (c Controller) AuditorAccount(ctx context.Context, request AuditorAccountRequestObject) (AuditorAccountResponseObject, error) {
	if request.Params.Code == nil {
		return AuditorAccountdefaultJSONResponse{
			Body: Error{
				Message: "code is required",
				Payload: "",
			},
			StatusCode: 400,
		}, nil
	}

	balance, err := c.Service.GetBalance(request.Id, *request.Params.Code)
	if err != nil {
		return AuditorAccountdefaultJSONResponse{
			Body: Error{
				Message: "can't get account",
				Payload: err.Error(),
			},
			StatusCode: 500,
		}, nil
	}

	amounts := []Amount{}
	for typ, val := range balance {
		amounts = append(amounts, Amount{
			Code:  typ,
			Value: val,
		})
	}
	return AuditorAccount200JSONResponse{
		AccountSuccessJSONResponse: AccountSuccessJSONResponse{
			Message: fmt.Sprintf("got %s's %s", request.Id, *request.Params.Code),
			Payload: Account{
				Id:      request.Id,
				Balance: amounts,
			},
		},
	}, nil
}

// Get all transactions for an account
// (GET /owner/accounts/{id}/transactions)
func (c Controller) AuditorTransactions(ctx context.Context, request AuditorTransactionsRequestObject) (AuditorTransactionsResponseObject, error) {
	var history []service.TransactionHistoryItem
	var err error

	history, err = c.Service.GetHistory(request.Id)
	if err != nil {
		return AuditorTransactionsdefaultJSONResponse{
			Body: Error{
				Message: "can't get history",
				Payload: err.Error(),
			},
			StatusCode: 500,
		}, nil
	}

	pl := []TransactionRecord{}
	for _, tx := range history {
		pl = append(pl, TransactionRecord{
			Amount: Amount{
				Code:  tx.TokenType,
				Value: tx.Amount,
			},
			Id:        tx.TxID,
			Recipient: tx.Recipient,
			Sender:    tx.Sender,
			Status:    tx.Status,
			Timestamp: tx.Timestamp,
			Message:   tx.Message,
		})
	}
	return AuditorTransactions200JSONResponse{
		TransactionsSuccessJSONResponse: TransactionsSuccessJSONResponse{
			Message: fmt.Sprintf("got %d transactions for %s", len(pl), request.Id),
			Payload: pl,
		},
	}, nil
}
