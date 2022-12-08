package chaincode

import (
	"encoding/json"
	"fmt"

	"github.com/gofiber/fiber/v2/internal/uuid"
	"github.com/google/uuid"
	"github.com/hyperledger/fabric-contract-api-go/contractapi"
	"github.com/xeipuuv/gojsonschema"

	"crypto/sha256"
	"encoding/hex"
	"log"
)

// SmartContract provides functions for managing an Asset
type SmartContract struct {
	contractapi.Contract
}

// Asset describes basic details of what makes up a simple asset

// Asset describes basic details of what makes up a simple asset
// Insert struct field in alphabetic order => to achieve determinism across languages
// golang keeps the order when marshal to json but doesn't order automatically

var lastSchemaHash string
var APIUserIds []string

type Data struct {
	Contributor     string `json:"Contributor"`
	ContributorId   string `json:"ContributorId"`
	ContentHash     string `json:"ContentHash"`
	Id              string `json:"Id"`
	Owner           string `json:"Owners"`
	JsonFileContent map[string]interface{}
}

type Schema struct {
	Version           int    `json:"Version"`
	Hash              string `json:"Hash"`
	JsonSchemaContent map[string]interface{}
}

type User struct {
	UUID      string   `json:UUID`
	APIUserId []string `json:"APIUserId"`
}

type Group struct {
	GroupName string   `json:GroupName`
	UUIDs     []string `json:UUIDs`
	Project   string   `json:Project`
	Org       string   `json:Org`
	Hash      string   `json:Hash`
}

// InitLedger adds a base set of Data entries to the ledger
func (s *SmartContract) InitLedger(ctx contractapi.TransactionContextInterface, InitSchema string, InitData string) error {

	// We use the function jsonReader in order to read the content of the shcema Json File. The schema Json file is composed by us and inserted as a parameter in the invokation of the initialization function.
	schemaJsonFileContent, error_schema := s.JsonReader(ctx, InitSchema)
	firstJsonFileContent, error_file := s.JsonReader(ctx, InitData)

	if error_schema != nil {
		return fmt.Errorf("failed to read shcema.json file: %v", error_schema)
	} else if error_file != nil {
		return fmt.Errorf("failed to read 1st json files: %v", error_file)
	} else {

		firstJsonFileHash, initDataHashError := s.Hash(ctx, InitData)
		schemaJsonFileHash, schemaHashError := s.Hash(ctx, InitSchema)
		lastSchemaHash = schemaJsonFileHash
		if initDataHashError != nil {
			return fmt.Errorf("failed to calculate 1st json file hash: %v", initDataHashError)
		} else if schemaHashError != nil {
			return fmt.Errorf("failed to calculate schema hash: %v", schemaHashError)
		} else {
			data := Data{
				Contributor:     "pepitoperes@email.com",
				ContributorId:   "ABC123",
				ContentHash:     firstJsonFileHash,
				Id:              "00000",
				Owner:           "CIA",
				JsonFileContent: firstJsonFileContent,
			}

			assetJSON, err := json.Marshal(data)
			if err != nil {
				return err
			}

			err = ctx.GetStub().PutState(data.ContentHash, assetJSON)
			if err != nil {
				return fmt.Errorf("failed to put to world state. %v", err)
			} else {
				fmt.Print("A new Data Struct has been created with the hash %v", firstJsonFileHash)
			}

			//This is the definition of the Schema that we should use for validate all the JSON files from now on.

			initSchema := Schema{
				Version:           1,
				Hash:              schemaJsonFileHash,
				JsonSchemaContent: schemaJsonFileContent,
			}

			assetJSON, err = json.Marshal(initSchema)
			if err != nil {
				return err
			}

			err = ctx.GetStub().PutState(initSchema.Hash, assetJSON)
			if err != nil {
				return fmt.Errorf("failed to put to world state. %v", err)
			} else {
				fmt.Print("A new Schema has been created with the hash %v", schemaJsonFileHash)
			}
		}
	}
	return nil
}

