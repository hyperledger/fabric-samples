// Copyright the Hyperledger Fabric contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package contractapi

import (
	"encoding/json"
	"fmt"
	"reflect"
	"sort"
	"strings"
	"unicode"

	"github.com/hyperledger/fabric-chaincode-go/pkg/cid"
	"github.com/hyperledger/fabric-chaincode-go/shim"
	"github.com/hyperledger/fabric-contract-api-go/internal"
	"github.com/hyperledger/fabric-contract-api-go/internal/utils"
	"github.com/hyperledger/fabric-contract-api-go/metadata"
	"github.com/hyperledger/fabric-contract-api-go/serializer"
	"github.com/hyperledger/fabric-protos-go/peer"
)

type contractChaincodeContract struct {
	info                      metadata.InfoMetadata
	functions                 map[string]*internal.ContractFunction
	unknownTransaction        *internal.TransactionHandler
	beforeTransaction         *internal.TransactionHandler
	afterTransaction          *internal.TransactionHandler
	transactionContextHandler reflect.Type
}

// ContractChaincode a struct to meet the chaincode interface and provide routing of calls to contracts
type ContractChaincode struct {
	DefaultContract       string
	contracts             map[string]contractChaincodeContract
	metadata              metadata.ContractChaincodeMetadata
	Info                  metadata.InfoMetadata
	TransactionSerializer serializer.TransactionSerializer
}

// SystemContractName the name of the system smart contract
const SystemContractName = "org.hyperledger.fabric"

// NewChaincode creates a new chaincode using contracts passed. The function parses each
// of the passed functions and stores details about their make-up to be used by the chaincode.
// Public functions of the contracts are stored and are made callable in the chaincode. The function
// will error if contracts are invalid e.g. public functions take in illegal types. A system contract is added
// to the chaincode which provides functionality for getting the metadata of the chaincode. The generated
// metadata is a JSON formatted MetadataContractChaincode containing each contract as a name and details
// of the public functions and types they take in/return. It also outlines version details for contracts and the
// chaincode. If these are blank strings this is set to latest. The names for parameters do not match those used
// in the functions, instead they are recorded as param0, param1, ..., paramN. If there exists a file
// contract-metadata/metadata.json then this will overwrite the generated metadata. The contents of this file must
// validate against the schema. The transaction serializer for the contract is set to be the JSONSerializer by
// default. This can be updated using by changing the TransactionSerializer property
func NewChaincode(contracts ...ContractInterface) (*ContractChaincode, error) {
	ciMethods := getCiMethods()

	cc := new(ContractChaincode)
	cc.contracts = make(map[string]contractChaincodeContract)

	for _, contract := range contracts {
		additionalExcludes := []string{}
		if castContract, ok := contract.(IgnoreContractInterface); ok {
			additionalExcludes = castContract.GetIgnoredFunctions()
		}

		err := cc.addContract(contract, append(ciMethods, additionalExcludes...))

		if err != nil {
			return nil, err
		}
	}

	sysC := new(SystemContract)
	sysC.Name = SystemContractName

	cc.addContract(sysC, ciMethods) // should never error as system contract is good

	err := cc.augmentMetadata()

	if err != nil {
		return nil, err
	}

	metadataJSON, _ := json.Marshal(cc.metadata)

	sysC.setMetadata(string(metadataJSON))

	cc.TransactionSerializer = new(serializer.JSONSerializer)

	return cc, nil
}

// Start starts the chaincode in the fabric shim
func (cc *ContractChaincode) Start() error {
	return shim.Start(cc)
}

// Init is called during Instantiate transaction after the chaincode container
// has been established for the first time, passes off details of the request to Invoke
// for handling the request if a function name is passed, otherwise returns shim.Success
func (cc *ContractChaincode) Init(stub shim.ChaincodeStubInterface) peer.Response {
	nsFcn, _ := stub.GetFunctionAndParameters()
	if nsFcn == "" {
		return shim.Success([]byte("Default initiator successful."))
	}

	return cc.Invoke(stub)
}

