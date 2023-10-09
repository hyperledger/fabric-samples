/*
Copyright IBM Corp. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package service

import (
	"github.com/hyperledger-labs/fabric-smart-client/pkg/api"
	"github.com/hyperledger-labs/fabric-smart-client/platform/view/services/flogging"
	"github.com/hyperledger-labs/fabric-smart-client/platform/view/view"
	"github.com/hyperledger-labs/fabric-token-sdk/token/services/ttx"
	"github.com/pkg/errors"
)

var logger = flogging.MustGetLogger("owner")

type TokenService struct {
	FSC api.ServiceProvider
}

type AcceptCashView struct{}

func (v *AcceptCashView) Call(context view.Context) (interface{}, error) {
	logger.Infof("incoming session from [%s]", context.Session().Info().Endpoint)

	// The recipient of a token (issued or transfer) responds, as first operation,
	// to a request for a recipient. The recipient identity is a new nym key and not the main key of the wallet.
	id, err := ttx.RespondRequestRecipientIdentity(context)
	if err != nil {
		return "", errors.Wrap(err, "failed to respond to identity request")
	}
	logger.Infof("shared recipient id: [%s]", id.UniqueID())

	// After we responded with the recipient identity, the counterparty assembles
	// the transaction that assigns the tokens to the recipient id and sends it to us.
	tx, err := ttx.ReceiveTransaction(context)
	if err != nil {
		err = errors.Wrap(err, "failed to receive tokens")
		logger.Error(err.Error())
		return "", err
	}
	logger.Infof("transaction received: [%s]", tx.ID())

	// The recipient can perform any check on the transaction as required by the business process
	// In particular, here, the recipient checks that the transaction contains at least one output, and
	// that there is at least one output that names the recipient. (The recipient is receiving something.
	outputs, err := tx.Outputs()
	if err != nil {
		err = errors.Wrap(err, "failed getting outputs")
		logger.Error(err.Error())
		return "", err
	}
	if outputs.Count() <= 0 {
		err = errors.New("outputs should be more than zero")
		logger.Error(err.Error())
		return "", err
	}
	if outputs.ByRecipient(id).Count() <= 0 {
		err = errors.New("outputs to me should be more than zero")
		logger.Error(err.Error())
		return "", err
	}

	// If everything is fine, the recipient accepts and sends back her signature.
	// Notice that, a signature from the recipient might or might not be required to make the transaction valid.
	// This depends on the driver implementation.
	_, err = context.RunView(ttx.NewAcceptView(tx))
	if err != nil {
		return "", errors.Wrap(err, "failed to accept new tokens")
	}
	logger.Infof("transaction accepted: [%s]", tx.ID())

	// Before completing, the recipient waits for finality of the transaction
	_, err = context.RunView(ttx.NewFinalityView(tx))
	if err != nil {
		return "", errors.Wrap(err, "new tokens were not committed")
	}
	logger.Infof("transaction committed: [%s]", tx.ID())

	return nil, nil
}
