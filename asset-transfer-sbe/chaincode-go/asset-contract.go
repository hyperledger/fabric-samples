/*
 * SPDX-License-Identifier: Apache-2.0
 */

package main

import (
	"encoding/json"
	"fmt"
	"sort"

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

// SmartContract provides functions for managing an Asset
type SmartContract struct {
	contractapi.Contract
}

// CreateAsset issues a new asset to the world state with given details.
func (s *SmartContract) CreateAsset(ctx contractapi.TransactionContextInterface, id string, value string, owner string) error {
	exists, err := s.AssetExists(ctx, id)
	if err != nil {
		return err
	}
	if exists {
		return fmt.Errorf("the asset %s already exists", id)
	}
	ownerOrg, err := s.getClientOrgId(ctx)
	if err != nil {
		return err
	}

	asset := Asset{
		ID:       id,
		Value:    value,
		Owner:    owner,
		OwnerOrg: ownerOrg,
	}
	assetJSON, err := json.Marshal(asset)
	if err != nil {
		return err
	} // Set the endorsement policy of the assetId Key, such that current owner Org is required to endorse future updates
	setStateBasedEndorsement(ctx, id, ownerOrg)

	// Optionally, set the endorsement policy of the assetId Key, such that any 1 Org (N) out of the specified Orgs can endorse future updates
	// setStateBasedEndorsementNOutOf(ctx, assetId, 1, new String[]{"Org1MSP", "Org2MSP"});

	return ctx.GetStub().PutState(id, assetJSON)
}

// ReadAsset returns the asset stored in the world state with given id.
func (s *SmartContract) ReadAsset(ctx contractapi.TransactionContextInterface, id string) (*Asset, error) {
	assetJSON, err := ctx.GetStub().GetState(id)
	if err != nil {
		return nil, fmt.Errorf("failed to read from world state: %v", err)
	}
	if assetJSON == nil {
		return nil, fmt.Errorf("the asset %s does not exist", id)
	}

	var asset Asset
	err = json.Unmarshal(assetJSON, &asset)
	if err != nil {
		return nil, err
	}

	return &asset, nil
}

// UpdateAsset updates an existing asset in the world state with provided parameters.
// Needs an endorsement of current owner Org Peer.
func (s *SmartContract) UpdateAsset(ctx contractapi.TransactionContextInterface, id string, value string, owner string) error {
	exists, err := s.AssetExists(ctx, id)
	if err != nil {
		return err
	}
	if !exists {
		return fmt.Errorf("the asset %s does not exist", id)
	}
	ownerOrg, err := s.getClientOrgId(ctx)
	if err != nil {
		return err
	}
	// overwriting original asset with new asset
	asset := Asset{
		ID:       id,
		Value:    value,
		Owner:    owner,
		OwnerOrg: ownerOrg,
	}
	assetJSON, err := json.Marshal(asset)
	if err != nil {
		return err
	}

	return ctx.GetStub().PutState(id, assetJSON)
}

// DeleteAsset deletes an given asset from the world state.
//Needs an endorsement of current owner Org Peer.
func (s *SmartContract) DeleteAsset(ctx contractapi.TransactionContextInterface, id string) error {
	exists, err := s.AssetExists(ctx, id)
	if err != nil {
		return err
	}
	if !exists {
		return fmt.Errorf("the asset %s does not exist", id)
	}

	return ctx.GetStub().DelState(id)
}

// AssetExists returns true when asset with given ID exists in world state
func (s *SmartContract) AssetExists(ctx contractapi.TransactionContextInterface, id string) (bool, error) {
	assetJSON, err := ctx.GetStub().GetState(id)
	if err != nil {
		return false, fmt.Errorf("failed to read from world state: %v", err)
	}

	return assetJSON != nil, nil
}

// TransferAsset updates the owner field of asset with given id in world state.
// Needs an endorsement of current owner Org Peer.
// Re-sets the endorsement policy of the assetId Key, such that new owner Org Peer is required to endorse future updates.
func (s *SmartContract) TransferAsset(ctx contractapi.TransactionContextInterface, id string, newOwner string, newOwnerOrg string) error {
	asset, err := s.ReadAsset(ctx, id)
	if err != nil {
		return err
	}

	asset.Owner = newOwner
	asset.OwnerOrg = newOwnerOrg
	assetJSON, err := json.Marshal(asset)
	if err != nil {
		return err
	}
	// Re-Set the endorsement policy of the assetId Key, such that a new owner Org Peer is required to endorse future updates
	setStateBasedEndorsement(ctx, id, newOwnerOrg)

	// Optionally, set the endorsement policy of the assetId Key, such that any 1 Org (N) out of the specified Orgs can endorse future updates
	// setStateBasedEndorsementNOutOf(ctx, assetId, 1, new String[]{"Org1MSP", "Org2MSP"});

	return ctx.GetStub().PutState(id, assetJSON)
}

// Retrieves the client's OrgId (MSPID)
func (s *SmartContract) getClientOrgId(ctx contractapi.TransactionContextInterface) (string, error) {
	return ctx.GetClientIdentity().GetMSPID()
}

// Sets an endorsement policy to the assetId Key.
// Enforces that the owner Org must endorse future update transactions for the specified assetId Key.
func setStateBasedEndorsement(ctx contractapi.TransactionContextInterface, assetId string, ownerOrgs string) {
	var ep KeyEndorsementPolicy
	ep.AddOrgs("MEMBER", ownerOrgs)
	ctx.GetStub().SetStateValidationParameter(assetId, ep.getPolicy())
}

// Sets an endorsement policy to the assetId Key.
// Enforces that a given number of Orgs (N) out of the specified Orgs must endorse future update transactions for the specified assetId Key.
func setStateBasedEndorsementNOutOf(ctx contractapi.TransactionContextInterface, assetId string, nOrgs int, ownerOrgs []string) {
	ctx.GetStub().SetStateValidationParameter(assetId, policy(nOrgs, ownerOrgs))
}

// Create a policy that requires a given number (N) of Org principals signatures out of the provided list of Orgs
func policy(nOrgs int, mspids []string) []byte {
	sort.Strings(mspids)
	var principals []string
	var signPolicy []string
	for i := 0; i < len(mspids); i++ {
		mspid := mspids[i]
		var mspRole MSPRole
		mspRole := MSPRole{
			mspId,
			MSPRole.MSPRoleType.MEMBER}
		var MSPPrincipal principals
		principal := MSPPrincipal{
			fabprotos.common.MSPPrincipal.Classification.ROLE,
			fabprotos.common.MSPRole.encode(mspRole).finish()}
		principals = append(principals, MspPrincipal.MSPPrincipal.newBuilder().setPrincipalClassification(MspPrincipal.MSPPrincipal.Classification.ROLE).setPrincipal(MspPrincipal.MSPRole.newBuilder().setMspIdentifier(mspid).setRole(MspPrincipal.MSPRole.MSPRoleType.MEMBER).build().toByteString()).build())
		var signedBy SignaturePolicy_SignedBy
		signedBy := SignaturePolicy_SignedBy{i}
		signPolicy = append(signPolicy, signedBy)
	}
	// create the policy such that it requires any N signature's from all of the principals provided
	var allOf SignaturePolicy_NOutOf
	allOf := SignaturePolicy_NOutOf{nOrgs, signPolicies}
	var nOutof SignaturePolicy_NOutOf_
	noutof := SignaturePolicy_NOutOf_{allOf}
	var spe SignaturePolicyEnvelope
	spe := SignaturePolicyEnvelope{
		version:    0,
		rule:       noutof,
		identities: principals}
	return
}