func (s *SmartContract) LastSchemaHash(ctx contractapi.TransactionContextInterface) string {
	return lastSchemaHash
}

func (s *SmartContract) Hash(ctx contractapi.TransactionContextInterface, doc string) (string, error) {

	var v interface{}
	err := json.Unmarshal([]byte(doc), &v)
	if err != nil {
		return "HASH CRASH", fmt.Errorf("Unable to unmarshal Json String passed as parameter. No hash calculation can be completed: %v", err)
	} else {
		cdoc, err := json.Marshal(v)
		if err != nil {
			return "HASH CRASH", fmt.Errorf("Unable to re-marshal interface into json format. No hash calculation can be completed: %v", err)
		} else {
			sum := sha256.Sum256(cdoc)
			return hex.EncodeToString(sum[0:]), nil
		}
	}
}

func (s *SmartContract) JsonReader(ctx contractapi.TransactionContextInterface, content string) (map[string]interface{}, error) {

	var payload map[string]interface{}
	// Now let's unmarshall the data into `payload`
	err := json.Unmarshal([]byte(content), &payload)
	if err != nil {
		log.Fatal("Error during Unmarshal() of string into type Interface: ", err)
	}
	return payload, nil

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

		var data map[string]interface{}
		err = json.Unmarshal(queryResponse.Value, &data)
		if err != nil {
			return nil, err
		} else if _, ok := data["Id"]; ok {
			var dataSruct Data
			err = json.Unmarshal(queryResponse.Value, &dataSruct)
			if err != nil {
				return nil, err
			} else {
				dataSamples = append(dataSamples, &dataSruct)
			}
		}
	}

	return dataSamples, nil
}

func (s *SmartContract) SchemaExists(ctx contractapi.TransactionContextInterface, Hash string) (bool, error) {
	assetJSON, err := ctx.GetStub().GetState(Hash)
	if err != nil {
		return false, fmt.Errorf("failed to read from world state. Schema doesn't exist: %v", err)
	} else {
		var schema map[string]interface{}
		err2 := json.Unmarshal(assetJSON, &schema)
		if err2 != nil {
			return false, fmt.Errorf("failed to read from world state: %v", err2)
		} else if err3, ok := schema["Hash"]; ok {
			return assetJSON != nil, nil
		} else {
			return false, fmt.Errorf("failed to read from world state. Hash passed as parameter may correspond to a Data struct rather than to a Schema: %v", err3)
		}
	}
}
func (s *SmartContract) CreateNewSchema(ctx contractapi.TransactionContextInterface, newSchemaContent string) error {

	jsonFileContent, err := s.JsonReader(ctx, newSchemaContent)
	if err != nil {
		return err
	} else {
		// Verify that an schema with exact same structure doesn't exist yet.
		hashContent, _ := s.Hash(ctx, newSchemaContent)
		exists, err := s.SchemaExists(ctx, hashContent)
		if exists {
			return fmt.Errorf("Schema already exists: %v", err)
		} else {
			//get previous schema's id
			assetJSON, err := ctx.GetStub().GetState(lastSchemaHash)
			if err != nil {
				return fmt.Errorf("failed to calculate new schema's version: %v", err)
			} else {
				var schema Schema
				err2 := json.Unmarshal(assetJSON, &schema)
				if err2 != nil {
					return fmt.Errorf("failed to read from world state. LastSchemaHash var may be corrupted: %v", err2)
				} else {
					version := schema.Version + 1
					lastSchemaHash = hashContent
					newSchema := Schema{
						Version:           version,
						Hash:              hashContent,
						JsonSchemaContent: jsonFileContent,
					}
					assetJSON, err := json.Marshal(newSchema)
					if err != nil {
						return err
					}

					err = ctx.GetStub().PutState(newSchema.Hash, assetJSON)
					if err != nil {
						return fmt.Errorf("failed to put to world state. %v", err)
					}
				}
			}

		}

		return nil
	}
}

