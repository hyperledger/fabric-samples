package main

import (
	"encoding/json"
	"fmt"

	"github.com/hyperledger/fabric-chaincode-go/v2/pkg/cid"
	"github.com/hyperledger/fabric-chaincode-go/v2/pkg/statebased"
	"github.com/hyperledger/fabric-contract-api-go/v2/contractapi"
)

type OwnerIdentifier struct {
	Org  string `json:"org"`
	User string `json:"user"`
}

// hasWritePermission returns true if the client's org matches the asset owner's org.
func hasWritePermission(
	ctx contractapi.TransactionContextInterface,
	asset *Asset,
) (bool, error) {

	clientID, err := clientIdentifier(ctx, "")
	if err != nil {
		return false, err
	}

	var ownerID OwnerIdentifier
	if err := json.Unmarshal([]byte(asset.Owner), &ownerID); err != nil {
		return false, err
	}

	return clientID.Org == ownerID.Org, nil
}

// clientIdentifier returns the client's organization and username.
// If user is empty, the client's certificate Common Name is used.
func clientIdentifier(
	ctx contractapi.TransactionContextInterface,
	user string,
) (*OwnerIdentifier, error) {

	mspID, err := cid.GetMSPID(ctx.GetStub())
	if err != nil {
		return nil, err
	}

	if user == "" {
		user, err = clientCommonName(ctx)
		if err != nil {
			return nil, err
		}
	}

	return &OwnerIdentifier{
		Org:  mspID,
		User: user,
	}, nil
}
func clientCommonName(
	ctx contractapi.TransactionContextInterface,
) (string, error) {

	c, err := cid.New(ctx.GetStub())
	if err != nil {
		return "", err
	}

	cert, err := c.GetX509Certificate()
	if err != nil {
		return "", err
	}

	if cert == nil {
		return "", fmt.Errorf("client certificate not found")
	}

	return cert.Subject.CommonName, nil
}

func ownerIdentifier(user, org string) *OwnerIdentifier {
	return &OwnerIdentifier{
		Org:  org,
		User: user,
	}
}

// setEndorsingOrgs sets state-based endorsement for a key.
func setEndorsingOrgs(
	ctx contractapi.TransactionContextInterface,
	ledgerKey string,
	orgs ...string,
) error {

	policy, err := newMemberPolicy(orgs...)
	if err != nil {
		return err
	}

	policyBytes, err := policy.Policy()
	if err != nil {
		return err
	}

	return ctx.GetStub().SetStateValidationParameter(
		ledgerKey,
		policyBytes,
	)
}

// newMemberPolicy creates an endorsement policy requiring MEMBER from the supplied orgs.
func newMemberPolicy(orgs ...string) (statebased.KeyEndorsementPolicy, error) {

	policy, err := statebased.NewStateEP(nil)
	if err != nil {
		return nil, err
	}

	if err := policy.AddOrgs(statebased.RoleTypeMember, orgs...); err != nil {
		return nil, err
	}

	return policy, nil
}
