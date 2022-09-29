// Copyright the Hyperledger Fabric contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package internal

import (
	"errors"
	"fmt"
	"reflect"

	"github.com/hyperledger/fabric-contract-api-go/internal/types"
	metadata "github.com/hyperledger/fabric-contract-api-go/metadata"
	"github.com/hyperledger/fabric-contract-api-go/serializer"
)

type contractFunctionParams struct {
	context reflect.Type
	fields  []reflect.Type
}

type contractFunctionReturns struct {
	success reflect.Type
	error   bool
}

// CallType enum for type of call that should be used for method submit vs evaluate
type CallType int

const (
	// CallTypeNA contract function isnt callabale by invoke/query
	CallTypeNA = iota
	// CallTypeSubmit contract function should be called by invoke
	CallTypeSubmit
	// CallTypeEvaluate contract function should be called by query
	CallTypeEvaluate
)

// ContractFunction contains a description of a function so that it can be called by a chaincode
type ContractFunction struct {
	function reflect.Value
	callType CallType
	params   contractFunctionParams
	returns  contractFunctionReturns
}

// Call calls function in a contract using string args and handles formatting the response into useful types
func (cf ContractFunction) Call(ctx reflect.Value, supplementaryMetadata *metadata.TransactionMetadata, components *metadata.ComponentMetadata, serializer serializer.TransactionSerializer, params ...string) (string, interface{}, error) {
	var parameterMetadata []metadata.ParameterMetadata
	if supplementaryMetadata != nil {
		parameterMetadata = supplementaryMetadata.Parameters
	}

	values, err := cf.formatArgs(ctx, parameterMetadata, components, params, serializer)

	if err != nil {
		return "", nil, err
	}

	someResp := cf.function.Call(values)

	var returnsMetadata *metadata.ReturnMetadata
	if supplementaryMetadata != nil {
		returnsMetadata = &supplementaryMetadata.Returns
	}

	return cf.handleResponse(someResp, returnsMetadata, components, serializer)
}

// ReflectMetadata returns the metadata for contract function
func (cf ContractFunction) ReflectMetadata(name string, existingComponents *metadata.ComponentMetadata) metadata.TransactionMetadata {
	transactionMetadata := metadata.TransactionMetadata{}
	transactionMetadata.Name = name
	transactionMetadata.Tag = []string{}

	txType := "submit"

	if cf.callType == CallTypeEvaluate {
		txType = "evaluate"
	}

	transactionMetadata.Tag = append(transactionMetadata.Tag, txType)

	for index, field := range cf.params.fields {
		schema, _ := metadata.GetSchema(field, existingComponents)

		param := metadata.ParameterMetadata{}
		param.Name = fmt.Sprintf("param%d", index)
		param.Schema = schema

		transactionMetadata.Parameters = append(transactionMetadata.Parameters, param)
	}

	if cf.returns.success != nil {
		schema, _ := metadata.GetSchema(cf.returns.success, existingComponents)

		transactionMetadata.Returns = metadata.ReturnMetadata{Schema: schema}
	}

	return transactionMetadata
}

type formatArgResult struct {
	paramName string
	converted reflect.Value
	err       error
}

func (cf *ContractFunction) formatArgs(ctx reflect.Value, supplementaryMetadata []metadata.ParameterMetadata, components *metadata.ComponentMetadata, params []string, serializer serializer.TransactionSerializer) ([]reflect.Value, error) {
	numParams := len(cf.params.fields)

	if supplementaryMetadata != nil {
		if len(supplementaryMetadata) != numParams {
			return nil, fmt.Errorf("Incorrect number of params in supplementary metadata. Expected %d, received %d", numParams, len(supplementaryMetadata))
		}
	}

	values := []reflect.Value{}

	if cf.params.context != nil {
		values = append(values, ctx)
	}

	if len(params) < numParams {
		return nil, fmt.Errorf("Incorrect number of params. Expected %d, received %d", numParams, len(params))
	}

	channels := []chan formatArgResult{}

	for i := 0; i < numParams; i++ {

		fieldType := cf.params.fields[i]

		var paramMetadata *metadata.ParameterMetadata

		if supplementaryMetadata != nil {
			paramMetadata = &supplementaryMetadata[i]
		}

		c := make(chan formatArgResult)
		go cf.formatArg(params[i], fieldType, paramMetadata, components, serializer, c)
		channels = append(channels, c)
	}

	for _, channel := range channels {
		for res := range channel {

			if res.err != nil {
				return nil, fmt.Errorf("Error managing parameter%s. %s", res.paramName, res.err.Error())
			}

			values = append(values, res.converted)
		}
	}

	return values, nil
}

