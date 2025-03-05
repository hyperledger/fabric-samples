package contract

import (
	"encoding/json"
	"strconv"

	"github.com/hyperledger/fabric-gateway/pkg/client"
)

type AssetTransferBasic struct {
	contract *client.Contract
}

func NewAssetTransferBasic(contract *client.Contract) *AssetTransferBasic {
	return &AssetTransferBasic{contract}
}

func (atb *AssetTransferBasic) CreateAsset(anAsset Asset) error {
	if _, err := atb.contract.Submit(
		"CreateAsset",
		client.WithArguments(
			anAsset.ID,
			anAsset.Color,
			strconv.FormatUint(anAsset.Size, 10),
			anAsset.Owner,
			strconv.FormatUint(anAsset.AppraisedValue, 10),
		)); err != nil {
		return err
	}
	return nil
}

func (atb *AssetTransferBasic) TransferAsset(id, newOwner string) (string, error) {
	result, err := atb.contract.Submit(
		"TransferAsset",
		client.WithArguments(
			id,
			newOwner,
		),
	)
	if err != nil {
		return "", err
	}

	return string(result), nil
}

func (atb *AssetTransferBasic) DeleteAsset(id string) error {
	if _, err := atb.contract.Submit(
		"DeleteAsset",
		client.WithArguments(
			id,
		),
	); err != nil {
		return err
	}
	return nil
}

func (atb *AssetTransferBasic) GetAllAssets() ([]Asset, error) {
	assetsRaw, err := atb.contract.Evaluate("GetAllAssets")
	if err != nil {
		return nil, err
	}

	if len(assetsRaw) == 0 {
		return nil, nil
	}

	assets := []Asset{}
	if err := json.Unmarshal(assetsRaw, &assets); err != nil {
		return nil, err
	}

	return assets, nil
}
