// Copyright the Hyperledger Fabric contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package metadata

import (
	"io/ioutil"
	"path"
	"runtime"
)

// For testing!
type ioutilInterface interface {
	ReadFile(string) ([]byte, error)
}

type ioutilFront struct{}

func (i ioutilFront) ReadFile(filename string) ([]byte, error) {
	return ioutil.ReadFile(filename)
}

var ioutilAbs ioutilInterface = ioutilFront{}

func readLocalFile(localPath string) ([]byte, error) {
	_, filename, _, _ := runtime.Caller(1)

	schemaPath := path.Join(path.Dir(filename), localPath)

	file, err := ioutilAbs.ReadFile(schemaPath)

	return file, err
}
