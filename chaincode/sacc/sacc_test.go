/*
Copyright Hitachi America Ltd. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package main

import (
	"fmt"
	"testing"

	"github.com/hyperledger/fabric-chaincode-go/shim"
	"github.com/hyperledger/fabric-chaincode-go/shimtest"
)

func checkInit(t *testing.T, stub *shimtest.MockStub, args [][]byte) {
	res := stub.MockInit("1", args)
	if res.Status != shim.OK {
		fmt.Println("Init failed", string(res.Message))
		t.FailNow()
	}
}

func checkState(t *testing.T, stub *shimtest.MockStub, name string, value string) {
	bytes := stub.State[name]
	if bytes == nil {
		fmt.Println("State", name, "failed to get value")
		t.FailNow()
	}
	if string(bytes) != value {
		fmt.Println("State value", name, "was not", value, "as expected")
		t.FailNow()
	}
}

func checkQuery(t *testing.T, stub *shimtest.MockStub, name string, value string) {
	res := stub.MockInvoke("1", [][]byte{[]byte("query"), []byte(name)})
	if res.Status != shim.OK {
		fmt.Println("Query", name, "failed", string(res.Message))
		t.FailNow()
	}
	if res.Payload == nil {
		fmt.Println("Query", name, "failed to get value")
		t.FailNow()
	}
	if string(res.Payload) != value {
		fmt.Println("Query value", name, "was not", value, "as expected")
		t.FailNow()
	}
}

func checkInvoke(t *testing.T, stub *shimtest.MockStub, args [][]byte) {
	res := stub.MockInvoke("1", args)
	if res.Status != shim.OK {
		fmt.Println("Invoke", args, "failed", string(res.Message))
		t.FailNow()
	}
}

func TestSacc_Init(t *testing.T) {
	cc := new(SimpleAsset)
	stub := shimtest.NewMockStub("sacc", cc)

	// Init a=10
	checkInit(t, stub, [][]byte{[]byte("a"), []byte("10")})

	checkState(t, stub, "a", "10")
}

func TestSacc_Query(t *testing.T) {
	cc := new(SimpleAsset)
	stub := shimtest.NewMockStub("sacc", cc)

	// Init a=10
	checkInit(t, stub, [][]byte{[]byte("a"), []byte("10")})

	// Query a
	checkQuery(t, stub, "a", "10")
}

func TestSacc_Invoke(t *testing.T) {
	cc := new(SimpleAsset)
	stub := shimtest.NewMockStub("sacc", cc)

	// Init a=10
	checkInit(t, stub, [][]byte{[]byte("a"), []byte("10")})

	// Invoke: Set a=20
	checkInvoke(t, stub, [][]byte{[]byte("set"), []byte("a"), []byte("20")})

	// Query a
	checkQuery(t, stub, "a", "20")
}

func TestSacc_InitWithIncorrectArguments(t *testing.T) {
	cc := new(SimpleAsset)
	stub := shimtest.NewMockStub("sacc", cc)

	// Init with incorrect arguments
	res := stub.MockInit("1", [][]byte{[]byte("a"), []byte("10"), []byte("10")})

	if res.Status != shim.ERROR {
		fmt.Println("Invalid Init accepted")
		t.FailNow()
	}

	if res.Message != "Incorrect arguments. Expecting a key and a value" {
		fmt.Println("Unexpected Error message:", string(res.Message))
		t.FailNow()
	}
}

func TestSacc_QueryWithIncorrectArguments(t *testing.T) {
	cc := new(SimpleAsset)
	stub := shimtest.NewMockStub("sacc", cc)

	// Init a=10
	checkInit(t, stub, [][]byte{[]byte("a"), []byte("10")})

	// Query with incorrect arguments
	res := stub.MockInvoke("1", [][]byte{[]byte("query"), []byte("a"), []byte("b")})

	if res.Status != shim.ERROR {
		fmt.Println("Invalid query accepted")
		t.FailNow()
	}

	if res.Message != "Incorrect arguments. Expecting a key" {
		fmt.Println("Unexpected Error message:", string(res.Message))
		t.FailNow()
	}
}

func TestSacc_QueryForAssetNotFound(t *testing.T) {
	cc := new(SimpleAsset)
	stub := shimtest.NewMockStub("sacc", cc)

	// Init a=10
	checkInit(t, stub, [][]byte{[]byte("a"), []byte("10")})

	// Query for b (as a asset not found)
	res := stub.MockInvoke("1", [][]byte{[]byte("query"), []byte("b")})

	if res.Status != shim.ERROR {
		fmt.Println("Invalid query accepted")
		t.FailNow()
	}

	if res.Message != "Asset not found: b" {
		fmt.Println("Unexpected Error message:", string(res.Message))
		t.FailNow()
	}
}

func TestSacc_InvokeWithIncorrectArguments(t *testing.T) {
	cc := new(SimpleAsset)
	stub := shimtest.NewMockStub("sacc", cc)

	// Init a=10
	checkInit(t, stub, [][]byte{[]byte("a"), []byte("10")})

	// Invoke with incorrect arguments
	res := stub.MockInvoke("1", [][]byte{[]byte("set"), []byte("a")})
	if res.Status != shim.ERROR {
		fmt.Println("Invalid Invoke accepted")
		t.FailNow()
	}

	if res.Message != "Incorrect arguments. Expecting a key and a value" {
		fmt.Println("Unexpected Error message:", string(res.Message))
		t.FailNow()
	}
}
