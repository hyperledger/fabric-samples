/*
Copyright IBM Corp. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package service

import (
	"github.com/hyperledger-labs/fabric-smart-client/platform/view/services/flogging"
	"github.com/hyperledger-labs/fabric-smart-client/platform/view/view"
	"github.com/hyperledger-labs/fabric-token-sdk/token/services/ttx"
	"github.com/pkg/errors"
)

var logger = flogging.MustGetLogger("service")

// VIEW

// Auditing is initiated as a response to an audit request from another
// FSC node (not via an internal service or API).

type AuditView struct{}

func (v *AuditView) Call(context view.Context) (interface{}, error) {
	logger.Infof("incoming session from [%s]", context.Session().Info().Endpoint)
	tx, err := ttx.ReceiveTransaction(context)
	if err != nil {
		err = errors.Wrap(err, "failed receiving transaction")
		logger.Error(err.Error())
		return "", err
	}
	// get auditor wallet
	w := ttx.MyAuditorWallet(context)
	if w == nil {
		err = errors.New("failed getting default auditor wallet")
		logger.Error(err.Error())
		return "", err
	}
	auditor := ttx.NewAuditor(context, w)

	// Validate
	err = auditor.Validate(tx)
	if err != nil {
		err = errors.Wrapf(err, "transaction invalid: [%s]", tx.ID())
		logger.Error(err.Error())
		return "", err
	}
	// See https://github.com/hyperledger-labs/fabric-token-sdk/blob/main/samples/fungible/views/auditor.go for examples of auditor checks

	logger.Infof("transaction valid: [%s]", tx.ID())
	res, err := context.RunView(ttx.NewAuditApproveView(w, tx))
	if err != nil {
		logger.Error(err.Error())
		return "", err
	}
	logger.Infof("transaction committed: [%s]", tx.ID())

	return res, err
}

type RegisterAuditorView struct{}

func (r *RegisterAuditorView) Call(context view.Context) (interface{}, error) {
	return context.RunView(ttx.NewRegisterAuditorView(
		&AuditView{},
	))
}
