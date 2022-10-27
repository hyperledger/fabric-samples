package main

import (
	"fmt"

	"github.com/xeipuuv/gojsonschema"
)

type Data struct {
	DocType          string   `json:"docType"`
	Id               string   `json:"id"`
	Title            string   `json:"title"`
	Description      string   `json:"description"`
	Type             string   `json:"Type"`
	DOI              string   `json:"DOI"`
	Url              string   `json:"url"`
	Manifest         []string `json:"manifest"`
	Footprint        string   `json:"footprint"`
	Keywords         []string `json:"keywords"`
	OtherDataIdName  string   `json:"otherDataIdName"`
	OtherDataIdValue string   `json:"otherDataIdValue"`
	FundingAgencies  []string `json:"fundingAgencies"`
	Acknowledgment   string   `json:"acknowledgment"`
	NoteForChange    string   `json:"noteForChange"`
	Contributor      string   `json:"contributor"`
	Contributor_id   string   `json:"contributor_id"`
}

/*
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

*/

func main() {

	schemaLoader := gojsonschema.NewReferenceLoader("file:///home/ofgarzon2662/OSC-IS/fabric-samples/test-network/JsonSchemaValidationTests/Schema.json")
	documentLoader := gojsonschema.NewReferenceLoader("file:////home/ofgarzon2662/OSC-IS/fabric-samples/test-network/JsonSchemaValidationTests/testFile.json")

	result, err := gojsonschema.Validate(schemaLoader, documentLoader)
	//fmt.Printf(result.Valid())
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
}
