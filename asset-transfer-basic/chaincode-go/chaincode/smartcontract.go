package chaincode

import (
	"encoding/json"
	"fmt"

	"github.com/hyperledger/fabric-contract-api-go/v2/contractapi"
)

// SmartContract provides functions for managing an Asset
type SmartContract struct {
	contractapi.Contract
}

// Asset describes basic details of what makes up a simple asset
// Insert struct field in alphabetic order => to achieve determinism across languages
// golang keeps the order when marshal to json but doesn't order automatically
type Asset struct {
	ID                       string `json:"id"`
	Owner                    string `json:"owner"`
	PrescripcionAnteriorId   string `json:"prescripcionAnteriorId"`
	Status                   string `json:"status"`
	StatusChange             string `json:"statusChange"`
	Prioridad                string `json:"prioridad"`
	Medicacion               string `json:"medicacion"`
	Razon                    string `json:"razon"`
	Notas                    string `json:"notas"`
	PeriodoDeTratamiento     string `json:"periodoDeTratamiento"`
	InstruccionesTratamiento string `json:"instruccionesTratamiento"`
	PeriodoDeValidez         string `json:"periodoDeValidez"`
	DniPaciente              string `json:"dniPaciente"`
	FechaDeAutorizacion      string `json:"fechaDeAutorizacion"`
	Cantidad                 string `json:"cantidad"`
	ExpectedSupplyDuration   string `json:"expectedSupplyDuration"`
}

// InitLedger adds a base set of assets to the ledger
// InitLedger adds a base set of assets to the ledger
func (s *SmartContract) InitLedger(ctx contractapi.TransactionContextInterface) error {
	assets := []Asset{
		{
			ID:                       "asset1",
			Owner:                    "Tomoko",
			PrescripcionAnteriorId:   "presc123",
			Status:                   "active",
			StatusChange:             "2024-01-15T10:00:00Z",
			Prioridad:                "high",
			Medicacion:               "medication1",
			Razon:                    "reason1",
			Notas:                    "some notes",
			PeriodoDeTratamiento:     "30 days",
			InstruccionesTratamiento: "take daily",
			PeriodoDeValidez:         "1 year",
			DniPaciente:              "12345678",
			FechaDeAutorizacion:      "2024-01-01T09:00:00Z",
			Cantidad:                 "5",
			ExpectedSupplyDuration:   "2024-02-01T09:00:00Z",
		},
		{
			ID:                       "asset2",
			Owner:                    "Alice",
			PrescripcionAnteriorId:   "presc456",
			Status:                   "completed",
			StatusChange:             "2024-02-20T11:00:00Z",
			Prioridad:                "medium",
			Medicacion:               "medication2",
			Razon:                    "reason2",
			Notas:                    "other notes",
			PeriodoDeTratamiento:     "60 days",
			InstruccionesTratamiento: "take twice daily",
			PeriodoDeValidez:         "2 years",
			DniPaciente:              "87654321",
			FechaDeAutorizacion:      "2024-01-10T10:00:00Z",
			Cantidad:                 "10",
			ExpectedSupplyDuration:   "2024-04-10T10:00:00Z",
		},
	}

	for _, asset := range assets {
		assetJSON, err := json.Marshal(asset)
		if err != nil {
			return err
		}

		err = ctx.GetStub().PutState(asset.ID, assetJSON)
		if err != nil {
			return fmt.Errorf("failed to put to world state. %v", err)
		}
	}

	return nil
}

// CreateAsset issues a new asset to the world state with given details.
func (s *SmartContract) CreateAsset(ctx contractapi.TransactionContextInterface, id string, owner string, prescripcionAnteriorId string, status string, statusChange string, prioridad string, medicacion string, razon string, notas string, periodoDeTratamiento string, instruccionesTratamiento string, periodoDeValidez string, dniPaciente string, fechaDeAutorizacion string, cantidad string, expectedSupplyDuration string) error {
	exists, err := s.AssetExists(ctx, id)
	if err != nil {
		return err
	}
	if exists {
		return fmt.Errorf("the asset %s already exists", id)
	}

	asset := Asset{
		ID:                       id,
		Owner:                    owner,
		PrescripcionAnteriorId:   prescripcionAnteriorId,
		Status:                   status,
		StatusChange:             statusChange,
		Prioridad:                prioridad,
		Medicacion:               medicacion,
		Razon:                    razon,
		Notas:                    notas,
		PeriodoDeTratamiento:     periodoDeTratamiento,
		InstruccionesTratamiento: instruccionesTratamiento,
		PeriodoDeValidez:         periodoDeValidez,
		DniPaciente:              dniPaciente,
		FechaDeAutorizacion:      fechaDeAutorizacion,
		Cantidad:                 cantidad,
		ExpectedSupplyDuration:   expectedSupplyDuration,
	}
	assetJSON, err := json.Marshal(asset)
	if err != nil {
		return err
	}

	return ctx.GetStub().PutState(id, assetJSON)
}

// UpdateAsset updates an existing asset in the world state with provided parameters.
func (s *SmartContract) UpdateAsset(ctx contractapi.TransactionContextInterface, id string, owner string, prescripcionAnteriorId string, status string, statusChange string, prioridad string, medicacion string, razon string, notas string, periodoDeTratamiento string, instruccionesTratamiento string, periodoDeValidez string, dniPaciente string, fechaDeAutorizacion string, cantidad string, expectedSupplyDuration string) error {
	exists, err := s.AssetExists(ctx, id)
	if err != nil {
		return err
	}
	if !exists {
		return fmt.Errorf("the asset %s does not exist", id)
	}

	// overwriting original asset with new asset
	asset := Asset{
		ID:                       id,
		Owner:                    owner,
		PrescripcionAnteriorId:   prescripcionAnteriorId,
		Status:                   status,
		StatusChange:             statusChange,
		Prioridad:                prioridad,
		Medicacion:               medicacion,
		Razon:                    razon,
		Notas:                    notas,
		PeriodoDeTratamiento:     periodoDeTratamiento,
		InstruccionesTratamiento: instruccionesTratamiento,
		PeriodoDeValidez:         periodoDeValidez,
		DniPaciente:              dniPaciente,
		FechaDeAutorizacion:      fechaDeAutorizacion,
		Cantidad:                 cantidad,
		ExpectedSupplyDuration:   expectedSupplyDuration,
	}
	assetJSON, err := json.Marshal(asset)
	if err != nil {
		return err
	}

	return ctx.GetStub().PutState(id, assetJSON)
}

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

// DeleteAsset deletes an given asset from the world state.
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

// AssetExists returns true when asset with given ID exists in world state
func (s *SmartContract) AssetExists(ctx contractapi.TransactionContextInterface, id string) (bool, error) {
	assetJSON, err := ctx.GetStub().GetState(id)
	if err != nil {
		return false, fmt.Errorf("failed to read from world state: %v", err)
	}

	return assetJSON != nil, nil
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
