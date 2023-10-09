/*
Copyright IBM Corp. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package service

import (
	"strconv"

	"github.com/hyperledger-labs/fabric-token-sdk/token"
	"github.com/hyperledger-labs/fabric-token-sdk/token/services/ttx"
	"github.com/pkg/errors"
)

// SERVICE
type BalanceByWallet map[string]ValueByTokenType
type ValueByTokenType map[string]int64

// GetAllBalances returns a map of all wallets with their balances per token type
func (s TokenService) GetAllBalances() (walletBalance BalanceByWallet, err error) {
	walletBalance = make(BalanceByWallet)
	wm := token.GetManagementService(s.FSC).WalletManager()
	wallets, err := wm.OwnerWalletIDs()
	if err != nil {
		return walletBalance, errors.Wrap(err, "can't get list of wallets")
	}
	logger.Infof("getting balances for %v", wallets)
	for _, w := range wallets {
		b, err := s.GetBalance(w, "")
		if err != nil {
			logger.Error(err)
			return walletBalance, err
		}
		walletBalance[w] = b
	}

	return
}

// GetBalance returns the balances per token type of a wallet
func (s TokenService) GetBalance(wallet string, tokenType string) (typeVal ValueByTokenType, err error) {
	typeVal = make(ValueByTokenType)

	// Tokens owned by identities in this wallet will be listed
	if wallet == "" {
		return typeVal, errors.New("no wallet id provided")
	}
	w := ttx.GetWallet(s.FSC, wallet)
	if w == nil {
		return nil, errors.Errorf("wallet not found: %s", wallet)
	}

	unspentTokens, err := w.ListUnspentTokens(ttx.WithType(tokenType))
	if len(unspentTokens.Tokens) == 0 {
		return typeVal, nil
	}
	// Add the value of all unspent tokens in the wallet
	for _, token := range unspentTokens.Tokens {
		val, err := strconv.ParseInt(token.Quantity, 0, 64)
		if err != nil {
			return typeVal, errors.Wrap(err, "Error parsing token "+token.Id.String())
		}
		typeVal[token.Type] += val
	}

	return
}
