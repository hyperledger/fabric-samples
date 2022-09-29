// Copyright the Hyperledger Fabric contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package utils

import (
	"sort"
	"strconv"
	"strings"

	"github.com/xeipuuv/gojsonschema"
)

// ValidateErrorsToString converts errors from JSON schema output into readable string
func ValidateErrorsToString(resErrors []gojsonschema.ResultError) string {
	toReturn := ""

	sort.Slice(resErrors[:], func(i, j int) bool {
		return resErrors[i].String() < resErrors[j].String()
	})

	for i, v := range resErrors {
		toReturn += strconv.Itoa(i+1) + ". " + v.String() + "\n"
	}

	return strings.Trim(toReturn, "\n")
}

// StringInSlice returns whether string exists in string slice
func StringInSlice(a string, list []string) bool {
	for _, b := range list {
		if b == a {
			return true
		}
	}
	return false
}

// SliceAsCommaSentence returns string slice as comma separated sentence
func SliceAsCommaSentence(slice []string) string {
	return strings.Replace(strings.Join(slice, " and "), " and ", ", ", len(slice)-2)
}
