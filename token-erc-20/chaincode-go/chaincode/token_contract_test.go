package chaincode

import (
	"strings"
	"testing"

	"github.com/hyperledger/fabric-chaincode-go/v2/pkg/cid"
	"github.com/hyperledger/fabric-chaincode-go/v2/shim"
	"github.com/hyperledger/fabric-contract-api-go/v2/contractapi"
)

type fakeStub struct {
	shim.ChaincodeStubInterface
	state map[string][]byte
}

func (s *fakeStub) GetState(key string) ([]byte, error) {
	return s.state[key], nil
}

func (s *fakeStub) PutState(key string, value []byte) error {
	s.state[key] = value
	return nil
}

func (s *fakeStub) CreateCompositeKey(objectType string, attributes []string) (string, error) {
	return "\x00" + strings.Join(append([]string{objectType}, attributes...), "\x00") + "\x00", nil
}

func (s *fakeStub) SetEvent(name string, payload []byte) error {
	return nil
}

type fakeClientIdentity struct {
	cid.ClientIdentity
	id string
}

func (c *fakeClientIdentity) GetID() (string, error) {
	return c.id, nil
}

type fakeTransactionContext struct {
	contractapi.TransactionContextInterface
	stub     *fakeStub
	identity *fakeClientIdentity
}

func (ctx *fakeTransactionContext) GetStub() shim.ChaincodeStubInterface {
	return ctx.stub
}

func (ctx *fakeTransactionContext) GetClientIdentity() cid.ClientIdentity {
	return ctx.identity
}

func newTransactionContext(clientID string, state map[string][]byte) *fakeTransactionContext {
	state[nameKey] = []byte("token")
	return &fakeTransactionContext{
		stub:     &fakeStub{state: state},
		identity: &fakeClientIdentity{id: clientID},
	}
}

func TestTransferZeroAmount(t *testing.T) {
	ctx := newTransactionContext("sender", map[string][]byte{"sender": []byte("10")})

	err := (&SmartContract{}).Transfer(ctx, "recipient", 0)
	if err != nil {
		t.Fatalf("Transfer of 0 returned error: %v", err)
	}
	if got := string(ctx.stub.state["sender"]); got != "10" {
		t.Errorf("sender balance = %s, want 10", got)
	}
	if got := string(ctx.stub.state["recipient"]); got != "0" {
		t.Errorf("recipient balance = %s, want 0", got)
	}
}

func TestTransferFromZeroAmount(t *testing.T) {
	state := map[string][]byte{"owner": []byte("10")}
	ctx := newTransactionContext("spender", state)
	allowanceKey, err := ctx.stub.CreateCompositeKey(allowancePrefix, []string{"owner", "spender"})
	if err != nil {
		t.Fatal(err)
	}
	state[allowanceKey] = []byte("5")

	err = (&SmartContract{}).TransferFrom(ctx, "owner", "recipient", 0)
	if err != nil {
		t.Fatalf("TransferFrom of 0 returned error: %v", err)
	}
	if got := string(state[allowanceKey]); got != "5" {
		t.Errorf("allowance = %s, want 5", got)
	}
}
