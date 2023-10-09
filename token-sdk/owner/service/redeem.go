/*
Copyright IBM Corp. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package service

import (
	viewregistry "github.com/hyperledger-labs/fabric-smart-client/platform/view"
	"github.com/hyperledger-labs/fabric-smart-client/platform/view/view"
	"github.com/pkg/errors"

	token2 "github.com/hyperledger-labs/fabric-token-sdk/token"
	"github.com/hyperledger-labs/fabric-token-sdk/token/services/ttx"
	"github.com/hyperledger-labs/fabric-token-sdk/token/token"
)

// SERVICE

// Redeem burns an amount of a certain token. It prepares the transaction,
// gets it approved by the auditor and sends it to the blockchain for endorsement and commit.
func (s TokenService) RedeemTokens(tokenType string, quantity uint64, wallet string, message string) (txID string, err error) {
	logger.Infof("redeeming %d %s from [%s] with message [%s]", quantity, tokenType, wallet, message)
	res, err := viewregistry.GetManager(s.FSC).InitiateView(&RedeemView{
		Redeem: &Redeem{
			Wallet:    wallet,
			TokenType: tokenType,
			Quantity:  quantity,
			Message:   message,
		},
	})
	if err != nil {
		logger.Error(err)
		return
	}
	txID, ok := res.(string)
	if !ok {
		err = errors.New("cannot parse redeem response")
		logger.Error(err)
		return
	}

	return
}

// VIEW

// Redeem contains the input information for a redeem operation
type Redeem struct {
	// Wallet is the identifier of the wallet that owns the tokens to redeem
	Wallet string
	// TokenIDs contains a list of token ids to redeem. If empty, tokens are selected on the spot.
	TokenIDs []*token.ID
	// TokenType of tokens to redeem
	TokenType string
	// Quantity to redeem
	Quantity uint64
	// Message is an optional user message sent with the transaction.
	// It's stored in the ApplicationMetadata and is sent in the transient field.
	Message string
}

type RedeemView struct {
	*Redeem
}

func (v *RedeemView) Call(context view.Context) (interface{}, error) {
	// specify the auditor and create the envelope for the transaction
	logger.Debug("getting identity of auditor")
	auditor := viewregistry.GetIdentityProvider(context).Identity("auditor")
	if auditor == nil {
		return "", errors.New("auditor identity not found")
	}
	tx, err := ttx.NewTransaction(context, nil, ttx.WithAuditor(auditor))
	if err != nil {
		return "", errors.Wrap(err, "failed creating transaction")
	}

	// The sender will select tokens owned by this wallet
	logger.Debug("loading wallet [%s]", v.Wallet)
	senderWallet := ttx.GetWallet(context, v.Wallet)
	if senderWallet == nil {
		return "", errors.Errorf("sender wallet [%s] not found", v.Wallet)
	}

	// the sender adds a new redeem operation to the transaction following the instruction received.
	// Notice the use of `token2.WithTokenIDs(t.TokenIDs...)`. If t.TokenIDs is not empty, the Redeem
	// function uses those tokens, otherwise the tokens will be selected on the spot.
	// Token selection happens internally by invoking the default token selector:
	// selector, err := tx.TokenService().SelectorManager().NewSelector(tx.ID())
	// if err != nil {
	//	return "", errors.Wrap(err, "failed getting selector")
	// }
	// selector.Select(wallet, amount, tokenType)
	// It is also possible to pass a custom token selector to the Redeem function by using the relative opt:
	// token2.WithTokenSelector(selector).
	err = tx.Redeem(
		senderWallet,
		v.TokenType,
		v.Quantity,
		token2.WithTokenIDs(v.TokenIDs...),
	)
	if err != nil {
		return "", errors.Wrap(err, "failed adding tokens to redeem")
	}

	// You can set any metadata you want. It is shared with the
	// auditor but not committed to the ledger.
	if v.Message != "" {
		tx.SetApplicationMetadata("message", []byte(v.Message))
	}

	// The sender is ready to collect all the required signatures.
	// In this case, the sender's and the auditor's signatures.
	// Invoke the Token Chaincode to collect endorsements on the Token Request and prepare the relative transaction.
	// This is all done in one shot running the following view.
	logger.Infof("collecting signatures and submitting transaction to chaincode: [%s]", tx.ID())
	_, err = context.RunView(ttx.NewCollectEndorsementsView(tx))
	if err != nil {
		return "", errors.Wrap(err, "failed to sign transaction")
	}
	// Send to the ordering service and wait for finality
	logger.Infof("submitting fabric transaction to orderer for final settlemement: [%s]", tx.ID())
	_, err = context.RunView(ttx.NewOrderingAndFinalityView(tx))
	if err != nil {
		return "", errors.Wrap(err, "failed to order or commit transaction")
	}
	return tx.ID(), nil
}