// GetAllSchemas returns all schemas found in world state

func (s *SmartContract) GetAllSchemas(ctx contractapi.TransactionContextInterface) ([]*Schema, error) {
	// range query with empty string for startKey and endKey does an
	// open-ended query of all schemas in the chaincode namespace.
	resultsIterator, err := ctx.GetStub().GetStateByRange("", "")
	if err != nil {
		return nil, err
	}
	defer resultsIterator.Close()

	var schemaSamples []*Schema
	for resultsIterator.HasNext() {
		queryResponse, err := resultsIterator.Next()
		if err != nil {
			return nil, err
		}

		var schema map[string]interface{}
		err = json.Unmarshal(queryResponse.Value, &schema)
		if err != nil {
			return nil, err
		} else if _, ok := schema["Hash"]; ok {
			var schemaStruct Schema
			err = json.Unmarshal(queryResponse.Value, &schemaStruct)
			if err != nil {
				return nil, err
			} else {
				schemaSamples = append(schemaSamples, &schemaStruct)
			}
		}
	}

	return schemaSamples, nil
}

// AssetExists returns true when asset with given ID exists in world state
func (s *SmartContract) AssetExists(ctx contractapi.TransactionContextInterface, Hash string) (bool, error) {
	assetJSON, err := ctx.GetStub().GetState(Hash)
	if err != nil {
		return false, fmt.Errorf("failed to read from world state. Asset dosen't exist: %v", err)
	} else if assetJSON != nil {
		var data map[string]interface{}
		err2 := json.Unmarshal(assetJSON, &data)
		if err2 != nil {
			return false, fmt.Errorf("failed to read from world state: %v", err2)
		} else if err3, ok := data["Id"]; ok {
			return assetJSON != nil, nil
		} else {
			return false, fmt.Errorf("failed to read from world state. Hash passed as parameter may correspond to a Schema struct rather than to a Data Struct: %v", err3)
		}
	} else {
		return assetJSON != nil, nil
	}
}

// JSON Validation

func (s *SmartContract) ValidJson(ctx contractapi.TransactionContextInterface, JsonContent string) (bool, error) {

	//schemaLoader := gojsonschema.NewReferenceLoader("file:///Users/fernando/Projects/OSC-IS/fabric-samples/test-network/JsonSchemaValidationTests/Schema.json")
	//documentLoader := gojsonschema.NewReferenceLoader("file:////Users/fernando/Projects/OSC-IS/fabric-samples/test-network/JsonSchemaValidationTests/testFile.json")

	//schemaLoader := gojsonschema.NewReferenceLoader("file:///home/chaincode/Schema.json")
	//documentLoader := gojsonschema.NewReferenceLoader("file:////home/chaincode/testFile.json")

	// PATH Needs to be absolute path (From root '/'). Add something that takes care of that.

	//m := schemas[len(schemas) - 1].JsonFileContent

	CurrentSchemaHash := s.LastSchemaHash(ctx)
	schema, _ := s.ReadSchema(ctx, CurrentSchemaHash)

	schemaLoader := gojsonschema.NewGoLoader(schema.JsonSchemaContent)
	documentLoader := gojsonschema.NewStringLoader(JsonContent)

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
	Contributor string, ContributorId string, Id string, Owner string, JsonFileContent string) error {

	ContentHash, _ := s.Hash(ctx, JsonFileContent)
	exists, err := s.AssetExists(ctx, ContentHash)
	if err != nil {
		return err
	}
	if exists {
		return fmt.Errorf("the asset %s already exists", ContentHash)
	}

	valid, err := s.ValidJson(ctx, JsonFileContent)
	if err != nil {
		return err
	}
	if !valid {
		return fmt.Errorf("the json file provided is not valid")
	} else {
		jsonFileContent, err := s.JsonReader(ctx, JsonFileContent)
		if err != nil {
			return err
		} else {
			data := Data{
				Contributor:     Contributor,
				ContributorId:   ContributorId,
				ContentHash:     ContentHash,
				Id:              Id,
				Owner:           Owner,
				JsonFileContent: jsonFileContent,
			}

			assetJSON, err := json.Marshal(data)
			if err != nil {
				return err

			}
			return ctx.GetStub().PutState(ContentHash, assetJSON)
		}
	}

}

