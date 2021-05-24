/*
SPDX-License-Identifier: Apache-2.0
*/

package auction

import (
	"encoding/base64"
	"fmt"

	"github.com/golang/protobuf/proto"
	"github.com/hyperledger/fabric-chaincode-go/shim"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
	"github.com/hyperledger/fabric-protos-go/common"
	"github.com/hyperledger/fabric-protos-go/msp"
)

func (s *SmartContract) GetSubmittingClientIdentity(ctx contractapi.TransactionContextInterface) (string, error) {

	b64ID, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return "", fmt.Errorf("Failed to read clientID: %v", err)
	}
	decodeID, err := base64.StdEncoding.DecodeString(b64ID)
	if err != nil {
		return "", fmt.Errorf("failed to base64 decode clientID: %v", err)
	}
	return string(decodeID), nil
}

// getCollectionName is an internal helper function to get collection of submitting client identity.
func getCollectionName(ctx contractapi.TransactionContextInterface) (string, error) {

	// Get the MSP ID of submitting client identity
	clientMSPID, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return "", fmt.Errorf("failed to get verified MSPID: %v", err)
	}

	// Create the collection name
	orgCollection := "_implicit_org_" + clientMSPID

	return orgCollection, nil
}

// verifyClientOrgMatchesPeerOrg is an internal function used to verify that client org id matches peer org id.
func verifyClientOrgMatchesPeerOrg(ctx contractapi.TransactionContextInterface) error {

	clientMSPID, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return fmt.Errorf("failed getting the client's MSPID: %v", err)
	}
	peerMSPID, err := shim.GetMSPID()
	if err != nil {
		return fmt.Errorf("failed getting the peer's MSPID: %v", err)
	}

	if clientMSPID != peerMSPID {
		return fmt.Errorf("client from org %v is not authorized to read or write private data from an org %v peer", clientMSPID, peerMSPID)
	}

	return nil
}

func contains(sli []string, str string) bool {
	for _, a := range sli {
		if a == str {
			return true
		}
	}
	return false
}

func setAssetStateBasedEndorsement(ctx contractapi.TransactionContextInterface, assetId string, mspids []string, auditor bool) error {

	principals := make([]*msp.MSPPrincipal, len(mspids))
	participantSigsPolicy := make([]*common.SignaturePolicy, len(mspids))

	for i, id := range mspids {
		principal, err := proto.Marshal(
			&msp.MSPRole{
				Role:          msp.MSPRole_PEER,
				MspIdentifier: id,
			},
		)
		if err != nil {
			return err
		}
		principals[i] = &msp.MSPPrincipal{
			PrincipalClassification: msp.MSPPrincipal_ROLE,
			Principal:               principal,
		}
		participantSigsPolicy[i] = &common.SignaturePolicy{
			Type: &common.SignaturePolicy_SignedBy{
				SignedBy: int32(i),
			},
		}
	}

	if auditor == false {
		// create the defalt policy for an auction without an auditor

		policy := &common.SignaturePolicyEnvelope{
			Version: 0,
			Rule: &common.SignaturePolicy{
				Type: &common.SignaturePolicy_NOutOf_{
					NOutOf: &common.SignaturePolicy_NOutOf{
						N:     int32(len(mspids)),
						Rules: participantSigsPolicy,
					},
				},
			},
			Identities: principals,
		}

		spBytes, err := proto.Marshal(policy)
		if err != nil {
			return err
		}
		err = ctx.GetStub().SetStateValidationParameter(assetId, spBytes)
		if err != nil {
			return fmt.Errorf("failed to set validation parameter on auction: %v", err)
		}
	} else {

		// create the defalt policy for an auction with an auditor

		// create the auditor identity and signature policy
		auditorMSP, err := proto.Marshal(
			&msp.MSPRole{
				Role:          msp.MSPRole_PEER,
				MspIdentifier: "Org3MSP",
			},
		)
		if err != nil {
			return err
		}
		principals = append(principals, &msp.MSPPrincipal{
			PrincipalClassification: msp.MSPPrincipal_ROLE,
			Principal:               auditorMSP,
		},
		)
		// Create the policies in case the auditor is needed. In this case, an
		// auditor and 1 participant can update the auction.
		auditorPolicies := make([]*common.SignaturePolicy, 2)
		auditorPolicies[0] = &common.SignaturePolicy{
			Type: &common.SignaturePolicy_SignedBy{
				SignedBy: int32(len(principals) - 1),
			},
		}
		auditorPolicies[1] = &common.SignaturePolicy{
			Type: &common.SignaturePolicy_NOutOf_{
				NOutOf: &common.SignaturePolicy_NOutOf{
					N:     1,
					Rules: participantSigsPolicy,
				},
			},
		}

		// The auditor policy below is equivilent to AND(auditor, OR(participants))
		policies := make([]*common.SignaturePolicy, 2)
		policies[0] = &common.SignaturePolicy{
			Type: &common.SignaturePolicy_NOutOf_{
				NOutOf: &common.SignaturePolicy_NOutOf{
					N:     2,
					Rules: auditorPolicies,
				},
			},
		}
		// Participants can also update the auction without an auditor
		policies[1] = &common.SignaturePolicy{
			Type: &common.SignaturePolicy_NOutOf_{
				NOutOf: &common.SignaturePolicy_NOutOf{
					N:     int32(len(mspids)),
					Rules: participantSigsPolicy,
				},
			},
		}
		// Either the auditor policy or the participant policy can update
		// the auction
		policy := &common.SignaturePolicyEnvelope{
			Version: 0,
			Rule: &common.SignaturePolicy{
				Type: &common.SignaturePolicy_NOutOf_{
					NOutOf: &common.SignaturePolicy_NOutOf{
						N:     1,
						Rules: policies,
					},
				},
			},
			Identities: principals,
		}
		spBytes, err := proto.Marshal(policy)
		if err != nil {
			return err
		}
		err = ctx.GetStub().SetStateValidationParameter(assetId, spBytes)
		if err != nil {
			return fmt.Errorf("failed to set validation parameter on auction: %v", err)
		}
	}
	return nil
}
