/*
Copyright IBM Corp. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package service

import (
	"github.com/hyperledger-labs/fabric-smart-client/pkg/api"
	viewregistry "github.com/hyperledger-labs/fabric-smart-client/platform/view"
	"github.com/hyperledger-labs/fabric-smart-client/platform/view/services/flogging"
	"github.com/hyperledger-labs/fabric-smart-client/platform/view/view"
	"github.com/hyperledger-labs/fabric-token-sdk/token/services/ttx"
	"github.com/pkg/errors"
)

var logger = flogging.MustGetLogger("service")

// SERVICE

type TokenService struct {
	FSC api.ServiceProvider
}

// Issue issues an amount of tokens to a wallet. It connects to the other node, prepares the transaction,
// gets it approved by the auditor and sends it to the blockchain for endorsement and commit.
func (s TokenService) Issue(tokenType string, quantity uint64, recipient string, recipientNode string, message string) (txID string, err error) {
	logger.Infof("going to issue %d %s to [%s] on [%s] with message [%s]", quantity, tokenType, recipient, recipientNode, message)
	res, err := viewregistry.GetManager(s.FSC).InitiateView(&IssueCashView{
		IssueCash: &IssueCash{
			TokenType:     tokenType,
			Quantity:      quantity,
			Recipient:     recipient,
			RecipientNode: recipientNode,
			Message:       message,
		},
	})
	if err != nil {
		logger.Errorf("error issuing: %s", err.Error())
		return
	}
	txID, ok := res.(string)
	if !ok {
		return "", errors.New("cannot parse issue response")
	}
	logger.Infof("issued %d %s to [%s] on [%s] with message [%s]. ID: [%s]", quantity, tokenType, recipient, recipientNode, message, txID)
	return
}

// VIEW

// IssueCash contains the input information to issue a token
type IssueCash struct {
	// TokenType is the type of token to issue
	TokenType string
	// Quantity represent the number of units of a certain token type stored in the token
	Quantity uint64
	// Recipient is an identifier of the recipient identity
	Recipient string
	// RecipientNode is the identifier of the node of the recipient
	RecipientNode string
	// Message is the message that will be visible to the recipient and the auditor
	Message string
}

type IssueCashView struct {
	*IssueCash
}

func (v *IssueCashView) Call(context view.Context) (interface{}, error) {
	// Is the wallet on our node?
	// tms := token.GetManagementService(context)
	// if w := tms.WalletManager().OwnerWalletByIdentity(view.Identity(v.Recipient)); w != nil {
	// 	logger.Infof("%s", v.Recipient)
	// }

	node := view.Identity(v.RecipientNode)
	rec := view.Identity(v.Recipient)
	eps := viewregistry.GetEndpointService(context)
	if !eps.IsBoundTo(node, rec) {
		logger.Infof("binding [%s] to node [%s]", v.Recipient, v.RecipientNode)
		eps.Bind(node, rec) // TODO: it doesn't forget a wrong binding
	}
	// // Debug information
	// epr, err := eps.Endpoint(rec)
	// if err != nil {
	// 	logger.Errorf(err.Error())
	// }
	// logger.Infof("recipient node: %s", epr["P2P"])

	// As a first step operation, the issuer contacts the recipient's FSC node
	// to ask for the identity to use to assign ownership of the freshly created token.
	// Notice that, this step would not be required if the issuer knew already which
	// identity the recipient wants to use.
	logger.Infof("requesting [%s] identity from [%s]", v.Recipient, v.RecipientNode)
	recipient, err := ttx.RequestRecipientIdentity(context, rec)
	if err != nil {
		return "", errors.Wrapf(err, "failed getting recipient identity from %s", v.RecipientNode)
	}

	// Prepare the transaction and specify the auditor that will approve it.
	logger.Debug("getting identity of auditor")
	auditor := viewregistry.GetIdentityProvider(context).Identity("auditor")
	if auditor == nil {
		return "", errors.New("auditor identity not found")
	}
	tx, err := ttx.NewTransaction(context, nil, ttx.WithAuditor(auditor))
	if err != nil {
		return "", errors.Wrap(err, "failed creating transaction")
	}

	// You can set any metadata you want. It is shared with the recipient and
	// auditor but not committed to the ledger. We used 'message' here to let
	// the user share messages that will be shown in the transaction history.
	if v.Message != "" {
		tx.SetApplicationMetadata("message", []byte(v.Message))
	}

	// Get issuer wallet
	logger.Debug("loading issuer wallet")
	wallet := ttx.MyIssuerWallet(context)
	if wallet == nil {
		return "", errors.Errorf("issuer wallet not found")
	}

	// The issuer adds a new issue operation to the transaction to issue
	// the amount to the recipient id recieved from the owner's node.
	err = tx.Issue(
		wallet,
		recipient,
		v.TokenType,
		v.Quantity,
	)
	if err != nil {
		return "", errors.Wrap(err, "failed adding new issued token")
	}

	// The issuer is ready to collect all the required signatures.
	// In this case, the issuer's and the auditor's signatures.
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
	// Last but not least, the issuer sends the transaction for ordering and waits for transaction finality.
	logger.Infof("submitting fabric transaction to orderer for final settlemement: [%s]", tx.ID())
	_, err = context.RunView(ttx.NewOrderingAndFinalityView(tx))
	if err != nil {
		return "", errors.Wrap(err, "failed to order or commit transaction")
	}
	return tx.ID(), nil
}