// UpdateAsset updates an existing asset in the world state with provided parameters.
/*func (s *SmartContract) UpdateAsset(ctx contractapi.TransactionContextInterface,
	Contributor string, ContributorId string, Id string, Owner string) error {
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
		Owner:         Owner,
	}

	assetJSON, err := json.Marshal(data)
	if err != nil {
		return err
	}

	return ctx.GetStub().PutState(Id, assetJSON)
}
*/

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

func (s *SmartContract) ReadUser(ctx contractapi.TransactionContextInterface, UUID string) (*User, error) {
	assetJSON, err := ctx.GetStub().GetState(UUID)
	if err != nil {
		return nil, fmt.Errorf("failed to read from world state: %v", err)
	}
	if assetJSON == nil {
		return nil, fmt.Errorf("the User %s does not exist", UUID)
	}

	var user User
	err = json.Unmarshal(assetJSON, &user)
	if err != nil {
		return nil, err
	}

	return &user, nil
}

func (s *SmartContract) ReadSchema(ctx contractapi.TransactionContextInterface, hash string) (*Schema, error) {
	assetJSON, err := ctx.GetStub().GetState(hash)
	if err != nil {
		return nil, fmt.Errorf("failed to read from world state: %v", err)
	}
	if assetJSON == nil {
		return nil, fmt.Errorf("the schema with hash %s does not exist", hash)
	}

	var schema Schema
	err = json.Unmarshal(assetJSON, &schema)
	if err != nil {
		return nil, err
	}

	return &schema, nil
}

// TransferAsset updates the owner field of asset with given id in world state, and returns the old owner.
func (s *SmartContract) TransferAsset(ctx contractapi.TransactionContextInterface, Id string, newOwner string) (string, error) {
	data, err := s.ReadAsset(ctx, Id)
	if err != nil {
		return "Read Asset function failed excecution", err
	}

	data.Owner = newOwner

	assetJSON, err := json.Marshal(data)
	if err != nil {
		return "Marshal of Data not one", err
	}

	err = ctx.GetStub().PutState(Id, assetJSON)
	if err != nil {
		return "Unable to update asset", err
	}

	return data.Owner, nil
}

func (s *SmartContract) contains(ctx contractapi.TransactionContextInterface, st []string, str string) bool {
	for _, v := range st {
		if v == str {
			return true
		}
	}
	return false
}

func (s *SmartContract) UserExists(ctx contractapi.TransactionContextInterface, APIUserId string) bool {
	return s.contains(s, APIUserIds, APIUserId)
}

func (s *SmartContract) CreateUserID(ctx contractapi.TransactionContextInterface, APIId string) error {
	userExists := s.UserExists(ctx, APIId) //Add a function to check whether a user already exists or not.
	if userExists {
		return fmt.Errorf("the user with APIId %s already exists", APIId)
	} else {
		UUID := uuid.NewRandom()
		user := User{
			UUID:      UUID,
			APIUserId: []string{"APIId"},
		}

		assetJSON, err := json.Marshal(user)
		if err != nil {
			return err
		}

		err = ctx.GetStub().PutState(user.UUID, assetJSON)
		if err != nil {
			return fmt.Errorf("failed to create new user. %v", err)
		} else {
			fmt.Print("A new User has been created with the UUID %v", UUID)
			return nil
		}

	}

}