func (cf *ContractFunction) formatArg(param string, fieldType reflect.Type, parameterMetadata *metadata.ParameterMetadata, components *metadata.ComponentMetadata, serializer serializer.TransactionSerializer, c chan formatArgResult) {
	defer close(c)

	converted, err := serializer.FromString(param, fieldType, parameterMetadata, components)

	paramName := ""

	if parameterMetadata != nil {
		paramName = " " + parameterMetadata.Name
	}

	res := new(formatArgResult)
	res.paramName = paramName
	res.converted = converted
	res.err = err

	c <- *res
}

func (cf *ContractFunction) handleResponse(response []reflect.Value, returnsMetadata *metadata.ReturnMetadata, components *metadata.ComponentMetadata, serializer serializer.TransactionSerializer) (string, interface{}, error) {
	expectedLength := 0

	returnsSuccess := cf.returns.success != nil

	if returnsSuccess && cf.returns.error {
		expectedLength = 2
	} else if returnsSuccess || cf.returns.error {
		expectedLength = 1
	}

	if len(response) == expectedLength {

		var successResponse reflect.Value
		var errorResponse reflect.Value

		if returnsSuccess && cf.returns.error {
			successResponse = response[0]
			errorResponse = response[1]
		} else if returnsSuccess {
			successResponse = response[0]
		} else if cf.returns.error {
			errorResponse = response[0]
		}

		var successString string
		var errorError error
		var iface interface{}

		if successResponse.IsValid() {
			if serializer != nil {
				var err error
				successString, err = serializer.ToString(successResponse, cf.returns.success, returnsMetadata, components)

				if err != nil {
					return "", nil, fmt.Errorf("Error handling success response. %s", err.Error())
				}
			}

			iface = successResponse.Interface()
		}

		if errorResponse.IsValid() && !errorResponse.IsNil() {
			errorError = errorResponse.Interface().(error)
		}

		return successString, iface, errorError
	}

	return "", nil, errors.New("response does not match expected return for given function")
}

func newContractFunction(fnValue reflect.Value, callType CallType, paramDetails contractFunctionParams, returnDetails contractFunctionReturns) *ContractFunction {
	cf := ContractFunction{}
	cf.callType = callType
	cf.function = fnValue
	cf.params = paramDetails
	cf.returns = returnDetails

	return &cf
}

// NewContractFunctionFromFunc creates a new contract function from a given function
func NewContractFunctionFromFunc(fn interface{}, callType CallType, contextHandlerType reflect.Type) (*ContractFunction, error) {
	fnType := reflect.TypeOf(fn)
	fnValue := reflect.ValueOf(fn)

	if fnType.Kind() != reflect.Func {
		return nil, fmt.Errorf("Cannot create new contract function from %s. Can only use func", fnType.Kind())
	}

	myMethod := reflect.Method{}
	myMethod.Func = fnValue
	myMethod.Type = fnType

	paramDetails, returnDetails, err := parseMethod(myMethod, contextHandlerType)

	if err != nil {
		return nil, err
	}

	return newContractFunction(fnValue, callType, paramDetails, returnDetails), nil
}

// NewContractFunctionFromReflect creates a new contract function from a reflected method
func NewContractFunctionFromReflect(typeMethod reflect.Method, valueMethod reflect.Value, callType CallType, contextHandlerType reflect.Type) (*ContractFunction, error) {
	paramDetails, returnDetails, err := parseMethod(typeMethod, contextHandlerType)

	if err != nil {
		return nil, err
	}

	return newContractFunction(valueMethod, callType, paramDetails, returnDetails), nil
}

