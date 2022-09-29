// Copyright the Hyperledger Fabric contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package serializer

import (
	"reflect"

	"github.com/hyperledger/fabric-contract-api-go/metadata"
)

// TransactionSerializer defines the functions a valid transaction serializer
// should have. Serializers to be used by a chaincode must implement this interface. Serializers
// are called on calls to chaincode with FromString used to format arguments going in to a call
// and ToString to format the success response from a call
type TransactionSerializer interface {
	// FromString receives the value in its original string form, the reflected type that the
	// new value should be of, the schema defining the rules that the converted value should
	// adhere to and components which the schema may point to as a reference. The schema and
	// component metadata may be nil. The function should produce a reflect value which matches
	// the goal type.
	FromString(string, reflect.Type, *metadata.ParameterMetadata, *metadata.ComponentMetadata) (reflect.Value, error)

	// ToString receives a reflected value of a value, the reflected type of that that value was
	// originally, the schema defining the rules of what that value should meet and components
	// which the schema may point to as a reference. The schema and component metadata may be nil
	// The function should produce a string which represents the original value
	ToString(reflect.Value, reflect.Type, *metadata.ReturnMetadata, *metadata.ComponentMetadata) (string, error)
}
