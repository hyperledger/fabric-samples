/*
 * SPDX-License-Identifier: Apache-2.0
 */

package commercialpaper

import (
	"encoding/json"
	"fmt"

	ledgerapi "github.com/hyperledger/fabric-samples/commercial-paper/organization/magnetocorp/contract-go/ledger-api"
)

type commercialPaperState uint

const (
	issued commercialPaperState = iota
	trading
	redeemed
)

func (state commercialPaperState) String() string {
	names := [...]string{"ISSUED", "TRADING", "REDEEMED"}

	if int(state) >= len(names) {
		return "UNKNOWN"
	}

	return names[int(state)]
}

// CreateCommercialPaperKey creates a key for commercial papers
func CreateCommercialPaperKey(issuer string, paperNumber string) string {
	return ledgerapi.MakeKey(issuer, paperNumber)
}

// Used for managing the fact status is private but want it in world state
type commercialPaperAlias CommercialPaper
type jsonCommercialPaper struct {
	*commercialPaperAlias
	State string `json:"state"`
}

// CommercialPaper defines a commercial paper
type CommercialPaper struct {
	PaperNumber      string `json:"paperNumber"`
	Issuer           string `json:"issuer"`
	IssueDateTime    string `json:"issueDateTime"`
	FaceValue        int    `json:"faceValue"`
	MaturityDateTime string `json:"maturityDateTime"`
	Owner            string `json:"owner"`
	state            string `metadata:"state"`
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
	jcp := jsonCommercialPaper{commercialPaperAlias: (*commercialPaperAlias)(&cp), State: cp.state}

	return json.Marshal(&jcp)
}

// GetState returns the state
func (cp *CommercialPaper) GetState() string {
	return cp.state
}

// SetIssued returns the state to issued
func (cp *CommercialPaper) SetIssued() {
	cp.state = issued.String()
}

// SetTrading sets the state to trading
func (cp *CommercialPaper) SetTrading() {
	cp.state = trading.String()
}

// SetRedeemed sets the state to redeemed
func (cp *CommercialPaper) SetRedeemed() {
	cp.state = redeemed.String()
}

// IsIssued returns true if state is issued
func (cp *CommercialPaper) IsIssued() bool {
	return cp.state == issued.String()
}

// IsTrading returns true if state is trading
func (cp *CommercialPaper) IsTrading() bool {
	return cp.state == trading.String()
}

// IsRedeemed returns true if state is redeemed
func (cp *CommercialPaper) IsRedeemed() bool {
	return cp.state == redeemed.String()
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
