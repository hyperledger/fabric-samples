// Copyright the Hyperledger Fabric contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package internal

import (
	"errors"
	"fmt"
	"reflect"

	"github.com/hyperledger/fabric-contract-api-go/contractapi/utils"
	"github.com/hyperledger/fabric-contract-api-go/serializer"
)

// TransactionHandlerType enum for type of transaction handled
type TransactionHandlerType int

const (
	// TransactionHandlerTypeBefore before transaction type
	TransactionHandlerTypeBefore TransactionHandlerType = iota + 1
	// TransactionHandlerTypeUnknown before transaction type
	TransactionHandlerTypeUnknown
	// TransactionHandlerTypeAfter before transaction type
	TransactionHandlerTypeAfter
)

func (tht TransactionHandlerType) String() (string, error) {
	switch tht {
	case TransactionHandlerTypeBefore:
		return "Before", nil
	case TransactionHandlerTypeAfter:
		return "After", nil
	case TransactionHandlerTypeUnknown:
		return "Unknown", nil
	default:
		return "", errors.New("Invalid transaction handler type")
	}
}

// TransactionHandler extension of contract function that manages function which handles calls
// to before, after and unknown transaction functions
type TransactionHandler struct {
	ContractFunction
	handlesType TransactionHandlerType
}

// Call calls tranaction function using string args and handles formatting the response into useful types
func (th TransactionHandler) Call(ctx reflect.Value, data interface{}, serializer serializer.TransactionSerializer) (string, interface{}, error) {
	values := []reflect.Value{}

	if th.params.context != nil {
		values = append(values, ctx)
	}

	if th.handlesType == TransactionHandlerTypeAfter && len(th.params.fields) == 1 {
		if data == nil {
			values = append(values, reflect.Zero(reflect.TypeOf(new(utils.UndefinedInterface))))
		} else {
			values = append(values, reflect.ValueOf(data))
		}
	}

	someResp := th.function.Call(values)

	return th.handleResponse(someResp, nil, nil, serializer)
}

// NewTransactionHandler create a new transaction handler from a given function
func NewTransactionHandler(fn interface{}, contextHandlerType reflect.Type, handlesType TransactionHandlerType) (*TransactionHandler, error) {
	cf, err := NewContractFunctionFromFunc(fn, 0, contextHandlerType)

	if err != nil {
		str, _ := handlesType.String()
		return nil, fmt.Errorf("Error creating %s. %s", str, err.Error())
	} else if handlesType != TransactionHandlerTypeAfter && len(cf.params.fields) > 0 {
		str, _ := handlesType.String()
		return nil, fmt.Errorf("%s transactions may not take any params other than the transaction context", str)
	} else if handlesType == TransactionHandlerTypeAfter && len(cf.params.fields) > 1 {
		return nil, fmt.Errorf("After transactions must take at most one non-context param")
	} else if handlesType == TransactionHandlerTypeAfter && len(cf.params.fields) == 1 && cf.params.fields[0].Kind() != reflect.Interface {
		return nil, fmt.Errorf("After transaction must take type interface{} as their only non-context param")
	}

	th := TransactionHandler{
		*cf,
		handlesType,
	}

	return &th, nil
}
