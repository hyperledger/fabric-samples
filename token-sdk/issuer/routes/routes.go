/*
Copyright IBM Corp. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package routes

import (
	"context"
	"fmt"

	"github.com/hyperledger/fabric-samples/token-sdk/issuer/service"
)

type Controller struct {
	Service service.TokenService
}

// Issue tokens to an account
// (POST /issue)
func (c Controller) Issue(ctx context.Context, request IssueRequestObject) (IssueResponseObject, error) {
	code := request.Body.Amount.Code
	value := uint64(request.Body.Amount.Value)
	recipient := request.Body.Counterparty.Account
	recipientNode := request.Body.Counterparty.Node
	var message string
	if request.Body.Message != nil {
		message = *request.Body.Message
	}

	txID, err := c.Service.Issue(code, value, recipient, recipientNode, message)
	if err != nil {
		return IssuedefaultJSONResponse{
			Body: Error{
				Message: "can't issue tokens",
				Payload: err.Error(),
			},
			StatusCode: 500,
		}, nil
	}

	return Issue200JSONResponse{
		IssueSuccessJSONResponse: IssueSuccessJSONResponse{
			Message: fmt.Sprintf("issued %d %s to %s on %s", value, code, recipient, recipientNode),
			Payload: txID,
		},
	}, nil
}
