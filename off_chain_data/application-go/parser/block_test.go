package parser

import (
	"encoding/json"
	"testing"

	atb "offchaindata/contract"

	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset"
	"github.com/hyperledger/fabric-protos-go-apiv2/ledger/rwset/kvrwset"
	"github.com/hyperledger/fabric-protos-go-apiv2/peer"
	"google.golang.org/protobuf/proto"
)

func Test_GetReadWriteSetsFromEndorserTransaction(t *testing.T) {
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

	parsedEndorserTransaction := parseEndorserTransaction(transaction)
	readWriteSets, err := parsedEndorserTransaction.readWriteSets()
	if err != nil {
		t.Fatal("unexpected error:", err)
	}

	if len(readWriteSets) != 1 {
		t.Fatal("expected 1 ReadWriteSet, got", len(readWriteSets))
	}

	assertReadWriteSet(
		readWriteSets[0].namespaceReadWriteSets()[0],
		expectedNamespace,
		expectedAsset,
		t,
	)
}

func assertReadWriteSet(
	parsedNsRwSet *NamespaceReadWriteSet,
	expectedNamespace string,
	expectedAsset atb.Asset,
	t *testing.T,
) {
	if parsedNsRwSet.Namespace() != expectedNamespace {
		t.Errorf("expected namespace %s, got %s", expectedNamespace, parsedNsRwSet.Namespace())
	}

	actualKVRWSet, err := parsedNsRwSet.ReadWriteSet()
	if err != nil {
		t.Fatal("unexpected error:", err)
	}
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

func Test_ReadWriteSetWrapping(t *testing.T) {
	nsReadWriteSetFake, _, _ := nsReadWriteSetFake()

	txReadWriteSetFake := &rwset.TxReadWriteSet{
		NsRwset: []*rwset.NsReadWriteSet{nsReadWriteSetFake},
	}

	parsedRwSet := parseReadWriteSet(txReadWriteSetFake)
	if len(parsedRwSet.namespaceReadWriteSets()) != 1 {
		t.Fatal("expected 1 NamespaceReadWriteSet, got", len(parsedRwSet.namespaceReadWriteSets()))
	}
}

func Test_NamespaceReadWriteSetParsing(t *testing.T) {
	nsReadWriteSetFake, expectedNamespace, expectedAsset := nsReadWriteSetFake()

	parsedNsRwSet := parseNamespaceReadWriteSet(nsReadWriteSetFake)
	assertReadWriteSet(
		parsedNsRwSet,
		expectedNamespace,
		expectedAsset,
		t,
	)
}

func nsReadWriteSetFake() (*rwset.NsReadWriteSet, string, atb.Asset) {
	expectedNamespace := "basic"
	expectedAsset := atb.Asset{
		ID:             "id-1",
		Color:          "green",
		Size:           8,
		Owner:          "Alice",
		AppraisedValue: 346,
	}

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

func protoMarshalOrPanic(v proto.Message) []byte {
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
