// Copyright the Hyperledger Fabric contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package utils

// UndefinedInterface the type of nil passed to an after transaction when
// the contract function called as part of the transaction does not specify
// a success return type or its return type is interface{} and value nil
type UndefinedInterface struct{}
