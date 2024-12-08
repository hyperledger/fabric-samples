package parser

import "github.com/hyperledger/fabric-protos-go-apiv2/msp"

// Implements identity.Identity Interface
type identityImpl struct {
	creator *msp.SerializedIdentity
}

func (i *identityImpl) MspID() string {
	return i.creator.GetMspid()
}

func (i *identityImpl) Credentials() []byte {
	return i.creator.GetIdBytes()
}
