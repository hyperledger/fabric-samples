/*
Copyright IBM Corp. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package service

import (
	"github.com/hyperledger-labs/fabric-smart-client/pkg/api"
	"github.com/hyperledger-labs/fabric-token-sdk/token/services/ttx"
	"github.com/pkg/errors"
)

type TokenService struct {
	FSC api.ServiceProvider
}

// SERVICE
type ValueByTokenType map[string]int64

// GetBalance returns the balances per token type of a wallet
func (s TokenService) GetBalance(wallet string, tokenType string) (typeVal ValueByTokenType, err error) {
	typeVal = make(ValueByTokenType)

	// get auditor wallet
	w := ttx.MyAuditorWallet(s.FSC)
	if w == nil {
		err = errors.New("failed getting default auditor wallet")
		logger.Error(err.Error())
		return
	}
	auditor := ttx.NewAuditor(s.FSC, w)

	aqe := auditor.NewQueryExecutor()
	defer aqe.Done()

	// TODO: how to get all TokenTypes separately?
	filter, err := aqe.NewHoldingsFilter().ByEnrollmentId(wallet).ByType(tokenType).Execute()
	if err != nil {
		err = errors.Wrapf(err, "failed retrieving holding for [%s][%s]", wallet, tokenType)
		logger.Error(err.Error())
		return
	}
	currentHolding := filter.Sum()

	typeVal[tokenType] = currentHolding.Int64()
	logger.Debugf("Current Holding: [%s][%s][%d]", wallet, tokenType, typeVal[tokenType])

	return
}
