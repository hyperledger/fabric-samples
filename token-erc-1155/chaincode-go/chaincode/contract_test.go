package chaincode

import (
	"reflect"
	"testing"
)

func TestSortedKeysToID(t *testing.T) {
	testMap := map[ToID]uint64{
		{To: "Alice", ID: 2}:   100,
		{To: "Bob", ID: 1}:     200,
		{To: "Charlie", ID: 2}: 300,
		{To: "Alice", ID: 1}:   400,
		{To: "Bob", ID: 2}:     500,
		{To: "Charlie", ID: 1}: 600,
	}

	expectedResult := []ToID{
		{To: "Alice", ID: 1},
		{To: "Bob", ID: 1},
		{To: "Charlie", ID: 1},
		{To: "Alice", ID: 2},
		{To: "Bob", ID: 2},
		{To: "Charlie", ID: 2},
	}

	result := sortedKeysToID(testMap)

	if !reflect.DeepEqual(result, expectedResult) {
		t.Fatalf("sortedKeysToID failed.\nExpected: %v\nGot:      %v", expectedResult, result)
	}
}
