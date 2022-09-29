// Copyright the Hyperledger Fabric contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package contractapi

// SystemContract contract added to all chaincode to provide access to metdata
type SystemContract struct {
	Contract
	metadata string
}

func (sc *SystemContract) setMetadata(metadata string) {
	sc.metadata = metadata
}

// GetMetadata returns JSON formatted metadata of chaincode
// the system contract is part of. This metadata is composed
// of reflected metadata combined with the metadata file
// if used
func (sc *SystemContract) GetMetadata() string {
	return sc.metadata
}

// GetEvaluateTransactions returns the transactions that
// exist in system contract which should be marked as
// evaluate transaction in the metadata. I.e. should be called
// by query transaction
func (sc *SystemContract) GetEvaluateTransactions() []string {
	return []string{"GetMetadata"}
}
