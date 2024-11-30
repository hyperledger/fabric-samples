package main_test

import (
	"encoding/json"
	"testing"

	ocd "offChainData"

	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset"
	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset/kvrwset"
	"github.com/hyperledger/fabric-protos-go-apiv2/peer"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/reflect/protoreflect"
)

func TestGetReadWriteSetsFromEndorserTransaction(t *testing.T) {
	nsReadWriteSetFake, expectedNamespace, expectedAsset := nsReadWriteSetFake()

	transaction := &peer.Transaction{
		Actions: []*peer.TransactionAction{
			{
				Payload: protoMarshalOrPanic(&peer.ChaincodeActionPayload{
					Action: &peer.ChaincodeEndorsedAction{
						ProposalResponsePayload: protoMarshalOrPanic(&peer.ProposalResponsePayload{
							Extension: protoMarshalOrPanic(&peer.ChaincodeAction{
								Results: protoMarshalOrPanic(&rwset.TxReadWriteSet{
									NsRwset: []*rwset.NsReadWriteSet{nsReadWriteSetFake},
								}),
							}),
						}),
					},
				}),
			},
		},
	}

	parsedEndorserTransaction := ocd.NewParsedEndorserTransaction(transaction)
	if len(parsedEndorserTransaction.GetReadWriteSets()) != 1 {
		t.Fatal("expected 1 ReadWriteSet, got", len(parsedEndorserTransaction.GetReadWriteSets()))
	}

	assertReadWriteSet(
		parsedEndorserTransaction.GetReadWriteSets()[0].GetNamespaceReadWriteSets()[0],
		expectedNamespace,
		expectedAsset,
		t,
	)
}

func assertReadWriteSet(parsedNsRwSet ocd.NamespaceReadWriteSet, expectedNamespace string, expectedAsset ocd.Asset, t *testing.T) {
	if parsedNsRwSet.GetNamespace() != expectedNamespace {
		t.Errorf("expected namespace %s, got %s", expectedNamespace, parsedNsRwSet.GetNamespace())
	}

	actualKVRWSet := parsedNsRwSet.GetReadWriteSet()
	if len(actualKVRWSet.Writes) != 1 {
		t.Fatal("expected 1 write, got", len(actualKVRWSet.Writes))
	}

	actualWrite := actualKVRWSet.Writes[0]
	if actualWrite.GetKey() != expectedAsset.ID {
		t.Errorf("expected key %s, got %s", expectedAsset.ID, actualWrite.GetKey())
	}

	if string(actualWrite.GetValue()) != string(jsonMarshalOrPanic(expectedAsset)) {
		t.Errorf("expected value %s, got %s", jsonMarshalOrPanic(expectedAsset), actualWrite.GetValue())
	}
}

func TestReadWriteSetWrapping(t *testing.T) {
	nsReadWriteSetFake, _, _ := nsReadWriteSetFake()

	txReadWriteSetFake := &rwset.TxReadWriteSet{
		NsRwset: []*rwset.NsReadWriteSet{nsReadWriteSetFake},
	}

	parsedRwSet := ocd.NewParsedReadWriteSet(txReadWriteSetFake)
	if len(parsedRwSet.GetNamespaceReadWriteSets()) != 1 {
		t.Fatalf("Expected 1 NamespaceReadWriteSet, got %d", len(parsedRwSet.GetNamespaceReadWriteSets()))
	}
}

func TestNamespaceReadWriteSetParsing(t *testing.T) {
	nsReadWriteSetFake, expectedNamespace, expectedAsset := nsReadWriteSetFake()

	parsedNsRwSet := ocd.NewParsedNamespaceReadWriteSet(nsReadWriteSetFake)
	assertReadWriteSet(
		parsedNsRwSet,
		expectedNamespace,
		expectedAsset,
		t,
	)
}

func nsReadWriteSetFake() (*rwset.NsReadWriteSet, string, ocd.Asset) {
	expectedNamespace := "basic"
	expectedAsset := ocd.NewAsset()

	result := &rwset.NsReadWriteSet{
		Namespace: expectedNamespace,
		Rwset: protoMarshalOrPanic(&kvrwset.KVRWSet{
			Writes: []*kvrwset.KVWrite{{
				Key:   expectedAsset.ID,
				Value: []byte(jsonMarshalOrPanic(expectedAsset)),
			}},
		}),
	}

	return result, expectedNamespace, expectedAsset
}

func protoMarshalOrPanic(v protoreflect.ProtoMessage) []byte {
	result, err := proto.Marshal(v)
	if err != nil {
		panic(err)
	}
	return result
}

func jsonMarshalOrPanic(v any) []byte {
	result, err := json.Marshal(v)
	if err != nil {
		panic(err)
	}
	return result
}
