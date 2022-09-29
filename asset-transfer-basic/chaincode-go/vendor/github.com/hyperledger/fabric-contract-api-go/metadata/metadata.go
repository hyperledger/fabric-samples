// Copyright the Hyperledger Fabric contributors. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package metadata

import (
	"encoding/json"
	"errors"
	"fmt"
	os "os"
	"path/filepath"
	"reflect"

	"github.com/go-openapi/spec"
	"github.com/gobuffalo/packr"
	"github.com/hyperledger/fabric-contract-api-go/internal/utils"
	"github.com/xeipuuv/gojsonschema"
)

// MetadataFolder name of folder metadata should be placed in
const MetadataFolder = "contract-metadata"

// MetadataFile name of file metadata should be written in
const MetadataFile = "metadata.json"

// Helpers for testing
type osInterface interface {
	Executable() (string, error)
	Stat(string) (os.FileInfo, error)
	IsNotExist(error) bool
}

type osFront struct{}

func (o osFront) Executable() (string, error) {
	return os.Executable()
}

func (o osFront) Stat(name string) (os.FileInfo, error) {
	return os.Stat(name)
}

func (o osFront) IsNotExist(err error) bool {
	return os.IsNotExist(err)
}

var osAbs osInterface = osFront{}

// GetJSONSchema returns the JSON schema used for metadata
func GetJSONSchema() ([]byte, error) {
	box := packr.NewBox("./schema")

	schema, err := box.Find("schema.json")

	return schema, err
}

// ParameterMetadata details about a parameter used for a transaction.
type ParameterMetadata struct {
	Description    string               `json:"description,omitempty"`
	Name           string               `json:"name"`
	Schema         *spec.Schema         `json:"schema"`
	CompiledSchema *gojsonschema.Schema `json:"-"`
}

// ReturnMetadata details about the return type for a transaction
type ReturnMetadata struct {
	Schema         *spec.Schema
	CompiledSchema *gojsonschema.Schema
}

// TransactionMetadata contains information on what makes up a transaction
// When JSON serialized the Returns object is flattened to contain the schema
type TransactionMetadata struct {
	Parameters []ParameterMetadata `json:"parameters,omitempty"`
	Returns    ReturnMetadata      `json:"-"`
	Tag        []string            `json:"tag,omitempty"`
	Name       string              `json:"name"`
}

type tmAlias TransactionMetadata
type jsonTransactionMetadata struct {
	*tmAlias
	ReturnsSchema *spec.Schema `json:"returns,omitempty"`
}

// UnmarshalJSON handles converting JSON to TransactionMetadata since returns is flattened
// in swagger
func (tm *TransactionMetadata) UnmarshalJSON(data []byte) error {
	jtm := jsonTransactionMetadata{tmAlias: (*tmAlias)(tm)}

	err := json.Unmarshal(data, &jtm)

	if err != nil {
		return err
	}

	tm.Returns = ReturnMetadata{}
	tm.Returns.Schema = jtm.ReturnsSchema

	return nil
}

// MarshalJSON handles converting TransactionMetadata to JSON since returns is flattened
// in swagger
func (tm *TransactionMetadata) MarshalJSON() ([]byte, error) {
	jtm := jsonTransactionMetadata{tmAlias: (*tmAlias)(tm), ReturnsSchema: tm.Returns.Schema}

	return json.Marshal(&jtm)
}

// ContactMetadata contains contact details about an author of a contract/chaincode
type ContactMetadata struct {
	Name  string `json:"name,omitempty"`
	URL   string `json:"url,omitempty"`
	Email string `json:"email,omitempty"`
}

// LicenseMetadata contains licensing information for contract/chaincode
type LicenseMetadata struct {
	Name string `json:"name,omitempty"`
	URL  string `json:"url,omitempty"`
}

// InfoMetadata contains additional information to clarify use of contract/chaincode
type InfoMetadata struct {
	Description string           `json:"description,omitempty"`
	Title       string           `json:"title,omitempty"`
	Contact     *ContactMetadata `json:"contact,omitempty"`
	License     *LicenseMetadata `json:"license,omitempty"`
	Version     string           `json:"version,omitempty"`
}

// ContractMetadata contains information about what makes up a contract
type ContractMetadata struct {
	Info         *InfoMetadata         `json:"info,omitempty"`
	Name         string                `json:"name"`
	Transactions []TransactionMetadata `json:"transactions"`
	Default      bool                  `json:"default"`
}

// ObjectMetadata description of a component
type ObjectMetadata struct {
	ID                   string                 `json:"$id"`
	Properties           map[string]spec.Schema `json:"properties"`
	Required             []string               `json:"required"`
	AdditionalProperties bool                   `json:"additionalProperties"`
}

// ComponentMetadata stores map of schemas of all components
type ComponentMetadata struct {
	Schemas map[string]ObjectMetadata `json:"schemas,omitempty"`
}

