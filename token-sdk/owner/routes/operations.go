/*
Copyright IBM Corp. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package routes

import (
	"context"
)

type OperationsAPI struct{}

// (GET /readyz)
func (c Controller) Readyz(ctx context.Context, request ReadyzRequestObject) (ReadyzResponseObject, error) {
	// TODO: what defines readiness if the REST API is available after FSC?
	return Readyz200JSONResponse{
		HealthSuccessJSONResponse: HealthSuccessJSONResponse{
			Message: "ok",
		},
	}, nil
}

// (GET /healthz)
func (c Controller) Healthz(ctx context.Context, request HealthzRequestObject) (HealthzResponseObject, error) {
	// TODO: how to determine health?
	return Healthz200JSONResponse{
		HealthSuccessJSONResponse: HealthSuccessJSONResponse{
			Message: "ok",
		},
	}, nil
}
