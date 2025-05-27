package error

import (
	"context"
	"errors"
	"fmt"
	"github.com/hyperledger/fabric-protos-go-apiv2/gateway"

	"github.com/hyperledger/fabric-gateway/pkg/client"
	"google.golang.org/grpc/status"
)

func ErrorHandling(err error) string {
	var endorseErr *client.EndorseError
	var submitErr *client.SubmitError
	var commitStatusErr *client.CommitStatusError
	var commitErr *client.CommitError
	var res string

	if errors.As(err, &endorseErr) {
		res = fmt.Sprintf("Endorse error for transaction %s with gRPC status %v: %s\n", endorseErr.TransactionID, status.Code(endorseErr), endorseErr)
	} else if errors.As(err, &submitErr) {
		res = fmt.Sprintf("Submit error for transaction %s with gRPC status %v: %s\n", submitErr.TransactionID, status.Code(submitErr), submitErr)
	} else if errors.As(err, &commitStatusErr) {
		if errors.Is(err, context.DeadlineExceeded) {
			res = fmt.Sprintf("Timeout waiting for transaction %s commit status: %s", commitStatusErr.TransactionID, commitStatusErr)
		} else {
			res = fmt.Sprintf("Error obtaining commit status for transaction %s with gRPC status %v: %s\n", commitStatusErr.TransactionID, status.Code(commitStatusErr), commitStatusErr)
		}
	} else if errors.As(err, &commitErr) {
		res = fmt.Sprintf("Transaction %s failed to commit with status %d: %s\n", commitErr.TransactionID, int32(commitErr.Code), err)
	} else {
		panic(fmt.Errorf("unexpected error type %T: %w", err, err))
	}

	statusErr := status.Convert(err)
	details := statusErr.Details()
	if len(details) > 0 {
		fmt.Println("Error Details:")

		for _, detail := range details {
			switch detail := detail.(type) {
			case *gateway.ErrorDetail:
				res = fmt.Sprintf("- address: %s; mspId: %s; message: %s\n", detail.Address, detail.MspId, detail.Message)
			}
		}
	}
	return res
}
