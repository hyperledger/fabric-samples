// Copyright the Hyperledger Fabric contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package internal

import (
	"fmt"
	"reflect"
	"sort"

	"github.com/hyperledger/fabric-contract-api-go/internal/types"
	"github.com/hyperledger/fabric-contract-api-go/internal/utils"
)

func basicTypesAsSlice() []string {
	typesArr := []string{}

	for el := range types.BasicTypes {
		typesArr = append(typesArr, el.String())
	}
	sort.Strings(typesArr)

	return typesArr
}

func listBasicTypes() string {
	return utils.SliceAsCommaSentence(basicTypesAsSlice())
}

func arrayOfValidType(array reflect.Value, additionalTypes []reflect.Type) error {
	if array.Len() < 1 {
		return fmt.Errorf("Arrays must have length greater than 0")
	}

	return typeIsValid(array.Index(0).Type(), additionalTypes, false)
}

func structOfValidType(obj reflect.Type, additionalTypes []reflect.Type) error {
	if obj.Kind() == reflect.Ptr {
		obj = obj.Elem()
	}

	for i := 0; i < obj.NumField(); i++ {
		err := typeIsValid(obj.Field(i).Type, additionalTypes, false)

		if err != nil {
			return err
		}
	}

	return nil
}

func typeInSlice(a reflect.Type, list []reflect.Type) bool {
	for _, b := range list {
		if b == a {
			return true
		}
	}
	return false
}

func typeIsValid(t reflect.Type, additionalTypes []reflect.Type, allowError bool) error {
	if t.Kind() == reflect.Array {
		array := reflect.New(t).Elem()
		return arrayOfValidType(array, additionalTypes)
	} else if t.Kind() == reflect.Slice {
		slice := reflect.MakeSlice(t, 1, 1)
		return typeIsValid(slice.Index(0).Type(), additionalTypes, false)
	} else if t.Kind() == reflect.Map {
		if t.Key().Kind() != reflect.String {
			return fmt.Errorf("Map key type %s is not valid. Expected string", t.Key().String())
		}

		return typeIsValid(t.Elem(), additionalTypes, false)
	} else if (t.Kind() == reflect.Struct || (t.Kind() == reflect.Ptr && t.Elem().Kind() == reflect.Struct)) && !typeInSlice(t, additionalTypes) {
		additionalTypes = append(additionalTypes, t)

		if t.Kind() != reflect.Ptr {
			additionalTypes = append(additionalTypes, reflect.PtrTo(t))
		} else {
			additionalTypes = append(additionalTypes, t.Elem())
		}
		// add self for cyclic

		return structOfValidType(t, additionalTypes)
	} else if _, ok := types.BasicTypes[t.Kind()]; (!ok || (!allowError && t == types.ErrorType) || (t.Kind() == reflect.Interface && t.String() != "interface {}" && t.String() != "error")) && !typeInSlice(t, additionalTypes) {
		errStr := ""

		if allowError {
			errStr = " error,"
		}

		return fmt.Errorf("Type %s is not valid. Expected a struct or one of the basic types%s %s or an array/slice of these", t.String(), errStr, listBasicTypes())
	}

	return nil
}

func typeMatchesInterface(toMatch reflect.Type, iface reflect.Type) error {
	if iface.Kind() != reflect.Interface {
		return fmt.Errorf("Type passed for interface is not an interface")
	}

	for i := 0; i < iface.NumMethod(); i++ {
		ifaceMethod := iface.Method(i)
		matchMethod, exists := toMatch.MethodByName(ifaceMethod.Name)

		if !exists {
			return fmt.Errorf("Missing function %s", ifaceMethod.Name)
		}

		ifaceNumIn := ifaceMethod.Type.NumIn()
		matchNumIn := matchMethod.Type.NumIn() - 1 // skip over which the function is acting on

		if ifaceNumIn != matchNumIn {
			return fmt.Errorf("Parameter mismatch in method %s. Expected %d, got %d", ifaceMethod.Name, ifaceNumIn, matchNumIn)
		}

		for j := 0; j < ifaceNumIn; j++ {
			ifaceIn := ifaceMethod.Type.In(j)
			matchIn := matchMethod.Type.In(j + 1)

			if ifaceIn.Kind() != matchIn.Kind() {
				return fmt.Errorf("Parameter mismatch in method %s at parameter %d. Expected %s, got %s", ifaceMethod.Name, j, ifaceIn.Name(), matchIn.Name())
			}
		}

		ifaceNumOut := ifaceMethod.Type.NumOut()
		matchNumOut := matchMethod.Type.NumOut()
		if ifaceNumOut != matchNumOut {
			return fmt.Errorf("Return mismatch in method %s. Expected %d, got %d", ifaceMethod.Name, ifaceNumOut, matchNumOut)
		}

		for j := 0; j < ifaceNumOut; j++ {
			ifaceOut := ifaceMethod.Type.Out(j)
			matchOut := matchMethod.Type.Out(j)

			if ifaceOut.Kind() != matchOut.Kind() {
				return fmt.Errorf("Return mismatch in method %s at return %d. Expected %s, got %s", ifaceMethod.Name, j, ifaceOut.Name(), matchOut.Name())
			}
		}
	}

	return nil
}
