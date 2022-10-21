package chaincode

import (
	"encoding/json"
	"fmt"

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
	"github.com/xeipuuv/gojsonschema"

	"io/ioutil"
	"log"
)

// SmartContract provides functions for managing an Asset
type SmartContract struct {
	contractapi.Contract
}

// Asset describes basic details of what makes up a simple asset
// Insert struct field in alphabetic order => to achieve determinism across languages
// golang keeps the order when marshal to json but doesn't order automatically

/*type Data struct {
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
}*/

// Asset describes basic details of what makes up a simple asset
// Insert struct field in alphabetic order => to achieve determinism across languages
// golang keeps the order when marshal to json but doesn't order automatically

type Data struct {
	Contributor     string   `json:"Contributor"`
	ContributorId   string   `json:"ContributorId"`
	ContentHash     string   `json:"ContentHash"`
	Id              string   `json:"Id"`
	Owners          []string `json:"Owners"`
	JsonFileContent map[string]interface{}
}

type Schema struct {
	Contributor     string   `json:"Contributor"`
	ContributorId   string   `json:"ContributorId"`
	ContentHash     string   `json:"ContentHash"`
	Id              string   `json:"Id"`
	Owners          []string `json:"Owners"`
	JsonFileContent map[string]interface{}
}

// InitLedger adds a base set of Data entries to the ledger
func (s *SmartContract) InitLedger(ctx contractapi.TransactionContextInterface, pathSchema string, pathFirstJsonFile string) (error) {

	// We use the function jsonReader in order to read the content of the shcema Json File. The schema Json file is composed by us and inserted into
	// the docker container of the commited chaincode (For now)
	schemaJsonFileContent := jsonReader(ctx, pathSchema)
	firstJsonFileContent := jsonReader(ctx, pathFirstJsonFile)

	dataEntries := []Data{
		{Contributor: "pepitoperes@email.com", ContributorId: "ABC123", ContentHash: "ZXCVBNM", Id: "00000", Owners: []string{"CIA", "DEA", "FBI"}, firstJsonFileContent},
	}

	schema := Schema{
		{Contributor: "pepitoperes@email.com", ContributorId: "ABC123", ContentHash: "ZXCVBNM", Id: "00000", Owners: []string{"CIA", "DEA", "FBI"}, schemaJsonFileContent},
	}

	for _, data := range schema {
		assetJSON, err := json.Marshal(data)
		if err != nil {
			return err
		}

		err = ctx.GetStub().PutState(data.Id, assetJSON)
		if err != nil {
			return fmt.Errorf("failed to put to world state. %v", err)
		}
	}

	for _, data := range dataEntries {
		assetJSON, err := json.Marshal(data)
		if err != nil {
			return err
		}

		err = ctx.GetStub().PutState(data.Id, assetJSON)
		if err != nil {
			return fmt.Errorf("failed to put to world state. %v", err)
		}
	}

	return schema, nil
}

