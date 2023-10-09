/*
Copyright IBM Corp. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package routes

import (
	"context"
	"fmt"

	"github.com/hyperledger/fabric-samples/token-sdk/owner/service"
)

type Controller struct {
	Service service.TokenService
}

// Transfer tokens to another account
// (POST /owner/accounts/{id}/transfer)
func (c Controller) Transfer(ctx context.Context, request TransferRequestObject) (TransferResponseObject, error) {
	code := request.Body.Amount.Code
	value := uint64(request.Body.Amount.Value)
	sender := request.Id
	recipient := request.Body.Counterparty.Account
	recipientNode := request.Body.Counterparty.Node
	var message string
	if request.Body.Message != nil {
		message = *request.Body.Message
	}

	txID, err := c.Service.TransferTokens(code, value, sender, recipient, recipientNode, message)
	if err != nil {
		return TransferdefaultJSONResponse{
			Body: Error{
				Message: "can't transfer funds",
				Payload: err.Error(),
			},
			StatusCode: 500,
		}, nil
	}
	return Transfer200JSONResponse{
		TransferSuccessJSONResponse: TransferSuccessJSONResponse{
			Message: fmt.Sprintf("%s transferred %d %s to %s", sender, value, code, recipient),
			Payload: txID,
		},
	}, nil
}

// Get all accounts on this node and their balances
// (GET /owner/accounts)
func (c Controller) OwnerAccounts(ctx context.Context, request OwnerAccountsRequestObject) (OwnerAccountsResponseObject, error) {
	balances, err := c.Service.GetAllBalances()
	if err != nil {
		return OwnerAccountsdefaultJSONResponse{
			Body: Error{
				Message: "can't get accounts",
				Payload: err.Error(),
			},
			StatusCode: 500,
		}, nil
	}

	acc := []Account{}
	for wallet, balance := range balances {
		amounts := []Amount{}
		for typ, val := range balance {
			amounts = append(amounts, Amount{
				Code:  typ,
				Value: val,
			})
		}
		acc = append(acc, Account{
			Id:      wallet,
			Balance: amounts,
		})
	}
	return OwnerAccounts200JSONResponse{
		AccountsSuccessJSONResponse: AccountsSuccessJSONResponse{
			Message: fmt.Sprintf("got %d accounts", len(acc)),
			Payload: acc,
		},
	}, err
}

// Get an account and their balances
// (GET /owner/accounts/{id})
func (c Controller) OwnerAccount(ctx context.Context, request OwnerAccountRequestObject) (OwnerAccountResponseObject, error) {
	var code string
	if request.Params.Code != nil {
		code = *request.Params.Code
	}
	balance, err := c.Service.GetBalance(request.Id, code)
	if err != nil {
		return OwnerAccountdefaultJSONResponse{
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
	return OwnerAccount200JSONResponse{
		AccountSuccessJSONResponse: AccountSuccessJSONResponse{
			Message: fmt.Sprintf("got balances for %s", request.Id),
			Payload: Account{
				Id:      request.Id,
				Balance: amounts,
			},
		},
	}, nil
}

// Get all transactions for an account
// (GET /owner/accounts/{id}/transactions)
func (c Controller) OwnerTransactions(ctx context.Context, request OwnerTransactionsRequestObject) (OwnerTransactionsResponseObject, error) {
	var history []service.TransactionHistoryItem
	var err error

	history, err = c.Service.GetHistory(request.Id)
	if err != nil {
		return OwnerTransactionsdefaultJSONResponse{
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
	return OwnerTransactions200JSONResponse{
		TransactionsSuccessJSONResponse: TransactionsSuccessJSONResponse{
			Message: fmt.Sprintf("got %d transactions for %s", len(pl), request.Id),
			Payload: pl,
		},
	}, nil
}

// Redeem (burn) tokens
// (POST /owner/accounts/{id}/redeem)
func (c Controller) Redeem(ctx context.Context, request RedeemRequestObject) (RedeemResponseObject, error) {
	code := request.Body.Amount.Code
	value := uint64(request.Body.Amount.Value)
	account := request.Id
	var message string
	if request.Body.Message != nil {
		message = *request.Body.Message
	}

	txID, err := c.Service.RedeemTokens(code, value, account, message)
	if err != nil {
		return RedeemdefaultJSONResponse{
			Body: Error{
				Message: "can't redeem tokens",
				Payload: err.Error(),
			},
			StatusCode: 500,
		}, nil
	}

	return Redeem200JSONResponse{
		RedeemSuccessJSONResponse: RedeemSuccessJSONResponse{
			Message: fmt.Sprintf("%s redeemed %d %s", account, value, code),
			Payload: txID,
		},
	}, nil
}