// Setup

func parseMethod(typeMethod reflect.Method, contextHandlerType reflect.Type) (contractFunctionParams, contractFunctionReturns, error) {
	myContractFnParams, err := methodToContractFunctionParams(typeMethod, contextHandlerType)

	if err != nil {
		return contractFunctionParams{}, contractFunctionReturns{}, err
	}

	myContractFnReturns, err := methodToContractFunctionReturns(typeMethod)

	if err != nil {
		return contractFunctionParams{}, contractFunctionReturns{}, err
	}

	return myContractFnParams, myContractFnReturns, nil
}

func methodToContractFunctionParams(typeMethod reflect.Method, contextHandlerType reflect.Type) (contractFunctionParams, error) {
	myContractFnParams := contractFunctionParams{}

	usesCtx := (reflect.Type)(nil)

	numIn := typeMethod.Type.NumIn()

	startIndex := 1
	methodName := typeMethod.Name

	if methodName == "" {
		startIndex = 0
		methodName = "Function"
	}

	for i := startIndex; i < numIn; i++ {
		inType := typeMethod.Type.In(i)

		typeError := typeIsValid(inType, nil, false)

		isCtx := inType == contextHandlerType

		if typeError != nil && !isCtx && i == startIndex && inType.Kind() == reflect.Interface {
			invalidInterfaceTypeErr := fmt.Sprintf("%s contains invalid transaction context interface type. Set transaction context for contract does not meet interface used in method.", methodName)

			err := typeMatchesInterface(contextHandlerType, inType)

			if err != nil {
				return contractFunctionParams{}, fmt.Errorf("%s %s", invalidInterfaceTypeErr, err.Error())
			}

			isCtx = true
		}

		if typeError != nil && !isCtx {
			return contractFunctionParams{}, fmt.Errorf("%s contains invalid parameter type. %s", methodName, typeError.Error())
		} else if i != startIndex && isCtx {
			return contractFunctionParams{}, fmt.Errorf("Functions requiring the TransactionContext must require it as the first parameter. %s takes it in as parameter %d", methodName, i-startIndex)
		} else if isCtx {
			usesCtx = contextHandlerType
		} else {
			myContractFnParams.fields = append(myContractFnParams.fields, inType)
		}
	}

	myContractFnParams.context = usesCtx
	return myContractFnParams, nil
}

func methodToContractFunctionReturns(typeMethod reflect.Method) (contractFunctionReturns, error) {
	numOut := typeMethod.Type.NumOut()

	methodName := typeMethod.Name

	if methodName == "" {
		methodName = "Function"
	}

	if numOut > 2 {
		return contractFunctionReturns{}, fmt.Errorf("Functions may only return a maximum of two values. %s returns %d", methodName, numOut)
	} else if numOut == 1 {
		outType := typeMethod.Type.Out(0)

		typeError := typeIsValid(outType, nil, true)

		if typeError != nil {
			return contractFunctionReturns{}, fmt.Errorf("%s contains invalid single return type. %s", methodName, typeError.Error())
		} else if outType == types.ErrorType {
			return contractFunctionReturns{nil, true}, nil
		}
		return contractFunctionReturns{outType, false}, nil
	} else if numOut == 2 {
		firstOut := typeMethod.Type.Out(0)
		secondOut := typeMethod.Type.Out(1)

		firstTypeError := typeIsValid(firstOut, nil, true)
		if firstTypeError != nil && firstOut != types.ErrorType {
			return contractFunctionReturns{}, fmt.Errorf("%s contains invalid first return type. %s", methodName, firstTypeError.Error())
		} else if secondOut.String() != "error" {
			return contractFunctionReturns{}, fmt.Errorf("%s contains invalid second return type. Type %s is not valid. Expected error", methodName, secondOut.String())
		}
		return contractFunctionReturns{firstOut, true}, nil
	}
	return contractFunctionReturns{nil, false}, nil
}
