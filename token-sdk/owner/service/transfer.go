/*
Copyright IBM Corp. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package service

import (
	viewregistry "github.com/hyperledger-labs/fabric-smart-client/platform/view"
	"github.com/hyperledger-labs/fabric-smart-client/platform/view/view"
	"github.com/hyperledger-labs/fabric-token-sdk/token/services/ttx"
	"github.com/pkg/errors"
)

// SERVICE

// TransferTokens transfers an amount of a certain token. It connects to the other node, prepares the transaction,
// gets it approved by the auditor and sends it to the blockchain for endorsement and commit.
func (s TokenService) TransferTokens(tokenType string, quantity uint64, sender string, recipient string, recipientNode string, message string) (txID string, err error) {
	logger.Infof("going to transfer %d %s from [%s] to [%s] on [%s] with message [%s]", quantity, tokenType, sender, recipient, recipientNode, message)
	res, err := viewregistry.GetManager(s.FSC).InitiateView(&TransferView{
		Transfer: &Transfer{
			Wallet:        sender,
			TokenType:     tokenType,
			Quantity:      quantity,
			Recipient:     recipient,
			RecipientNode: recipientNode,
			Message:       message,
		},
	})
	if err != nil {
		logger.Error(err)
		return
	}
	txID, ok := res.(string)
	if !ok {
		err = errors.New("cannot parse transfer response")
		logger.Error(err)
		return
	}
	return
}

// VIEW

// Transfer contains the input information for a transfer
type Transfer struct {
	// Wallet is the identifier of the wallet that owns the tokens to transfer
	Wallet string
	// TokenType of tokens to transfer
	TokenType string
	// Quantity to transfer
	Quantity uint64
	// RecipientNode is the identity of the recipient's FSC node
	RecipientNode string
	// Recipient is the identity of the recipient's wallet
	Recipient string
	// Message is an optional user message sent with the transaction.
	// It's stored in the ApplicationMetadata and is sent in the transient field.
	Message string
}

type TransferView struct {
	*Transfer
}

func (v *TransferView) Call(context view.Context) (interface{}, error) {
	// As a first step operation, the sender tries its own node or contacts the recipients
	// FSC node to ask for the identity to use to assign ownership of the freshly created token.
	var recipient view.Identity
	var err error
	w := ttx.GetWallet(context, v.Recipient)
	if w != nil {
		// Get recipient identity from own wallet
		logger.Infof("getting local identity for %s", v.Recipient)
		recipient, err = w.GetRecipientIdentity()
		if err != nil {
			return nil, errors.Wrapf(err, "failed getting recipient identity from own node: %s", v.Recipient)
		}
	} else {
		node := view.Identity(v.RecipientNode)
		rec := view.Identity(v.Recipient)
		eps := viewregistry.GetEndpointService(context)
		if !eps.IsBoundTo(node, rec) {
			logger.Infof("binding [%s] to node [%s]", v.Recipient, v.RecipientNode)
			eps.Bind(node, rec) // TODO: it doesn't forget a wrong binding
		}

		// Request recipient identity from other node
		logger.Infof("requesting [%s] identity from [%s]", v.Recipient, v.RecipientNode)
		recipient, err = ttx.RequestRecipientIdentity(context, rec)
		if err != nil {
			return "", errors.Wrapf(err, "failed getting recipient identity from %s", v.RecipientNode)
		}
	}

	// specify the auditor and create the envelope for the transaction
	logger.Debug("getting identity of auditor")
	auditor := viewregistry.GetIdentityProvider(context).Identity("auditor") // TODO: should not be hardcoded
	if auditor == nil {
		return "", errors.New("auditor identity not found")
	}
	tx, err := ttx.NewTransaction(context, nil, ttx.WithAuditor(auditor))
	if err != nil {
		return "", errors.Wrap(err, "failed creating transaction")
	}

	// The sender will select tokens owned by this wallet
	senderWallet := ttx.GetWallet(context, v.Wallet)
	if senderWallet == nil {
		return "", errors.Errorf("sender wallet [%s] not found", v.Wallet)
	}

	// The sender adds a new transfer operation to the transaction following the instruction received.
	// Notice the use of `token2.WithTokenIDs(v.TokenIDs...)`. If v.TokenIDs is not empty, the Transfer
	// function uses those tokens, otherwise the tokens will be selected on the spot.
	// Token selection happens internally by invoking the default token selector:
	// selector, err := tx.TokenService().SelectorManager().NewSelector(tx.ID())
	// assert.NoError(err, "failed getting selector")
	// selector.Select(wallet, amount, tokenType)
	// It is also possible to pass a custom token selector to the Transfer function by using the relative opt:
	// token2.WithTokenSelector(selector).
	err = tx.Transfer(
		senderWallet,
		v.TokenType,
		[]uint64{v.Quantity},
		[]view.Identity{recipient},
	)
	if err != nil {
		return "", errors.Wrap(err, "failed adding new tokens")
	}
	// You can set any metadata you want. It is shared with the recipient and
	// auditor but not committed to the ledger.
	if v.Message != "" {
		tx.SetApplicationMetadata("message", []byte(v.Message))
	}

	// The sender is ready to collect all the required signatures.
	// In this case, the sender's and the auditor's signatures.
	// Invoke the Token Chaincode to collect endorsements on the Token Request and prepare the relative transaction.
	// This is all done in one shot running the following view.
	// Before completing, all recipients receive the approved transaction.
	// Depending on the token driver implementation, the recipient's signature might or might not be needed to make
	// the token transaction valid.
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
