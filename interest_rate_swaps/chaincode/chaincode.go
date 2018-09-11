/*
Copyright IBM Corp. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package main

import (
	"encoding/json"
	"fmt"
	"strconv"
	"time"

	"github.com/hyperledger/fabric/core/chaincode/shim"
	"github.com/hyperledger/fabric/core/chaincode/shim/ext/statebased"
	pb "github.com/hyperledger/fabric/protos/peer"
)

/* InterestRateSwap represents an interest rate swap on the ledger
 * The swap is active between its start- and end-date.
 * At the specified interval, two parties A and B exchange the following payments:
 * A->B (PrincipalAmount * FixedRateBPS) / 100
 * B->A (PrincipalAmount * (ReferenceRateBPS + FloatingRateBPS)) / 100
 * We represent rates as basis points, with one basis point being equal to 1/100th
 * of 1% (see https://www.investopedia.com/terms/b/basispoint.asp)
 */
type InterestRateSwap struct {
	StartDate       time.Time
	EndDate         time.Time
	PaymentInterval time.Duration
	PrincipalAmount uint64
	FixedRateBPS    uint64
	FloatingRateBPS uint64
	ReferenceRate   string
}

/*
SwapManager is the chaincode that handles interest rate swaps.
The chaincode endorsement policy includes an auditing organization.
It provides the following functions:
-) createSwap: create swap with participants
-) calculatePayment: calculate what needs to be paid
-) settlePayment: mark payment done
-) setReferenceRate: for providers to set the reference rate

The SwapManager stores three different kinds of information on the ledger:
-) the actual swap data ("swap" + ID)
-) the payment information ("payment" + ID), if "none", the payment has been settled
-) the reference rate ("rr" + ID)
*/
type SwapManager struct {
}

// Init callback
func (cc *SwapManager) Init(stub shim.ChaincodeStubInterface) pb.Response {
	args := stub.GetArgs()
	if len(args) < 5 {
		return shim.Error("Insufficient number of arguments. Expected: <function> <auditor_MSPID> <audit_threshold> <rr_provider1_MSPID> <rr_provider1_rateID> ... <rr_providerN_MSPID> <rr_providerN_rateID>")
	}
	// set the limit above which the auditor needs to be involved, require it
	// to be endorsed by the auditor
	err := stub.PutState("audit_limit", args[2])
	if err != nil {
		return shim.Error(err.Error())
	}
	auditorEP, err := statebased.NewStateEP(nil)
	if err != nil {
		return shim.Error(err.Error())
	}
	err = auditorEP.AddOrgs(statebased.RoleTypePeer, string(args[1]))
	if err != nil {
		return shim.Error(err.Error())
	}
	epBytes, err := auditorEP.Policy()
	if err != nil {
		return shim.Error(err.Error())
	}
	err = stub.SetStateValidationParameter("audit_limit", epBytes)
	if err != nil {
		return shim.Error(err.Error())
	}

	// create the reference rates, require them to be endorsed by the provider
	for i := 3; i+1 < len(args); i += 2 {
		org := string(args[i])
		rrID := "rr" + string(args[i+1])
		err = stub.PutState(rrID, []byte("0"))
		if err != nil {
			return shim.Error(err.Error())
		}
		ep, err := statebased.NewStateEP(nil)
		if err != nil {
			return shim.Error(err.Error())
		}
		err = ep.AddOrgs(statebased.RoleTypePeer, org)
		if err != nil {
			return shim.Error(err.Error())
		}
		epBytes, err = ep.Policy()
		if err != nil {
			return shim.Error(err.Error())
		}
		err = stub.SetStateValidationParameter(rrID, epBytes)
		if err != nil {
			return shim.Error(err.Error())
		}
	}

	return shim.Success([]byte{})
}

// Invoke dispatcher
func (cc *SwapManager) Invoke(stub shim.ChaincodeStubInterface) pb.Response {
	funcName, _ := stub.GetFunctionAndParameters()
	if function, ok := functions[funcName]; ok {
		fmt.Printf("Invoking %s\n", funcName)
		return function(stub)
	}
	return shim.Error(fmt.Sprintf("Unknown function %s", funcName))
}

var functions = map[string]func(stub shim.ChaincodeStubInterface) pb.Response{
	"createSwap":       createSwap,
	"calculatePayment": calculatePayment,
	"settlePayment":    settlePayment,
	"setReferenceRate": setReferenceRate,
}

