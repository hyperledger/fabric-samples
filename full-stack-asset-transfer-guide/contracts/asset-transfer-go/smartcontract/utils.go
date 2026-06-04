/*
SPDX-License-Identifier: Apache-2.0
*/

package smartcontract

import (
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"encoding/pem"
	"fmt"

	"github.com/hyperledger/fabric-chaincode-go/v2/pkg/statebased"
	"github.com/hyperledger/fabric-contract-api-go/v2/contractapi"
)

type OwnerIdentifier struct {
	Org  string `json:"org"`
	User string `json:"user"`
}

func hasWritePermission(ctx contractapi.TransactionContextInterface, asset *Asset) (bool, error) {
	clientID, err := clientIdentifier(ctx)
	if err != nil {
		return false, err
	}

	ownerID, err := ownerIdentifierFromString(asset.Owner)
	if err != nil {
		return false, fmt.Errorf("invalid asset owner field: %v", err)
	}

	return clientID.Org == ownerID.Org, nil
}

func clientIdentifier(ctx contractapi.TransactionContextInterface) (*OwnerIdentifier, error) {
	org, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return nil, fmt.Errorf("failed to get client MSP ID: %v", err)
	}

	user, err := clientCommonName(ctx)
	if err != nil {
		return nil, err
	}

	return &OwnerIdentifier{Org: org, User: user}, nil
}

func clientIdentifierWithUser(ctx contractapi.TransactionContextInterface, user string) (*OwnerIdentifier, error) {
	org, err := ctx.GetClientIdentity().GetMSPID()
	if err != nil {
		return nil, fmt.Errorf("failed to get client MSP ID: %v", err)
	}

	if user == "" {
		user, err = clientCommonName(ctx)
		if err != nil {
			return nil, err
		}
	}

	return &OwnerIdentifier{Org: org, User: user}, nil
}

func clientCommonName(ctx contractapi.TransactionContextInterface) (string, error) {
	encodedID, err := ctx.GetClientIdentity().GetID()
	if err != nil {
		return "", fmt.Errorf("failed to read client identity: %v", err)
	}

	idBytes, err := base64.StdEncoding.DecodeString(encodedID)
	if err != nil {
		return "", fmt.Errorf("failed to decode client identity: %v", err)
	}

	certBlock, _ := pem.Decode(idBytes)
	if certBlock == nil {
		certBlock = &pem.Block{Bytes: idBytes}
	}

	cert, err := x509.ParseCertificate(certBlock.Bytes)
	if err != nil {
		return "", fmt.Errorf("unable to parse client identity certificate: %v", err)
	}

	if cert.Subject.CommonName == "" {
		return "", fmt.Errorf("unable to identify client identity common name: %v", cert.Subject)
	}

	return cert.Subject.CommonName, nil
}

func ownerIdentifier(user string, org string) *OwnerIdentifier {
	return &OwnerIdentifier{Org: org, User: user}
}

func ownerIdentifierFromString(owner string) (*OwnerIdentifier, error) {
	var identifier OwnerIdentifier
	if err := json.Unmarshal([]byte(owner), &identifier); err != nil {
		return nil, err
	}

	if identifier.Org == "" || identifier.User == "" {
		return nil, fmt.Errorf("owner must include org and user")
	}

	return &identifier, nil
}

func marshalOwnerIdentifier(identifier *OwnerIdentifier) (string, error) {
	serialized, err := json.Marshal(identifier)
	if err != nil {
		return "", fmt.Errorf("failed to marshal owner identifier: %v", err)
	}

	return string(serialized), nil
}

func setStateBasedEndorsement(ctx contractapi.TransactionContextInterface, ledgerKey string, org string) error {
	policy, err := statebased.NewStateEP(nil)
	if err != nil {
		return fmt.Errorf("failed to create state endorsement policy: %v", err)
	}

	if err := policy.AddOrgs(statebased.RoleTypePeer, org); err != nil {
		return fmt.Errorf("failed to add org to endorsement policy: %v", err)
	}

	policyBytes, err := policy.Policy()
	if err != nil {
		return fmt.Errorf("failed to marshal endorsement policy: %v", err)
	}

	if err := ctx.GetStub().SetStateValidationParameter(ledgerKey, policyBytes); err != nil {
		return fmt.Errorf("failed to set state validation parameter: %v", err)
	}

	return nil
}