// ContractChaincodeMetadata describes a chaincode made using the contract api
type ContractChaincodeMetadata struct {
	Info       *InfoMetadata               `json:"info,omitempty"`
	Contracts  map[string]ContractMetadata `json:"contracts"`
	Components ComponentMetadata           `json:"components"`
}

// Append merge two sets of metadata. Source value will override the original
// values only in fields that are not yet set i.e. when info nil, contracts nil or
// zero length array, components empty.
func (ccm *ContractChaincodeMetadata) Append(source ContractChaincodeMetadata) {
	if ccm.Info == nil {
		ccm.Info = source.Info
	}

	if len(ccm.Contracts) == 0 {
		if ccm.Contracts == nil {
			ccm.Contracts = make(map[string]ContractMetadata)
		}

		for key, value := range source.Contracts {
			ccm.Contracts[key] = value
		}
	}

	if reflect.DeepEqual(ccm.Components, ComponentMetadata{}) {
		ccm.Components = source.Components
	}
}

// CompileSchemas compile parameter and return schemas for use by gojsonschema.
// When validating against the compiled schema you will need to make the
// comparison json have a key of the parameter name for parameters or
// return for return values e.g {"param1": "value"}. Compilation process
// resolves references to components
func (ccm *ContractChaincodeMetadata) CompileSchemas() error {
	compileSchema := func(propName string, schema *spec.Schema, components ComponentMetadata) (*gojsonschema.Schema, error) {
		combined := make(map[string]interface{})
		combined["components"] = components
		combined["properties"] = make(map[string]interface{})
		combined["properties"].(map[string]interface{})[propName] = schema

		combinedLoader := gojsonschema.NewGoLoader(combined)

		return gojsonschema.NewSchema(combinedLoader)
	}

	for contractName, contract := range ccm.Contracts {
		for txIdx, tx := range contract.Transactions {
			for paramIdx, param := range tx.Parameters {
				gjsSchema, err := compileSchema(param.Name, param.Schema, ccm.Components)

				if err != nil {
					return fmt.Errorf("Error compiling schema for %s [%s]. %s schema invalid. %s", contractName, tx.Name, param.Name, err.Error())
				}

				param.CompiledSchema = gjsSchema
				tx.Parameters[paramIdx] = param
			}

			if tx.Returns.Schema != nil {
				gjsSchema, err := compileSchema("return", tx.Returns.Schema, ccm.Components)

				if err != nil {
					return fmt.Errorf("Error compiling schema for %s [%s]. Return schema invalid. %s", contractName, tx.Name, err.Error())
				}

				tx.Returns.CompiledSchema = gjsSchema
			}

			contract.Transactions[txIdx] = tx
		}
		ccm.Contracts[contractName] = contract
	}

	return nil
}

// ReadMetadataFile return the contents of metadata file as ContractChaincodeMetadata
func ReadMetadataFile() (ContractChaincodeMetadata, error) {

	fileMetadata := ContractChaincodeMetadata{}

	ex, execErr := osAbs.Executable()
	if execErr != nil {
		return ContractChaincodeMetadata{}, fmt.Errorf("Failed to read metadata from file. Could not find location of executable. %s", execErr.Error())
	}
	exPath := filepath.Dir(ex)
	metadataPath := filepath.Join(exPath, MetadataFolder, MetadataFile)

	_, err := osAbs.Stat(metadataPath)

	if osAbs.IsNotExist(err) {
		return ContractChaincodeMetadata{}, errors.New("Failed to read metadata from file. Metadata file does not exist")
	}

	fileMetadata.Contracts = make(map[string]ContractMetadata)

	metadataBytes, err := ioutilAbs.ReadFile(metadataPath)

	if err != nil {
		return ContractChaincodeMetadata{}, fmt.Errorf("Failed to read metadata from file. Could not read file %s. %s", metadataPath, err)
	}

	json.Unmarshal(metadataBytes, &fileMetadata)

	return fileMetadata, nil
}

// ValidateAgainstSchema takes a ContractChaincodeMetadata and runs it against the
// schema that defines valid metadata structure. If it does not meet the schema it
// returns an error detailing why
func ValidateAgainstSchema(metadata ContractChaincodeMetadata) error {
	jsonSchema, err := GetJSONSchema()

	if err != nil {
		return fmt.Errorf("Failed to read JSON schema. %s", err.Error())
	}

	metadataBytes, _ := json.Marshal(metadata)

	schemaLoader := gojsonschema.NewBytesLoader(jsonSchema)
	metadataLoader := gojsonschema.NewBytesLoader(metadataBytes)

	schema, _ := gojsonschema.NewSchema(schemaLoader)

	result, err := schema.Validate(metadataLoader)

	if !result.Valid() {
		return fmt.Errorf("Cannot use metadata. Metadata did not match schema:\n%s", utils.ValidateErrorsToString(result.Errors()))
	}

	return nil
}