func (s *SmartContract) AssociateUserWithUUID(ctx contractapi.TransactionContextInterface, UUID string, APIId string) (string, error) {
	user, err := s.ReadUser(ctx, UUID)
	if err != nil {
		return "Error", fmt.Errorf("read User function failed excecution: %v", err)
	}
	if s.contains(ctx, user.APIUserId, APIId) {
		return "APIId provided is already associated with user " + UUID, nil
	}
	user.APIUserId = append(user.APIUserId, APIId)

	assetJSON, err := json.Marshal(user)
	if err != nil {
		return "Marshal of Data not done", err
	}

	err = ctx.GetStub().PutState(UUID, assetJSON)
	if err != nil {
		return "Unable to update asset", err
	}

	return "APIId added to map for user " + UUID, nil
}

func (s *SmartContract) GetAllUsers(ctx contractapi.TransactionContextInterface) ([]*User, error) {
	// range query with empty string for startKey and endKey does an
	// open-ended query of all schemas in the chaincode namespace.
	resultsIterator, err := ctx.GetStub().GetStateByRange("", "")
	if err != nil {
		return nil, err
	}
	defer resultsIterator.Close()

	var UserSamples []*User
	for resultsIterator.HasNext() {
		queryResponse, err := resultsIterator.Next()
		if err != nil {
			return nil, err
		}

		var user map[string]interface{}
		err = json.Unmarshal(queryResponse.Value, &user)
		if err != nil {
			return nil, err
		} else if _, ok := user["UUID"]; ok {
			var userStruct User
			err = json.Unmarshal(queryResponse.Value, &userStruct)
			if err != nil {
				return nil, err
			} else {
				UserSamples = append(UserSamples, &userStruct)
			}
		}
	}

	return UserSamples, nil
}
func (s *SmartContract) GroupExists(ctx contractapi.TransactionContextInterface, Hash string) (bool, error) {
	assetJSON, err := ctx.GetStub().GetState(Hash)
	if err != nil {
		return false, fmt.Errorf("failed to read from world state: %v", err)
	}

	if assetJSON != nil {
		var data map[string]interface{}
		err2 := json.Unmarshal(assetJSON, &data)
		if err2 != nil {
			return false, fmt.Errorf("failed to read from world state: %v", err2)
		}
		if err3, ok := data["GroupName"]; ok {
			return assetJSON != nil, nil
		} else {
			return false, fmt.Errorf("failed to read from world state. Hash passed as parameter may correspond to a Schema struct or Data Struc rather than to a Group Struct: %v", err3)
		}
	} else {
		return assetJSON != nil, nil
	}
}

func (s *SmartContract) CreateGroup(ctx contractapi.TransactionContextInterface, GroupName string, Project string, Org string) error {
	//Create a function that checks that a group already exists. Maybe combining GroupName, Group Name and project, and turn that into a Hash? Use the Hash to determine if the group exists.
	doc := GroupName + Project + Org
	hash, err := s.Hash(s, doc)
	if err != nil {
		return fmt.Errorf("Unable to perform Hash calculation: %v", err)
	}

	groupExists, err := s.GroupExists(ctx, hash)
	if err != nil {
		return fmt.Errorf("Unable to check whether Group exists or not: %v", err)
	}

	if groupExists {
		return fmt.Errorf("the group with name %s already exists", GroupName)
	}

	group := Group{
		GroupName: GroupName,
		UUIDs:     []string{},
		Project:   Project,
		Org:       Org,
		Hash:      hash,
	}

	assetJSON, err := json.Marshal(group)
	if err != nil {
		return err
	}

	err = ctx.GetStub().PutState(group.Hash, assetJSON)
	if err != nil {
		return fmt.Errorf("failed to create new Group. %v", err)
	}

	fmt.Print("The Group %v has been created ", group.GroupName)

	return nil

	/**var v interface{}
	err := json.Unmarshal([]byte(doc), &v)
	if err != nil {
		return fmt.Errorf("HASH CRASH -- Unable to unmarshal Json String passed as parameter. No hash calculation can be completed: %v", err)
	}
	cdoc, err := json.Marshal(v)
	if err != nil {
		return fmt.Errorf("HASH CRASH -- Unable to re-marshal interface into json format. No hash calculation can be completed: %v", err)
	}
	sum := sha256.Sum256(cdoc)
	hash := hex.EncodeToString(sum[0:])
	groupExists, err := s.GroupExists(ctx, hash)

	if err != nil {
		return fmt.Errorf("Error trying to read from world state: %v", err)
	}
	if groupExists {
		return fmt.Errorf("the group with name %s already exists", GroupName)
	}
	**/
}

