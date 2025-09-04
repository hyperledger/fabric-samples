package chaincode

import (
	"encoding/json"
	"fmt"
	"strings"

	"github.com/hyperledger/fabric-contract-api-go/v2/contractapi"
)

type SmartContract struct {
	contractapi.Contract
}

type Receta struct {
	ID                         string `json:"id"`
	Identifier                 string `json:"identifier"`
	Owner                      string `json:"owner"`
	PrescripcionAnteriorId     string `json:"prescripcionAnteriorId"`
	Status                     string `json:"status"`
	StatusChange               string `json:"statusChange"`
	Prioridad                  string `json:"prioridad"`
	Medicacion                 string `json:"medicacion"`
	Razon                      string `json:"razon"`
	Notas                      string `json:"notas"`
	PeriodoDeTratamiento       string `json:"periodoDeTratamiento"`
	InstruccionesTratamiento   string `json:"instruccionesTratamiento"`
	PeriodoDeValidez           string `json:"periodoDeValidez"`
	PatientDocumentNumber      string `json:"patientDocumentNumber"`
	FechaDeAutorizacion        string `json:"fechaDeAutorizacion"`
	Cantidad                   string `json:"cantidad"`
	ExpectedSupplyDuration     string `json:"expectedSupplyDuration"`
	Practitioner               string `json:"practitioner"`
	PractitionerDocumentNumber string `json:"practitionerDocumentNumber"`
	Signature                  string `json:"signature"`
	Matricula                  string `json:"matricula"`
}

type Vacuna struct {
	ID                         string `json:"id"`
	Identifier                 string `json:"identifier"`
	Status                     string `json:"status"`
	StatusChange               string `json:"statusChange"`
	StatusReason               string `json:"statusReason"`
	VaccinateCode              string `json:"vaccinateCode"`
	AdministradedProduct       string `json:"administradedProduct"`
	Manufacturer               string `json:"manufacturer"`
	LotNumber                  string `json:"lotNumber"`
	ExpirationDate             string `json:"expirationDate"`
	PatientDocumentNumber      string `json:"patientDocumentNumber"`
	Reactions                  string `json:"reactions"`
	Practitioner               string `json:"practitioner"`
	PractitionerDocumentNumber string `json:"practitionerDocumentNumber"`
	Matricula                  string `json:"matricula"`
}

type Estado string

const (
	EstadoDraft     Estado = "draft"
	EstadoActive    Estado = "active"
	EstadoOnHold    Estado = "on_hold"
	EstadoCancelled Estado = "cancelled"
	EstadoCompleted Estado = "completed"
	EstadoStoped    Estado = "stoped"
)

