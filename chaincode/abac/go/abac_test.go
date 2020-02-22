/*
Copyright Hitachi America Ltd. All Rights Reserved.

SPDX-License-Identifier: Apache-2.0
*/

package main

import (
	"fmt"
	"testing"

	"github.com/gogo/protobuf/proto"
	"github.com/hyperledger/fabric-chaincode-go/shim"
	"github.com/hyperledger/fabric-chaincode-go/shimtest"
	"github.com/hyperledger/fabric-protos-go/msp"
)

// Cert with attribute. "abac.init":"true"
const certWithAttrs = `-----BEGIN CERTIFICATE-----
MIIC2TCCAn+gAwIBAgIUQ0IZAeWJyRqPFpcFshvpVbY1RzMwCgYIKoZIzj0EAwIw
ZjELMAkGA1UEBhMCVVMxFzAVBgNVBAgTDk5vcnRoIENhcm9saW5hMRQwEgYDVQQK
EwtIeXBlcmxlZGdlcjEPMA0GA1UECxMGY2xpZW50MRcwFQYDVQQDEw5yY2Etb3Jn
MS1hZG1pbjAeFw0xODExMTMxNzQ4MDBaFw0xOTExMTMxNzUzMDBaMG8xCzAJBgNV
BAYTAlVTMRcwFQYDVQQIEw5Ob3J0aCBDYXJvbGluYTEUMBIGA1UEChMLSHlwZXJs
ZWRnZXIxHDANBgNVBAsTBmNsaWVudDALBgNVBAsTBG9yZzExEzARBgNVBAMTCmFk
bWluLW9yZzEwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAR196Xv7te+C5gkz7Ui
h8t2gl8QjjSs6iOLFTk18IEH5vLh+DovGT9q3ylvZpExtOap5zFkCva9GnChxP05
4A0eo4IBADCB/TAOBgNVHQ8BAf8EBAMCB4AwDAYDVR0TAQH/BAIwADAdBgNVHQ4E
FgQUXf9wjawRl/KosmHcVnYB4ay8IqswHwYDVR0jBBgwFoAUwqQ3h+jBjt2e2wC1
f1amDdCHY7QwFwYDVR0RBBAwDoIMZjExN2MxODEyYzM3MIGDBggqAwQFBgcIAQR3
eyJhdHRycyI6eyJhYmFjLmluaXQiOiJ0cnVlIiwiYWRtaW4iOiJ0cnVlIiwiaGYu
QWZmaWxpYXRpb24iOiJvcmcxIiwiaGYuRW5yb2xsbWVudElEIjoiYWRtaW4tb3Jn
MSIsImhmLlR5cGUiOiJjbGllbnQifX0wCgYIKoZIzj0EAwIDSAAwRQIhAN1v/XK0
WmZf5u9X9FG5uGxwcJ9d5K/eFAC7KahSbs65AiB/GzS2u1cYznXzTDWoBm9oflxY
w8Ou1Sh9IjeXj/SDAA==
-----END CERTIFICATE-----
`

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

func setCreator(t *testing.T, stub *shimtest.MockStub, mspID string, idbytes []byte) {
	sid := &msp.SerializedIdentity{Mspid: mspID, IdBytes: idbytes}
	b, err := proto.Marshal(sid)
	if err != nil {
		t.FailNow()
	}
	stub.Creator = b
}

func TestAbac_Init(t *testing.T) {
	scc := new(SimpleChaincode)
	stub := shimtest.NewMockStub("abac", scc)

	setCreator(t, stub, "org1MSP", []byte(certWithAttrs))

	// Init A=123 B=234
	checkInit(t, stub, [][]byte{[]byte("init"), []byte("A"), []byte("123"), []byte("B"), []byte("234")})

	checkState(t, stub, "A", "123")
	checkState(t, stub, "B", "234")
}

func TestAbac_Query(t *testing.T) {
	scc := new(SimpleChaincode)
	stub := shimtest.NewMockStub("abac", scc)

	setCreator(t, stub, "org1MSP", []byte(certWithAttrs))

	// Init A=345 B=456
	checkInit(t, stub, [][]byte{[]byte("init"), []byte("A"), []byte("345"), []byte("B"), []byte("456")})

	// Query A
	checkQuery(t, stub, "A", "345")

	// Query B
	checkQuery(t, stub, "B", "456")
}

func TestAbac_Invoke(t *testing.T) {
	scc := new(SimpleChaincode)
	stub := shimtest.NewMockStub("abac", scc)

	setCreator(t, stub, "org1MSP", []byte(certWithAttrs))

	// Init A=567 B=678
	checkInit(t, stub, [][]byte{[]byte("init"), []byte("A"), []byte("567"), []byte("B"), []byte("678")})

	// Invoke A->B for 123
	checkInvoke(t, stub, [][]byte{[]byte("invoke"), []byte("A"), []byte("B"), []byte("123")})
	checkQuery(t, stub, "A", "444")
	checkQuery(t, stub, "B", "801")

	// Invoke B->A for 234
	checkInvoke(t, stub, [][]byte{[]byte("invoke"), []byte("B"), []byte("A"), []byte("234")})
	checkQuery(t, stub, "A", "678")
	checkQuery(t, stub, "B", "567")
	checkQuery(t, stub, "A", "678")
	checkQuery(t, stub, "B", "567")
}