// Create a new swap among participants.
// The creation of the swap needs to be endorsed by the chaincode endorsement policy.
// Once created, the swap needs to be endorsed by its participants as well as the
// auditor in case the principal amount of the swap exceeds the audit threshold.
// This is enforced through the state-based endorsement policy that is set in this
// function.
// Parameters: swap ID, a JSONized InterestRateSwap, MSP ID of participant 1,
//             MSP ID of participant 2
func createSwap(stub shim.ChaincodeStubInterface) pb.Response {
	_, parameters := stub.GetFunctionAndParameters()
	if len(parameters) != 4 {
		return shim.Error("Wrong number of arguments supplied. Expected: <swap_ID> <interest_rate_swap_json> <participant1_MSPID> <participant2_MSPID>")
	}

	// create the swap
	swapID := "swap" + string(parameters[0])
	irsJSON := []byte(parameters[1])
	var irs InterestRateSwap
	err := json.Unmarshal(irsJSON, &irs)
	if err != nil {
		return shim.Error(err.Error())
	}
	err = stub.PutState(swapID, irsJSON)
	if err != nil {
		return shim.Error(err.Error())
	}

	// get the auditing threshold
	auditLimit, err := stub.GetState("audit_limit")
	if err != nil {
		return shim.Error(err.Error())
	}
	threshold, err := strconv.Atoi(string(auditLimit))
	if err != nil {
		return shim.Error(err.Error())
	}

	// set endorsers
	ep, err := statebased.NewStateEP(nil)
	if err != nil {
		return shim.Error(err.Error())
	}
	err = ep.AddOrgs(statebased.RoleTypePeer, parameters[2], parameters[3])
	if err != nil {
		return shim.Error(err.Error())
	}
	// if the swap principal amount exceeds the audit threshold set in init, the auditor needs to endorse as well
	if irs.PrincipalAmount > uint64(threshold) {
		fmt.Printf("Adding auditor for swap %s with prinicipal amount %v above threshold %v\n", parameters[0], irs.PrincipalAmount, uint64(threshold))
		err = ep.AddOrgs(statebased.RoleTypePeer, "auditor")
		if err != nil {
			return shim.Error(err.Error())
		}
	}

	// set the endorsement policy for the swap
	epBytes, err := ep.Policy()
	if err != nil {
		return shim.Error(err.Error())
	}
	err = stub.SetStateValidationParameter(swapID, epBytes)
	if err != nil {
		return shim.Error(err.Error())
	}

	// create and set the key for the payment
	paymentID := "payment" + string(parameters[0])
	err = stub.PutState(paymentID, []byte("none"))
	if err != nil {
		return shim.Error(err.Error())
	}
	err = stub.SetStateValidationParameter(paymentID, epBytes)
	if err != nil {
		return shim.Error(err.Error())
	}

	return shim.Success([]byte{})
}

// Calculate the payment due for a given swap
func calculatePayment(stub shim.ChaincodeStubInterface) pb.Response {
	_, parameters := stub.GetFunctionAndParameters()
	if len(parameters) != 1 {
		return shim.Error("Wrong number of arguments supplied. Expected: <swap_ID>")
	}

	// retrieve swap
	swapID := "swap" + parameters[0]
	irsJSON, err := stub.GetState(swapID)
	if err != nil {
		return shim.Error(err.Error())
	}
	if irsJSON == nil {
		return shim.Error(fmt.Sprintf("Swap %s does not exist", parameters[0]))
	}
	var irs InterestRateSwap
	err = json.Unmarshal(irsJSON, &irs)
	if err != nil {
		return shim.Error(err.Error())
	}

	// check if the previous payment has been settled
	paymentID := "payment" + parameters[0]
	paid, err := stub.GetState(paymentID)
	if err != nil {
		return shim.Error(err.Error())
	}
	if paid == nil {
		return shim.Error("Unexpected error: payment entry is nil. This should not happen.")
	}
	if string(paid) != "none" {
		return shim.Error("Previous payment has not been settled yet")
	}

	// get reference rate
	referenceRateBytes, err := stub.GetState("rr" + irs.ReferenceRate)
	if err != nil {
		return shim.Error(err.Error())
	}
	if referenceRateBytes == nil {
		return shim.Error(fmt.Sprintf("Reference rate %s not found", irs.ReferenceRate))
	}
	referenceRate, err := strconv.Atoi(string(referenceRateBytes))
	if err != nil {
		return shim.Error(err.Error())
	}

	// calculate payment
	p1 := int((irs.PrincipalAmount * irs.FixedRateBPS) / 100)
	p2 := int((irs.PrincipalAmount * (irs.FloatingRateBPS + uint64(referenceRate))) / 100)
	payment := strconv.Itoa(p1 - p2)
	err = stub.PutState(paymentID, []byte(payment))
	if err != nil {
		return shim.Error(err.Error())
	}

	return shim.Success([]byte(payment))
}

// Settle the payment for a given swap
func settlePayment(stub shim.ChaincodeStubInterface) pb.Response {
	_, parameters := stub.GetFunctionAndParameters()
	if len(parameters) != 1 {
		return shim.Error("Wrong number of arguments supplied. Expected: <swap_ID>")
	}
	paymentID := "payment" + parameters[0]
	paid, err := stub.GetState(paymentID)
	if err != nil {
		return shim.Error(err.Error())
	}
	if paid == nil {
		return shim.Error("Unexpected error: payment entry is nil. This should not happen.")
	}
	if string(paid) == "none" {
		return shim.Error("Payment has already been settled.")
	}
	err = stub.PutState(paymentID, []byte("none"))
	if err != nil {
		return shim.Error(err.Error())
	}
	return shim.Success([]byte{})
}

// Set the reference rate for a given rate provider
func setReferenceRate(stub shim.ChaincodeStubInterface) pb.Response {
	_, parameters := stub.GetFunctionAndParameters()
	if len(parameters) != 2 {
		return shim.Error("Wrong number of arguments supplied. Expected: <reference_rate_ID> <reference_rate_BPS>")
	}

	rrID := "rr" + parameters[0]
	err := stub.PutState(rrID, []byte(parameters[1]))
	if err != nil {
		return shim.Error(err.Error())
	}
	return shim.Success([]byte{})
}

func main() {
	err := shim.Start(new(SwapManager))
	if err != nil {
		fmt.Printf("Error starting IRS chaincode: %s", err)
	}
}