func (s *SmartContract) InitLedger(ctx contractapi.TransactionContextInterface) error {
	recetas := []Receta{
		{
			ID:                         "receta1",
			Identifier:                 "rece1234",
			Owner:                      "Tomoko",
			PrescripcionAnteriorId:     "presc123",
			Status:                     "active",
			StatusChange:               "2024-01-15T10:00:00Z",
			Prioridad:                  "high",
			Medicacion:                 "medicacion1",
			Razon:                      "razon1",
			Notas:                      "algunas notas",
			PeriodoDeTratamiento:       "30 dias",
			InstruccionesTratamiento:   "una por dia",
			PeriodoDeValidez:           "1 anio",
			PatientDocumentNumber:      "12345678",
			FechaDeAutorizacion:        "2024-01-01T09:00:00Z",
			Cantidad:                   "5",
			ExpectedSupplyDuration:     "2024-02-01T09:00:00Z",
			Practitioner:               "practitioner",
			PractitionerDocumentNumber: "123456789",
			Signature:                  "signature",
			Matricula:                  "matricula123",
		},
		{
			ID:                         "receta2",
			Identifier:                 "rece1235",
			Owner:                      "Alice",
			PrescripcionAnteriorId:     "presc456",
			Status:                     "completed",
			StatusChange:               "2024-02-20T11:00:00Z",
			Prioridad:                  "medium",
			Medicacion:                 "medicacion2",
			Razon:                      "razon2",
			Notas:                      "otras notas",
			PeriodoDeTratamiento:       "60 dias",
			InstruccionesTratamiento:   "dos por dia",
			PeriodoDeValidez:           "2 anios",
			PatientDocumentNumber:      "87654321",
			FechaDeAutorizacion:        "2024-01-10T10:00:00Z",
			Cantidad:                   "10",
			ExpectedSupplyDuration:     "2024-04-10T10:00:00Z",
			Practitioner:               "practitioner",
			PractitionerDocumentNumber: "123456789",
			Signature:                  "signature",
			Matricula:                  "matricula456",
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

func (s *SmartContract) FirmarReceta(ctx contractapi.TransactionContextInterface, recetaID string, firma string) error {
	exists, err := s.RecetaExists(ctx, recetaID)
	if err != nil {
		return err
	}
	if !exists {
		return fmt.Errorf("la receta %s no existe", recetaID)
	}
	recetaJSON, err := ctx.GetStub().GetState(recetaID)
	if err != nil {
		return fmt.Errorf("error al obtener la receta: %v", err)
	}
	if recetaJSON == nil {
		return fmt.Errorf("la receta %s no fue encontrada en el ledger", recetaID)
	}
	var receta Receta
	if err := json.Unmarshal(recetaJSON, &receta); err != nil {
		return fmt.Errorf("error al parsear la receta: %v", err)
	}
	if receta.Status != string(EstadoDraft) {
		return fmt.Errorf("la receta %s no puede ser firmada porque no está en estado 'draft'", recetaID)
	}
	receta.Signature = firma
	receta.Status = string(EstadoActive)
	receta.StatusChange = "FIRMADA"
	updatedRecetaJSON, err := json.Marshal(receta)
	if err != nil {
		return fmt.Errorf("error al serializar la receta firmada: %v", err)
	}
	return ctx.GetStub().PutState(recetaID, updatedRecetaJSON)
}

func (s *SmartContract) EntregarReceta(ctx contractapi.TransactionContextInterface, recetaID string) error {
	exists, err := s.RecetaExists(ctx, recetaID)
	if err != nil {
		return err
	}
	if !exists {
		return fmt.Errorf("la receta %s no existe", recetaID)
	}
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
	if recetaActual.Status != string(EstadoActive) {
		return fmt.Errorf("solo se puede entregar la receta si está en estado 'active'")
	}
	recetaActual.Status = string(EstadoCompleted)
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

func (s *SmartContract) DeleteReceta(ctx contractapi.TransactionContextInterface, recetaID string) error {
	exists, err := s.RecetaExists(ctx, recetaID)
	if err != nil {
		return err
	}
	if !exists {
		return fmt.Errorf("la receta %s no existe", recetaID)
	}
	recetaJSON, err := ctx.GetStub().GetState(recetaID)
	if err != nil {
		return fmt.Errorf("error al obtener la receta: %v", err)
	}
	if recetaJSON == nil {
		return fmt.Errorf("la receta %s no fue encontrada en el ledger", recetaID)
	}
	var receta Receta
	if err := json.Unmarshal(recetaJSON, &receta); err != nil {
		return fmt.Errorf("error al parsear la receta: %v", err)
	}
	if receta.Status != string(EstadoDraft) {
		return fmt.Errorf("la receta %s no puede ser firmada porque no está en estado 'draft'", recetaID)
	}

	receta.Status = string(EstadoCancelled)

	updatedRecetaJSON, err := json.Marshal(receta)
	if err != nil {
		return fmt.Errorf("error al serializar la receta firmada: %v", err)
	}

	return ctx.GetStub().PutState(recetaID, updatedRecetaJSON)
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

		if len(queryResponse.Value) == 0 {
			continue
		}

		var receta Receta
		err = json.Unmarshal(queryResponse.Value, &receta)
		if err != nil {
			continue
		}

		if receta.Status == string(EstadoCancelled) {
			continue
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
			return nil, fmt.Errorf("error al parsear la receta con ID %s: %v", id, err)
		}

		status := strings.ToLower(strings.TrimSpace(receta.Status))
		if status == string(EstadoCancelled) {
			continue
		}

		recetas = append(recetas, &receta)
	}
	return recetas, nil
}

type ResultadoPaginado struct {
	Recetas  []*Receta `json:"componentes"`
	Bookmark string    `json:"bookmark"`
}

func (s *SmartContract) GetRecetasPorDniYEstadosPaginado(
	ctx contractapi.TransactionContextInterface,
	dni string,
	estados []string,
	pageSize int32,
	bookmark string,
) (*ResultadoPaginado, error) {
	if dni == "" || len(estados) == 0 {
		return nil, fmt.Errorf("el dni y al menos un estado son obligatorios")
	}
	query := map[string]interface{}{
		"selector": map[string]interface{}{
			"patientDocumentNumber": dni,
			"status": map[string]interface{}{
				"$in": estados,
			},
		},
		"limit": pageSize,
	}
	if bookmark != "" {
		query["bookmark"] = bookmark
	}
	queryBytes, err := json.Marshal(query)
	if err != nil {
		return nil, fmt.Errorf("error al generar la query: %v", err)
	}
	resultsIterator, metadata, err := ctx.GetStub().GetQueryResultWithPagination(string(queryBytes), pageSize, bookmark)
	if err != nil {
		return nil, fmt.Errorf("error al ejecutar la query: %v", err)
	}
	defer resultsIterator.Close()
	var recetas []*Receta
	for resultsIterator.HasNext() {
		response, iterErr := resultsIterator.Next()
		if iterErr != nil {
			return nil, iterErr
		}
		var receta Receta
		if err := json.Unmarshal(response.Value, &receta); err != nil {
			return nil, fmt.Errorf("error al parsear receta: %v", err)
		}

		recetas = append(recetas, &receta)
	}
	if recetas == nil {
		recetas = []*Receta{}
	}
	resultado := &ResultadoPaginado{
		Recetas:  recetas,
		Bookmark: metadata.Bookmark,
	}
	return resultado, nil
}

func (s *SmartContract) CreateVacuna(ctx contractapi.TransactionContextInterface, vacuna Vacuna) error {
	exists, err := s.VacunaExists(ctx, vacuna.ID)
	if err != nil {
		return err
	}
	if exists {
		return fmt.Errorf("la vacuna %s ya existe", vacuna.ID)
	}
	vacunaJSON, err := json.Marshal(vacuna)
	if err != nil {
		return err
	}

	return ctx.GetStub().PutState(vacuna.ID, vacunaJSON)
}

func (s *SmartContract) VacunaExists(ctx contractapi.TransactionContextInterface, id string) (bool, error) {
	vacunaJSON, err := ctx.GetStub().GetState(id)
	if err != nil {
		return false, err
	}
	return vacunaJSON != nil, nil
}

func (s *SmartContract) ReadVacuna(ctx contractapi.TransactionContextInterface, id string) (*Vacuna, error) {
	vacunaJSON, err := ctx.GetStub().GetState(id)
	if err != nil {
		return nil, fmt.Errorf("error al leer del ledger: %v", err)
	}
	if vacunaJSON == nil {
		return nil, fmt.Errorf("la vacuna %s no existe", id)
	}
	var vacuna Vacuna
	err = json.Unmarshal(vacunaJSON, &vacuna)
	if err != nil {
		return nil, err
	}

	return &vacuna, nil
}

func (s *SmartContract) GetMultipleVacunas(ctx contractapi.TransactionContextInterface, vacunaIDs []string) ([]*Vacuna, error) {
	var vacunas []*Vacuna
	for _, id := range vacunaIDs {
		vacunaJSON, err := ctx.GetStub().GetState(id)
		if err != nil {
			return nil, fmt.Errorf("error al leer del ledger: %v", err)
		}
		if vacunaJSON == nil {
			continue
		}
		var vacuna Vacuna
		err = json.Unmarshal(vacunaJSON, &vacuna)
		if err != nil {
			return nil, err
		}
		vacunas = append(vacunas, &vacuna)
	}
	return vacunas, nil
}

type ResultadoPaginadoVacunas struct {
	Vacunas  []*Vacuna `json:"componentes"`
	Bookmark string    `json:"bookmark"`
}

func (s *SmartContract) GetVacunasPorDniPaginado(
	ctx contractapi.TransactionContextInterface,
	dni string,
	pageSize int32,
	bookmark string,
) (*ResultadoPaginadoVacunas, error) {

	if dni == "" {
		return nil, fmt.Errorf("el dni es obligatorio")
	}

	query := map[string]interface{}{
		"selector": map[string]interface{}{
			"patientDocumentNumber": dni,
		},
		// primero probamos sin use_index
		//	"use_index": []string{"vacunas-index", "indexVacunas"},
		"limit": pageSize,
	}
	if bookmark != "" {
		query["bookmark"] = bookmark
	}
	queryBytes, err := json.Marshal(query)
	if err != nil {
		return nil, fmt.Errorf("error al generar la query: %v", err)
	}
	resultsIterator, metadata, err := ctx.GetStub().GetQueryResultWithPagination(string(queryBytes), pageSize, bookmark)
	if err != nil {
		return nil, fmt.Errorf("error al ejecutar la query: %v", err)
	}
	defer resultsIterator.Close()
	var vacunas []*Vacuna
	for resultsIterator.HasNext() {
		response, iterErr := resultsIterator.Next()
		if iterErr != nil {
			return nil, iterErr
		}
		var vacuna Vacuna
		if err := json.Unmarshal(response.Value, &vacuna); err != nil {
			return nil, fmt.Errorf("error al parsear vacuna: %v", err)
		}
		vacunas = append(vacunas, &vacuna)
	}
	if vacunas == nil {
		vacunas = []*Vacuna{}
	}
	resultado := &ResultadoPaginadoVacunas{
		Vacunas:  vacunas,
		Bookmark: metadata.Bookmark,
	}

	return resultado, nil
}

func (s *SmartContract) GetVacunasPorDniYEstado(ctx contractapi.TransactionContextInterface, dni string, estado string) ([]*Vacuna, error) {
	if dni == "" {
		return nil, fmt.Errorf("el dni es obligatorio")
	}
	resultsIterator, err := ctx.GetStub().GetStateByRange("", "")
	if err != nil {
		return nil, fmt.Errorf("error al obtener datos del ledger: %v", err)
	}
	defer resultsIterator.Close()

	var vacunasFiltradas []*Vacuna
	for resultsIterator.HasNext() {
		queryResponse, err := resultsIterator.Next()
		if err != nil {
			return nil, err
		}
		var vacuna Vacuna
		if err := json.Unmarshal(queryResponse.Value, &vacuna); err != nil {
			continue
		}
		if vacuna.PatientDocumentNumber != dni {
			continue
		}
		if estado != "" && vacuna.Status != estado {
			continue
		}
		vacunasFiltradas = append(vacunasFiltradas, &vacuna)
	}

	return vacunasFiltradas, nil
}
