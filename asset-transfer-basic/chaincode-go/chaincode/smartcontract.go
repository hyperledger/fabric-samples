package chaincode

import (
	"encoding/json"
	"fmt"

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

// SmartContract provides functions for managing an Asset
type SmartContract struct {
	contractapi.Contract
}

// Asset describes basic details of what makes up a simple asset
// Insert struct field in alphabetic order => to achieve determinism across languages
// golang keeps the order when marshal to json but doesn't order automatically
type Data struct {
	docType          string `json:"docType"`
	id               string `json:"id"`
	title            string `json:"title"`
	description      string `json:"description"`
	Type             string `json:"Type"`
	DOI              string `json:"DOI"`
	url              string `json:"url"`
	manifest         string `json:"manifest"`
	footprint        string `json:"footprint"`
	keywords         string `json:"keywords"`
	otherDataIdName  string `json:"otherDataIdName"`
	otherDataIdValue string `json:"otherDataIdValue"`
	fundingAgencies  string `json:"fundingAgencies"`
	acknowledgment   string `json:"acknowledgment"`
	noteForChange    string `json:"noteForChange"`
	contributor      string `json:"contributor"`
	contributor_id   string `json:"contributor_id"`
}

// InitLedger adds a base set of Data entries to the ledger
func (s *SmartContract) InitLedger(ctx contractapi.TransactionContextInterface) error {
	dataEntries := []Data{
		{docType: "TestType", id: "00000", title: "TestSample", description: "description", Type: "TestType", DOI: "https://doi.org/10.57873/T34W2R", url: "sdsc.edu", manifest: "TestManifest", footprint: "", keywords: "SmartContrac, ChainCode, Peer", otherDataIdName: "None", otherDataIdValue: "None", fundingAgencies: "DOS", acknowledgment: "SDSC", noteForChange: "NONE", contributor: "AveryhardworkingUser@email.com", contributor_id: "ABC123"},
	}

	for _, data := range dataEntries {
		assetJSON, err := json.Marshal(data)
		if err != nil {
			return err
		}

		err = ctx.GetStub().PutState(data.id, assetJSON)
		if err != nil {
			return fmt.Errorf("failed to put to world state. %v", err)
		}
	}

	return nil
}

// GetAllAssets returns all assets found in world state
func (s *SmartContract) GetAllAssets(ctx contractapi.TransactionContextInterface) ([]*Data, error) {
	// range query with empty string for startKey and endKey does an
	// open-ended query of all assets in the chaincode namespace.
	resultsIterator, err := ctx.GetStub().GetStateByRange("", "")
	if err != nil {
		return nil, err
	}
	defer resultsIterator.Close()

	var dataSamples []*Data
	for resultsIterator.HasNext() {
		queryResponse, err := resultsIterator.Next()
		if err != nil {
			return nil, err
		}

		var data Data
		err = json.Unmarshal(queryResponse.Value, &data)
		if err != nil {
			return nil, err
		}
		dataSamples = append(dataSamples, &data)
	}

	return dataSamples, nil
}

// AssetExists returns true when asset with given ID exists in world state
func (s *SmartContract) AssetExists(ctx contractapi.TransactionContextInterface, id string) (bool, error) {
	assetJSON, err := ctx.GetStub().GetState(id)
	if err != nil {
		return false, fmt.Errorf("failed to read from world state: %v", err)
	}

	return assetJSON != nil, nil
}

// CreateDataSample issues a new Data Sample to the world state with given details.
func (s *SmartContract) CreateDataSample(ctx contractapi.TransactionContextInterface,
	docType string, id string, title string, description string, Type string, DOI string,
	url string, manifest string, footprint string, keywords string, otherDataIdName string,
	otherDataIdValue, string, fundingAgencies string, acknowledgment string, noteForChange string,
	contributor string, contributor_id string) error {

	exists, err := s.AssetExists(ctx, id)
	if err != nil {
		return err
	}
	if exists {
		return fmt.Errorf("the asset %s already exists", id)
	}

	data := Data{
		docType:          docType,
		id:               id,
		title:            title,
		description:      description,
		Type:             Type,
		DOI:              DOI,
		url:              url,
		manifest:         manifest,
		footprint:        footprint,
		keywords:         keywords,
		otherDataIdName:  otherDataIdName,
		otherDataIdValue: otherDataIdValue,
		fundingAgencies:  fundingAgencies,
		acknowledgment:   acknowledgment,
		noteForChange:    noteForChange,
		contributor:      contributor,
		contributor_id:   contributor_id,
	}
	assetJSON, err := json.Marshal(data)
	if err != nil {
		return err
	}

	return ctx.GetStub().PutState(id, assetJSON)
}

// UpdateAsset updates an existing asset in the world state with provided parameters.
func (s *SmartContract) UpdateAsset(ctx contractapi.TransactionContextInterface,
	docType string, id string, title string, description string, Type string, DOI string,
	url string, manifest string, footprint string, keywords string, otherDataIdName string,
	otherDataIdValue, string, fundingAgencies string, acknowledgment string, noteForChange string,
	contributor string, contributor_id string) error {
	exists, err := s.AssetExists(ctx, id)
	if err != nil {
		return err
	}
	if !exists {
		return fmt.Errorf("the asset %s does not exist", id)
	}

	// overwriting original asset with new asset
	data := Data{
		docType:          docType,
		id:               id,
		title:            title,
		description:      description,
		Type:             Type,
		DOI:              DOI,
		url:              url,
		manifest:         manifest,
		footprint:        footprint,
		keywords:         keywords,
		otherDataIdName:  otherDataIdName,
		otherDataIdValue: otherDataIdValue,
		fundingAgencies:  fundingAgencies,
		acknowledgment:   acknowledgment,
		noteForChange:    noteForChange,
		contributor:      contributor,
		contributor_id:   contributor_id,
	}
	assetJSON, err := json.Marshal(data)
	if err != nil {
		return err
	}

	return ctx.GetStub().PutState(id, assetJSON)
}

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

func (s *SmartContract) ReadAsset(ctx contractapi.TransactionContextInterface, id string) (*Data, error) {
	assetJSON, err := ctx.GetStub().GetState(id)
	if err != nil {
		return nil, fmt.Errorf("failed to read from world state: %v", err)
	}
	if assetJSON == nil {
		return nil, fmt.Errorf("the asset %s does not exist", id)
	}

	var data Data
	err = json.Unmarshal(assetJSON, &data)
	if err != nil {
		return nil, err
	}

	return &data, nil
}

// TransferAsset updates the owner field of asset with given id in world state, and returns the old owner.
func (s *SmartContract) TransferAsset(ctx contractapi.TransactionContextInterface, id string, newContributor string, newContributorId string) (string, error) {
	data, err := s.ReadAsset(ctx, id)
	if err != nil {
		return "", err
	}

	data.contributor = newContributor
	data.contributor_id = newContributorId

	assetJSON, err := json.Marshal(data)
	if err != nil {
		return "", err
	}

	err = ctx.GetStub().PutState(id, assetJSON)
	if err != nil {
		return "", err
	}

	return data.contributor, nil
}

/*


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



// TransferAsset updates the owner field of asset with given id in world state, and returns the old owner.
func (s *SmartContract) TransferAsset(ctx contractapi.TransactionContextInterface, id string, newOwner string) (string, error) {
	asset, err := s.ReadAsset(ctx, id)
	if err != nil {
		return "", err
	}

	oldOwner := asset.Owner
	asset.Owner = newOwner

	assetJSON, err := json.Marshal(asset)
	if err != nil {
		return "", err
	}

	err = ctx.GetStub().PutState(id, assetJSON)
	if err != nil {
		return "", err
	}

	return oldOwner, nil
}

// GetAllAssets returns all assets found in world state
func (s *SmartContract) GetAllAssets(ctx contractapi.TransactionContextInterface) ([]*Asset, error) {
	// range query with empty string for startKey and endKey does an
	// open-ended query of all assets in the chaincode namespace.
	resultsIterator, err := ctx.GetStub().GetStateByRange("", "")
	if err != nil {
		return nil, err
	}
	defer resultsIterator.Close()

	var assets []*Asset
	for resultsIterator.HasNext() {
		queryResponse, err := resultsIterator.Next()
		if err != nil {
			return nil, err
		}

		var asset Asset
		err = json.Unmarshal(queryResponse.Value, &asset)
		if err != nil {
			return nil, err
		}
		assets = append(assets, &asset)
	}

	return assets, nil
}
*/