func (s *SmartContract) JsonReader(ctx contractapi.TransactionContextInterface, path string) (map[string]interface{}, error) {

	content, err := ioutil.ReadFile(path)
	if err != nil {
		log.Fatal("Error when opening file: ", err)
	}

	// Now let's unmarshall the data into `payload`
	var payload map[string]interface{}
	err = json.Unmarshal(content, &payload)
	if err != nil {
		log.Fatal("Error during Unmarshal(): ", err)
	}
	return payload != nil, nil
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
func (s *SmartContract) AssetExists(ctx contractapi.TransactionContextInterface, Id string) (bool, error) {
	assetJSON, err := ctx.GetStub().GetState(Id)
	if err != nil {
		return false, fmt.Errorf("failed to read from world state: %v", err)
	}

	return assetJSON != nil, nil
}

// JSON Validation

func (s *SmartContract) ValidJson(ctx contractapi.TransactionContextInterface) (bool, error) {

	//schemaLoader := gojsonschema.NewReferenceLoader("file:///Users/fernando/Projects/OSC-IS/fabric-samples/test-network/JsonSchemaValidationTests/Schema.json")
	//documentLoader := gojsonschema.NewReferenceLoader("file:////Users/fernando/Projects/OSC-IS/fabric-samples/test-network/JsonSchemaValidationTests/testFile.json")

	//schemaLoader := gojsonschema.NewReferenceLoader("file:///home/chaincode/Schema.json")
	//documentLoader := gojsonschema.NewReferenceLoader("file:////home/chaincode/testFile.json")

	m := schema.JsonFileContent.JsonFileContent
	schemaLoader := gojsonschema.NewGoLoader(m)
	documentLoader := gojsonschema.NewReferenceLoader("file:////home/chaincode/testFile.json")

	result, err := gojsonschema.Validate(schemaLoader, documentLoader)

	if err != nil {
		panic(err.Error())
	}

	if result.Valid() {
		fmt.Printf("The document is valid\n")
	} else {
		fmt.Printf("The document is not valid. see errors :\n")
		for _, desc := range result.Errors() {
			fmt.Printf("- %s\n", desc)
		}
	}
	return result.Valid(), nil
}

// CreateDataSample issues a new Data Sample to the world state with given details.
func (s *SmartContract) CreateDataSample(ctx contractapi.TransactionContextInterface,
	Contributor string, ContributorId string, ContentHash string, Id string) error {

	exists, err := s.AssetExists(ctx, Id)
	if err != nil {
		return err
	}
	if exists {
		return fmt.Errorf("the asset %s already exists", Id)
	}

	valid, err := s.ValidJson(ctx)
	if err != nil {
		return err
	}
	if !valid {
		return fmt.Errorf("the json file provided is not valid")
	} else {
		data := Data{
			Contributor:   Contributor,
			ContributorId: ContributorId,
			ContentHash:   ContentHash,
			Id:            Id,
			Owners:        []string{"DOE", "DOS", "DOJ"}},
			JsonFileContent: 

		assetJSON, err := json.Marshal(data)
		if err != nil {
			return err
		}
		return ctx.GetStub().PutState(Id, assetJSON)
	}
}

// UpdateAsset updates an existing asset in the world state with provided parameters.
func (s *SmartContract) UpdateAsset(ctx contractapi.TransactionContextInterface,
	Contributor string, ContributorId string, ContentHash string, Id string) error {
	exists, err := s.AssetExists(ctx, Id)
	if err != nil {
		return err
	}
	if !exists {
		return fmt.Errorf("the asset %s does not exist", Id)
	}

	// overwriting original asset with new asset

	data := Data{
		Contributor:   Contributor,
		ContributorId: ContributorId,
		ContentHash:   ContentHash,
		Id:            Id,
		Owners:        []string{"DOE", "DOS", "DOJ"},
	}

	assetJSON, err := json.Marshal(data)
	if err != nil {
		return err
	}

	return ctx.GetStub().PutState(Id, assetJSON)
}

func (s *SmartContract) DeleteAsset(ctx contractapi.TransactionContextInterface, Id string) error {
	exists, err := s.AssetExists(ctx, Id)
	if err != nil {
		return err
	}
	if !exists {
		return fmt.Errorf("the asset %s does not exist", Id)
	}

	return ctx.GetStub().DelState(Id)
}

func (s *SmartContract) ReadAsset(ctx contractapi.TransactionContextInterface, Id string) (*Data, error) {
	assetJSON, err := ctx.GetStub().GetState(Id)
	if err != nil {
		return nil, fmt.Errorf("failed to read from world state: %v", err)
	}
	if assetJSON == nil {
		return nil, fmt.Errorf("the asset %s does not exist", Id)
	}

	var data Data
	err = json.Unmarshal(assetJSON, &data)
	if err != nil {
		return nil, err
	}

	return &data, nil
}

// TransferAsset updates the owner field of asset with given id in world state, and returns the old owner.
func (s *SmartContract) TransferAsset(ctx contractapi.TransactionContextInterface, Id string, newOwners []string) ([]string, error) {
	data, err := s.ReadAsset(ctx, Id)
	if err != nil {
		return []string{}, err
	}

	data.Owners = newOwners

	assetJSON, err := json.Marshal(data)
	if err != nil {
		return []string{}, err
	}

	err = ctx.GetStub().PutState(Id, assetJSON)
	if err != nil {
		return []string{}, err
	}

	return data.Owners, nil
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
