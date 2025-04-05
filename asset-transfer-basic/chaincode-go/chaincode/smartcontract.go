package chaincode

import (
	"encoding/json"
	"fmt"

	"github.com/hyperledger/fabric-contract-api-go/v2/contractapi"
)

type SmartContract struct {
	contractapi.Contract
}

type Receta struct {
	ID                       string `json:"id"`
	Identificador			 string `json:"identificador"`
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

func (s *SmartContract) InitLedger(ctx contractapi.TransactionContextInterface) error {
	recetas := []Receta{
		{
			ID:                       "receta1",
			Identificador			  "rece1234"
			Owner:                    "Tomoko",
			PrescripcionAnteriorId:   "presc123",
			Status:                   "active",
			StatusChange:             "2024-01-15T10:00:00Z",
			Prioridad:                "high",
			Medicacion:               "medicacion1",
			Razon:                    "razon1",
			Notas:                    "algunas notas",
			PeriodoDeTratamiento:     "30 dias",
			InstruccionesTratamiento: "una por dia",
			PeriodoDeValidez:         "1 anio",
			DniPaciente:              "12345678",
			FechaDeAutorizacion:      "2024-01-01T09:00:00Z",
			Cantidad:                 "5",
			ExpectedSupplyDuration:   "2024-02-01T09:00:00Z",
		},
		{
			ID:                       "receta2",
			Identificador             "rece1235"
			Owner:                    "Alice",
			PrescripcionAnteriorId:   "presc456",
			Status:                   "completed",
			StatusChange:             "2024-02-20T11:00:00Z",
			Prioridad:                "medium",
			Medicacion:               "medicacion2",
			Razon:                    "razon2",
			Notas:                    "otras notas",
			PeriodoDeTratamiento:     "60 dias",
			InstruccionesTratamiento: "dos por dia",
			PeriodoDeValidez:         "2 anios",
			DniPaciente:              "87654321",
			FechaDeAutorizacion:      "2024-01-10T10:00:00Z",
			Cantidad:                 "10",
			ExpectedSupplyDuration:   "2024-04-10T10:00:00Z",
		},
	}

	for _, receta := range recetas {
		recetaJSON, err := json.Marshal(receta)
		if err != nil {
			return err
		}

		err = ctx.GetStub().PutState(receta.ID, recetaJSON)
		if err != nil {
			return fmt.Errorf("error al guardar receta en el ledger: %v", err)
		}
	}

	return nil
}

func (s *SmartContract) CreateReceta(ctx contractapi.TransactionContextInterface, receta Receta) error {
	exists, err := s.RecetaExists(ctx, receta.ID)
	if err != nil {
		return err
	}
	if exists {
		return fmt.Errorf("la receta %s ya existe", receta.ID)
	}

	recetaJSON, err := json.Marshal(receta)
	if err != nil {
		return err
	}

	return ctx.GetStub().PutState(receta.ID, recetaJSON)
}

func (s *SmartContract) EntregarReceta(ctx contractapi.TransactionContextInterface, recetaID string) error {
	exists, err := s.RecetaExists(ctx, recetaID)
	if err != nil {
		return err
	}
	if !exists {
		return fmt.Errorf("la receta %s no existe", recetaID)
	}

	// Obtener la receta actual
	recetaActualJSON, err := ctx.GetStub().GetState(recetaID)
	if err != nil {
		return fmt.Errorf("error al obtener la receta actual: %v", err)
	}
	if recetaActualJSON == nil {
		return fmt.Errorf("la receta %s no fue encontrada en el ledger", recetaID)
	}

	var recetaActual Receta
	if err := json.Unmarshal(recetaActualJSON, &recetaActual); err != nil {
		return fmt.Errorf("error al parsear la receta actual: %v", err)
	}

	// Validar que el estado actual sea ACTIVO
	if recetaActual.Status != "ACTIVO" {
		return fmt.Errorf("solo se puede entregar la receta si está en estado 'ACTIVO'")
	}

	// Cambiar el estado a ENTREGADO
	recetaActual.Status = "ENTREGADO"

	// Guardar la receta modificada
	updatedRecetaJSON, err := json.Marshal(recetaActual)
	if err != nil {
		return fmt.Errorf("error al serializar la receta modificada: %v", err)
	}

	return ctx.GetStub().PutState(recetaID, updatedRecetaJSON)
}

func (s *SmartContract) ReadReceta(ctx contractapi.TransactionContextInterface, id string) (*Receta, error) {
	recetaJSON, err := ctx.GetStub().GetState(id)
	if err != nil {
		return nil, fmt.Errorf("error al leer del ledger: %v", err)
	}
	if recetaJSON == nil {
		return nil, fmt.Errorf("la receta %s no existe", id)
	}

	var receta Receta
	err = json.Unmarshal(recetaJSON, &receta)
	if err != nil {
		return nil, err
	}

	return &receta, nil
}

func (s *SmartContract) DeleteReceta(ctx contractapi.TransactionContextInterface, id string) error {
	exists, err := s.RecetaExists(ctx, id)
	if err != nil {
		return err
	}
	if !exists {
		return fmt.Errorf("la receta %s no existe", id)
	}

	return ctx.GetStub().DelState(id)
}

func (s *SmartContract) RecetaExists(ctx contractapi.TransactionContextInterface, id string) (bool, error) {
	recetaJSON, err := ctx.GetStub().GetState(id)
	if err != nil {
		return false, fmt.Errorf("error al acceder al ledger: %v", err)
	}
	return recetaJSON != nil, nil
}

func (s *SmartContract) TransferirReceta(ctx contractapi.TransactionContextInterface, id string, nuevoOwner string) (string, error) {
	receta, err := s.ReadReceta(ctx, id)
	if err != nil {
		return "", err
	}

	oldOwner := receta.Owner
	receta.Owner = nuevoOwner

	recetaJSON, err := json.Marshal(receta)
	if err != nil {
		return "", err
	}

	err = ctx.GetStub().PutState(id, recetaJSON)
	if err != nil {
		return "", err
	}

	return oldOwner, nil
}

func (s *SmartContract) GetAllRecetas(ctx contractapi.TransactionContextInterface) ([]*Receta, error) {
	resultsIterator, err := ctx.GetStub().GetStateByRange("", "")
	if err != nil {
		return nil, err
	}
	defer resultsIterator.Close()

	var recetas []*Receta
	for resultsIterator.HasNext() {
		queryResponse, err := resultsIterator.Next()
		if err != nil {
			return nil, err
		}

		var receta Receta
		err = json.Unmarshal(queryResponse.Value, &receta)
		if err != nil {
			return nil, err
		}
		recetas = append(recetas, &receta)
	}

	return recetas, nil
}

func (s *SmartContract) GetMultipleRecetas(ctx contractapi.TransactionContextInterface, recetaIDs []string) ([]*Receta, error) {
	var recetas []*Receta
	for _, id := range recetaIDs {
		recetaJSON, err := ctx.GetStub().GetState(id)
		if err != nil {
			return nil, fmt.Errorf("error al leer del ledger: %v", err)
		}
		if recetaJSON == nil {
			continue
		}

		var receta Receta
		err = json.Unmarshal(recetaJSON, &receta)
		if err != nil {
			return nil, err
		}
		recetas = append(recetas, &receta)
	}
	return recetas, nil
}
// TODO: adaptar los campos para que se tengan un identificar de usuarios ademas del DNI
func (s *SmartContract) GetRecetasPorDniYEstado(ctx contractapi.TransactionContextInterface, dni string, estado string) ([]*Receta, error) {
	if dni == "" || estado == "" {
		return nil, fmt.Errorf("el dni y el estado son obligatorios")
	}

	resultsIterator, err := ctx.GetStub().GetStateByRange("", "")
	if err != nil {
		return nil, fmt.Errorf("error al obtener datos del ledger: %v", err)
	}
	defer resultsIterator.Close()

	var recetasFiltradas []*Receta
	for resultsIterator.HasNext() {
		queryResponse, err := resultsIterator.Next()
		if err != nil {
			return nil, err
		}

		var receta Receta
		err = json.Unmarshal(queryResponse.Value, &receta)
		if err != nil {
			return nil, err
		}

		if receta.DniPaciente == dni && receta.Status == estado {
			recetasFiltradas = append(recetasFiltradas, &receta)
		}
	}

	return recetasFiltradas, nil
}