func (s *SmartContract) ReadGroup(ctx contractapi.TransactionContextInterface, Hash string) (*Group, error) {
	exists, err := s.GroupExists(ctx, Hash)
	if (err != nil) || (exists != true) {
		return nil, fmt.Errorf("Failed to read Group: % v", err)
	}
	assetJSON, err := ctx.GetStub().GetState(Hash)
	if err != nil {
		return nil, fmt.Errorf("failed to read from world state: %v", err)
	}
	if assetJSON == nil {
		return nil, fmt.Errorf("the Group with Hash %s does not exist", Hash)
	}

	var group Group
	err = json.Unmarshal(assetJSON, &group)
	if err != nil {
		return nil, err
	}

	return &group, nil
}

func (s *SmartContract) AddUserIDToGroup(ctx contractapi.TransactionContextInterface, UUID string, Hash string) ([]string, error) {
	group, err := s.ReadGroup(ctx, Hash)
	if err != nil {
		return nil, fmt.Errorf("Read Group function failed excecution: %v", err)
	}

	contained := s.contains(ctx, group.UUIDs, UUID)
	if contained {
		return nil, fmt.Errorf("User %v already contained in Group", UUID)
	}

	group.UUIDs = append(group.UUIDs, UUID)

	assetJSON, err := json.Marshal(group)
	if err != nil {
		return nil, fmt.Errorf("Marshal of Group Struct not done: %v", err)
	}

	err = ctx.GetStub().PutState(Hash, assetJSON)

	if err != nil {
		return nil, fmt.Errorf("Unable to update asset: %v", err)
	}

	return group.UUIDs, nil
}

func (s *SmartContract) LinearSearch(ctx contractapi.TransactionContextInterface, list []string, element string) int {
	for i, n := range list {
		if n == element {
			return i
		}
	}
	return -1
}

func (s *SmartContract) RemoveElement(ctx contractapi.TransactionContextInterface, list []string, element string) []string {
	index := s.LinearSearch(ctx, list, element)
	if index != -1 {
		return append(list[:index])
	} else {
		return list
	}
}

func (s *SmartContract) DelUserIDFromGroup(ctx contractapi.TransactionContextInterface, UUID string, Hash string) ([]string, error) {
	group, err := s.ReadGroup(ctx, Hash)
	if err != nil {
		return nil, fmt.Errorf("Read Group function failed excecution: %v", err)
	}

	contained := s.contains(ctx, group.UUIDs, UUID)
	if !contained {
		return nil, fmt.Errorf("User %v already removed from Group or unexisting", UUID)
	}

	uuids := group.UUIDs
	uuids = s.RemoveElement(s, uuids, UUID)
	group.UUIDs = uuids

	assetJSON, err := json.Marshal(group)
	if err != nil {
		return nil, fmt.Errorf("Marshal of Group Struct not done: %v", err)
	}

	err = ctx.GetStub().PutState(Hash, assetJSON)

	if err != nil {
		return nil, fmt.Errorf("Unable to update asset: %v", err)
	}

	return group.UUIDs, nil
}

func (s *SmartContract) GetAPIUserByUUID(ctx contractapi.TransactionContextInterface, UUID string) ([]string, error) {
	user, err := s.ReadUser(s, UUID)
	if err != nil {
		return nil, fmt.Errorf("failed to read User %v from Wrold State", UUID)
	}
	return user.APIUserId, nil

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
