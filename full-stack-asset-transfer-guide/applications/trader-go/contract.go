package main

import (
	"encoding/json"
	"errors"
	"fmt"

	"github.com/hyperledger/fabric-gateway/pkg/client"
	"github.com/hyperledger/fabric-protos-go-apiv2/peer"
)

const retries = 2

type Asset struct {
	ID             string  `json:"ID"`
	Color          string  `json:"Color"`
	Size           int     `json:"Size"`
	Owner          string  `json:"Owner"`
	AppraisedValue float64 `json:"AppraisedValue"`
}

type contractCaller interface {
	Submit(name string, options ...client.ProposalOption) ([]byte, error)
	Evaluate(name string, options ...client.ProposalOption) ([]byte, error)
}

type AssetTransfer struct {
	contract contractCaller
}

func NewAssetTransfer(contract *client.Contract) *AssetTransfer {
	return &AssetTransfer{contract: contract}
}

func (a *AssetTransfer) CreateAsset(asset Asset) error {
	data, err := json.Marshal(asset)
	if err != nil {
		return fmt.Errorf("failed to marshal asset: %w", err)
	}
	_, err = a.contract.Submit("CreateAsset", client.WithArguments(string(data)))
	return err
}

func (a *AssetTransfer) GetAllAssets() ([]Asset, error) {
	result, err := a.contract.Evaluate("GetAllAssets")
	if err != nil {
		return nil, err
	}
	if len(result) == 0 {
		return []Asset{}, nil
	}

	var assets []Asset
	if err := json.Unmarshal(result, &assets); err != nil {
		return nil, fmt.Errorf("failed to unmarshal assets: %w", err)
	}
	return assets, nil
}

func (a *AssetTransfer) ReadAsset(id string) (*Asset, error) {
	result, err := a.contract.Evaluate("ReadAsset", client.WithArguments(id))
	if err != nil {
		return nil, err
	}

	var asset Asset
	if err := json.Unmarshal(result, &asset); err != nil {
		return nil, fmt.Errorf("failed to unmarshal asset: %w", err)
	}
	return &asset, nil
}

func (a *AssetTransfer) UpdateAsset(asset Asset) error {
	data, err := json.Marshal(asset)
	if err != nil {
		return fmt.Errorf("failed to marshal asset: %w", err)
	}
	return submitWithRetry(func() error {
		_, err := a.contract.Submit("UpdateAsset", client.WithArguments(string(data)))
		return err
	})
}

func (a *AssetTransfer) DeleteAsset(id string) error {
	return submitWithRetry(func() error {
		_, err := a.contract.Submit("DeleteAsset", client.WithArguments(id))
		return err
	})
}

func (a *AssetTransfer) AssetExists(id string) (bool, error) {
	result, err := a.contract.Evaluate("AssetExists", client.WithArguments(id))
	if err != nil {
		return false, err
	}
	return string(result) == "true", nil
}

func (a *AssetTransfer) TransferAsset(id, newOwner, newOwnerOrg string) error {
	fmt.Printf("transferring asset '%s' to %s, %s\n", id, newOwner, newOwnerOrg)
	_, err := a.contract.Submit("TransferAsset", client.WithArguments(id, newOwner, newOwnerOrg))
	return err
}

func submitWithRetry(submit func() error) error {
	var lastErr error
	for i := 0; i < retries; i++ {
		err := submit()
		if err == nil {
			return nil
		}
		lastErr = err

		var commitErr *client.CommitError
		if errors.As(err, &commitErr) && commitErr.Code == peer.TxValidationCode_MVCC_READ_CONFLICT {
			continue
		}
		break
	}
	return lastErr
}