// Invoke is called to update or query the ledger in a proposal transaction. Takes the
// args passed in the transaction and uses the first argument to identify the contract
// and function of that contract to be called. The remaining args are then used as
// parameters to that function. Args are converted from strings to the expected parameter
// types of the function before being passed using the set transaction serializer for the ContractChaincode.
// A transaction context is generated and is passed, if required, as the first parameter to the named function.
// Before and after functions are called before and after the named function passed if the contract defines such
// functions to exist. If the before function returns an error the named function is not called and its error
// is returned in shim.Error. If the after function returns an error then its value is returned
// to shim.Error otherwise the value returned from the named function is returned as shim.Success (formatted by
// the transaction serializer). If an unknown name is passed as part of the first arg a shim.Error is returned.
// If a valid name is passed but the function name is unknown then the contract with that name's
// unknown function is called and its value returned as success or error depending on its return. If no
// unknown function is defined for the contract then shim.Error is returned by Invoke. In the case of
// unknown function names being passed (and the unknown handler returns an error) or the named function
// returning an error then the after function if defined is not called. If the named function or unknown
// function handler returns a non-error type then then the after transaction is sent this value. The same
// transaction context is passed as a pointer to before, after, named and unknown functions on each Invoke.
// If no contract name is passed then the default contract is used.
func (cc *ContractChaincode) Invoke(stub shim.ChaincodeStubInterface) peer.Response {

	nsFcn, params := stub.GetFunctionAndParameters()

	li := strings.LastIndex(nsFcn, ":")

	var ns string
	var fn string

	if li == -1 {
		ns = cc.DefaultContract
		fn = nsFcn
	} else {
		ns = nsFcn[:li]
		fn = nsFcn[li+1:]
	}

	if _, ok := cc.contracts[ns]; !ok {
		return shim.Error(fmt.Sprintf("Contract not found with name %s", ns))
	}

	if fn == "" {
		return shim.Error("Blank function name passed")
	}

	originalFn := fn

	fnRune := []rune(fn)

	if unicode.IsLower(fnRune[0]) {
		fnRune[0] = unicode.ToUpper(fnRune[0])
		fn = string(fnRune)
	}

	nsContract := cc.contracts[ns]

	ctx := reflect.New(nsContract.transactionContextHandler)
	ctxIface := ctx.Interface().(SettableTransactionContextInterface)
	ctxIface.SetStub(stub)

	ci, _ := cid.New(stub)
	ctxIface.SetClientIdentity(ci)

	beforeTransaction := nsContract.beforeTransaction

	if beforeTransaction != nil {
		_, _, errRes := beforeTransaction.Call(ctx, nil, nil)

		if errRes != nil {
			return shim.Error(errRes.Error())
		}
	}

	var successReturn string
	var successIFace interface{}
	var errorReturn error

	serializer := cc.TransactionSerializer

	if _, ok := nsContract.functions[fn]; !ok {
		unknownTransaction := nsContract.unknownTransaction
		if unknownTransaction == nil {
			return shim.Error(fmt.Sprintf("Function %s not found in contract %s", originalFn, ns))
		}

		successReturn, successIFace, errorReturn = unknownTransaction.Call(ctx, nil, serializer)
	} else {
		var transactionSchema *metadata.TransactionMetadata

		for _, v := range cc.metadata.Contracts[ns].Transactions {
			if v.Name == fn {
				transactionSchema = &v
				break
			}
		}

		successReturn, successIFace, errorReturn = nsContract.functions[fn].Call(ctx, transactionSchema, &cc.metadata.Components, serializer, params...)
	}

	if errorReturn != nil {
		return shim.Error(errorReturn.Error())
	}

	afterTransaction := nsContract.afterTransaction

	if afterTransaction != nil {
		_, _, errRes := afterTransaction.Call(ctx, successIFace, nil)

		if errRes != nil {
			return shim.Error(errRes.Error())
		}
	}

	return shim.Success([]byte(successReturn))
}

