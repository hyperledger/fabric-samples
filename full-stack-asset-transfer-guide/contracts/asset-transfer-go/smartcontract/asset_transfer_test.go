package smartcontract

import (
	"encoding/json"
	"testing"
)

func TestAssetJSONRoundTrip(t *testing.T) {
	asset := Asset{
		ID:             "asset1",
		Color:          "blue",
		Owner:          `{"org":"Org1MSP","user":"User1"}`,
		AppraisedValue: 100,
		Size:           10,
	}

	bytes, err := json.Marshal(asset)
	if err != nil {
		t.Fatalf("failed to marshal asset: %v", err)
	}

	var decoded Asset
	if err := json.Unmarshal(bytes, &decoded); err != nil {
		t.Fatalf("failed to unmarshal asset: %v", err)
	}

	if decoded.ID != asset.ID || decoded.Color != asset.Color || decoded.Owner != asset.Owner || decoded.AppraisedValue != asset.AppraisedValue || decoded.Size != asset.Size {
		t.Fatalf("decoded asset does not match original asset")
	}
}

func TestOwnerIdentifierSerialization(t *testing.T) {
	owner := ownerIdentifier("User1", "Org1MSP")
	serialized, err := marshalOwnerIdentifier(owner)
	if err != nil {
		t.Fatalf("failed to marshal owner identifier: %v", err)
	}

	decoded, err := ownerIdentifierFromString(serialized)
	if err != nil {
		t.Fatalf("failed to unmarshal owner identifier: %v", err)
	}

	if decoded.Org != owner.Org || decoded.User != owner.User {
		t.Fatalf("decoded owner identifier does not match original")
	}
}
