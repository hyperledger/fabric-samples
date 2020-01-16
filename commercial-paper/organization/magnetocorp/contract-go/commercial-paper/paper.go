/*
 * SPDX-License-Identifier: Apache-2.0
 */

package commercialpaper

import (
	"encoding/json"
	"fmt"

	ledgerapi "github.com/hyperledger/fabric-samples/commercial-paper/organization/magnetocorp/contract-go/ledger-api"
)

// State enum for commercial paper state property
type State uint

const (
	// ISSUED state for when a paper has been issued
	ISSUED State = iota + 1
	// TRADING state for when a paper is trading
	TRADING
	// REDEEMED state for when a paper has been redeemed
	REDEEMED
)

func (state State) String() string {
	names := []string{"ISSUED", "TRADING", "REDEEMED"}

	if state < ISSUED || state > REDEEMED {
		return "UNKNOWN"
	}

	return names[state-1]
}

// CreateCommercialPaperKey creates a key for commercial papers
func CreateCommercialPaperKey(issuer string, paperNumber string) string {
	return ledgerapi.MakeKey(issuer, paperNumber)
}

// Used for managing the fact status is private but want it in world state
type commercialPaperAlias CommercialPaper
type jsonCommercialPaper struct {
	*commercialPaperAlias
	State State  `json:"currentState"`
	Class string `json:"class"`
	Key   string `json:"key"`
}

// CommercialPaper defines a commercial paper
type CommercialPaper struct {
	PaperNumber      string `json:"paperNumber"`
	Issuer           string `json:"issuer"`
	IssueDateTime    string `json:"issueDateTime"`
	FaceValue        int    `json:"faceValue"`
	MaturityDateTime string `json:"maturityDateTime"`
	Owner            string `json:"owner"`
	state            State  `metadata:"currentState"`
	class            string `metadata:"class"`
	key              string `metadata:"key"`
}

// UnmarshalJSON special handler for managing JSON marshalling
func (cp *CommercialPaper) UnmarshalJSON(data []byte) error {
	jcp := jsonCommercialPaper{commercialPaperAlias: (*commercialPaperAlias)(cp)}

	err := json.Unmarshal(data, &jcp)

	if err != nil {
		return err
	}

	cp.state = jcp.State

	return nil
}

// MarshalJSON special handler for managing JSON marshalling
func (cp CommercialPaper) MarshalJSON() ([]byte, error) {
	jcp := jsonCommercialPaper{commercialPaperAlias: (*commercialPaperAlias)(&cp), State: cp.state, Class: "org.papernet.commercialpaper", Key: ledgerapi.MakeKey(cp.Issuer, cp.PaperNumber)}

	return json.Marshal(&jcp)
}

// GetState returns the state
func (cp *CommercialPaper) GetState() State {
	return cp.state
}

// SetIssued returns the state to issued
func (cp *CommercialPaper) SetIssued() {
	cp.state = ISSUED
}

// SetTrading sets the state to trading
func (cp *CommercialPaper) SetTrading() {
	cp.state = TRADING
}

// SetRedeemed sets the state to redeemed
func (cp *CommercialPaper) SetRedeemed() {
	cp.state = REDEEMED
}

// IsIssued returns true if state is issued
func (cp *CommercialPaper) IsIssued() bool {
	return cp.state == ISSUED
}

// IsTrading returns true if state is trading
func (cp *CommercialPaper) IsTrading() bool {
	return cp.state == TRADING
}

// IsRedeemed returns true if state is redeemed
func (cp *CommercialPaper) IsRedeemed() bool {
	return cp.state == REDEEMED
}

// GetSplitKey returns values which should be used to form key
func (cp *CommercialPaper) GetSplitKey() []string {
	return []string{cp.Issuer, cp.PaperNumber}
}

// Serialize formats the commercial paper as JSON bytes
func (cp *CommercialPaper) Serialize() ([]byte, error) {
	return json.Marshal(cp)
}

// Deserialize formats the commercial paper from JSON bytes
func Deserialize(bytes []byte, cp *CommercialPaper) error {
	err := json.Unmarshal(bytes, cp)

	if err != nil {
		return fmt.Errorf("Error deserializing commercial paper. %s", err.Error())
	}

	return nil
}