func (cc *ContractChaincode) addContract(contract ContractInterface, excludeFuncs []string) error {
	ns := contract.GetName()

	if ns == "" {
		ns = reflect.TypeOf(contract).Elem().Name()
	}

	if _, ok := cc.contracts[ns]; ok {
		return fmt.Errorf("Multiple contracts being merged into chaincode with name %s", ns)
	}

	ccn := contractChaincodeContract{}
	ccn.transactionContextHandler = reflect.ValueOf(contract.GetTransactionContextHandler()).Elem().Type()
	transactionContextPtrHandler := reflect.ValueOf(contract.GetTransactionContextHandler()).Type()
	ccn.functions = make(map[string]*internal.ContractFunction)
	ccn.info = contract.GetInfo()

	if ccn.info.Version == "" {
		ccn.info.Version = "latest"
	}

	if ccn.info.Title == "" {
		ccn.info.Title = ns
	}

	contractType := reflect.PtrTo(reflect.TypeOf(contract).Elem())
	contractValue := reflect.ValueOf(contract).Elem().Addr()

	ut := contract.GetUnknownTransaction()

	if ut != nil {
		var err error
		ccn.unknownTransaction, err = internal.NewTransactionHandler(ut, transactionContextPtrHandler, internal.TransactionHandlerTypeUnknown)

		if err != nil {
			return err
		}
	}

	bt := contract.GetBeforeTransaction()

	if bt != nil {
		var err error
		ccn.beforeTransaction, err = internal.NewTransactionHandler(bt, transactionContextPtrHandler, internal.TransactionHandlerTypeBefore)

		if err != nil {
			return err
		}
	}

	at := contract.GetAfterTransaction()

	if at != nil {
		var err error
		ccn.afterTransaction, err = internal.NewTransactionHandler(at, transactionContextPtrHandler, internal.TransactionHandlerTypeAfter)

		if err != nil {
			return err
		}
	}

	evaluateMethods := []string{}

	if eci, ok := contract.(EvaluationContractInterface); ok {
		evaluateMethods = eci.GetEvaluateTransactions()
	}

	for i := 0; i < contractType.NumMethod(); i++ {
		typeMethod := contractType.Method(i)
		valueMethod := contractValue.Method(i)

		if !utils.StringInSlice(typeMethod.Name, excludeFuncs) {
			var err error

			var callType internal.CallType = internal.CallTypeSubmit

			if utils.StringInSlice(typeMethod.Name, evaluateMethods) {
				callType = internal.CallTypeEvaluate
			}

			ccn.functions[typeMethod.Name], err = internal.NewContractFunctionFromReflect(typeMethod, valueMethod, callType, transactionContextPtrHandler)

			if err != nil {
				return err
			}
		}
	}

	if len(ccn.functions) == 0 {
		return fmt.Errorf("Contracts are required to have at least 1 (non-ignored) public method. Contract %s has none. Method names that have been ignored: %s", ns, utils.SliceAsCommaSentence(excludeFuncs))
	}

	cc.contracts[ns] = ccn

	if cc.DefaultContract == "" {
		cc.DefaultContract = ns
	}

	return nil
}

func (cc *ContractChaincode) reflectMetadata() metadata.ContractChaincodeMetadata {
	reflectedMetadata := metadata.ContractChaincodeMetadata{}
	reflectedMetadata.Contracts = make(map[string]metadata.ContractMetadata)
	reflectedMetadata.Components.Schemas = make(map[string]metadata.ObjectMetadata)
	reflectedMetadata.Info = &cc.Info

	if cc.Info.Version == "" {
		reflectedMetadata.Info.Version = "latest"
	}

	if cc.Info.Title == "" {
		reflectedMetadata.Info.Title = "undefined"
	}

	for key, contract := range cc.contracts {
		contractMetadata := metadata.ContractMetadata{}
		contractMetadata.Name = key
		infoCopy := contract.info
		contractMetadata.Info = &infoCopy

		if cc.DefaultContract == key {
			contractMetadata.Default = true
		}

		for key, fn := range contract.functions {
			fnMetadata := fn.ReflectMetadata(key, &reflectedMetadata.Components)

			contractMetadata.Transactions = append(contractMetadata.Transactions, fnMetadata)
		}

		sort.Slice(contractMetadata.Transactions, func(i, j int) bool {
			return contractMetadata.Transactions[i].Name < contractMetadata.Transactions[j].Name
		})

		reflectedMetadata.Contracts[key] = contractMetadata
	}

	return reflectedMetadata
}

func (cc *ContractChaincode) augmentMetadata() error {
	fileMetadata, err := metadata.ReadMetadataFile()

	if err != nil && !strings.Contains(err.Error(), "Failed to read metadata from file") {
		return err
	}

	reflectedMetadata := cc.reflectMetadata()

	fileMetadata.Append(reflectedMetadata)
	err = fileMetadata.CompileSchemas()

	if err != nil {
		return err
	}

	err = metadata.ValidateAgainstSchema(fileMetadata)

	if err != nil {
		return err
	}

	cc.metadata = fileMetadata

	return nil
}

func getCiMethods() []string {
	contractInterfaceType := reflect.TypeOf((*ContractInterface)(nil)).Elem()
	ignoreContractInterfaceType := reflect.TypeOf((*IgnoreContractInterface)(nil)).Elem()
	evaluateContractInterfaceType := reflect.TypeOf((*EvaluationContractInterface)(nil)).Elem()

	interfaceTypes := []reflect.Type{contractInterfaceType, ignoreContractInterfaceType, evaluateContractInterfaceType}

	var ciMethods []string
	for _, interfaceType := range interfaceTypes {
		for i := 0; i < interfaceType.NumMethod(); i++ {
			ciMethods = append(ciMethods, interfaceType.Method(i).Name)
		}
	}

	return ciMethods
}
