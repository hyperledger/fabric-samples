package main

import (
	"encoding/json"
	"fmt"
	"log"

	"github.com/hyperledger/fabric-contract-api-go/contractapi"
)

// KYCRecord structure for KYC data
type KYCRecord struct {
	UserID          string `json:"userId"`
	SolanaAddress   string `json:"solanaAddress"`
	FullName        string `json:"fullName"`
	KYCVerified     bool   `json:"kycVerified"`
	VerificationDate string `json:"verificationDate"`
	RiskScore       int    `json:"riskScore"`
	DocumentHash    string `json:"documentHash"`
}

// TransactionRecord structure to track transactions
type TransactionRecord struct {
	TxID            string  `json:"txId"`
	SolanaSignature string  `json:"solanaSignature"`
	FromUser        string  `json:"fromUser"`
	ToUser          string  `json:"toUser"`
	Amount          float64 `json:"amount"`
	Currency        string  `json:"currency"`
	Timestamp       string  `json:"timestamp"`
	Status          string  `json:"status"`
}

// NivixKYCContract manages KYC data for Nivix platform
type NivixKYCContract struct {
	contractapi.Contract
}

// StoreKYCData stores KYC information in a private data collection
func (s *NivixKYCContract) StoreKYCData(ctx contractapi.TransactionContextInterface, 
								userId string, 
								solanaAddress string, 
								fullName string, 
								kycVerified bool,
								verificationDate string,
								riskScore int,
								documentHash string) error {
	
	// Create the KYC record
	kycRecord := KYCRecord{
		UserID:          userId,
		SolanaAddress:   solanaAddress,
		FullName:        fullName,
		KYCVerified:     kycVerified,
		VerificationDate: verificationDate,
		RiskScore:       riskScore,
		DocumentHash:    documentHash,
	}
	
	// Convert to JSON
	kycJSON, err := json.Marshal(kycRecord)
	if err != nil {
		return fmt.Errorf("failed to marshal KYC data: %v", err)
	}
	
	// Store in private data collection
	// Note: In production, this should go to a private data collection
	err = ctx.GetStub().PutPrivateData("kycCollection", userId, kycJSON)
	if err != nil {
		return fmt.Errorf("failed to store KYC data: %v", err)
	}
	
	// Store a public reference that this user has KYC (without personal details)
	publicData := struct {
		SolanaAddress string `json:"solanaAddress"`
		KYCVerified   bool   `json:"kycVerified"`
		RiskScore     int    `json:"riskScore"`
	}{
		SolanaAddress: solanaAddress,
		KYCVerified:   kycVerified,
		RiskScore:     riskScore,
	}
	
	publicJSON, err := json.Marshal(publicData)
	if err != nil {
		return fmt.Errorf("failed to marshal public KYC data: %v", err)
	}
	
	// Store public reference on the main ledger
	return ctx.GetStub().PutState(solanaAddress, publicJSON)
}

// GetKYCStatus quickly checks if a Solana address has KYC verification
func (s *NivixKYCContract) GetKYCStatus(ctx contractapi.TransactionContextInterface, 
									solanaAddress string) (*struct {
										SolanaAddress string `json:"solanaAddress"`
										KYCVerified   bool   `json:"kycVerified"`
										RiskScore     int    `json:"riskScore"`
									}, error) {
	kycBytes, err := ctx.GetStub().GetState(solanaAddress)
	if err != nil {
		return nil, fmt.Errorf("failed to read KYC status: %v", err)
	}
	if kycBytes == nil {
		return nil, fmt.Errorf("the Solana address %s has no KYC record", solanaAddress)
	}
	
	var kycStatus struct {
		SolanaAddress string `json:"solanaAddress"`
		KYCVerified   bool   `json:"kycVerified"`
		RiskScore     int    `json:"riskScore"`
	}
	
	err = json.Unmarshal(kycBytes, &kycStatus)
	if err != nil {
		return nil, fmt.Errorf("failed to unmarshal KYC data: %v", err)
	}
	
	return &kycStatus, nil
}

// RecordTransaction stores a transaction record from Solana
func (s *NivixKYCContract) RecordTransaction(ctx contractapi.TransactionContextInterface,
									txId string,
									solanaSignature string,
									fromUser string,
									toUser string,
									amount float64,
									currency string,
									timestamp string) error {
	
	// Create transaction record
	txRecord := TransactionRecord{
		TxID:            txId,
		SolanaSignature: solanaSignature,
		FromUser:        fromUser,
		ToUser:          toUser,
		Amount:          amount,
		Currency:        currency,
		Timestamp:       timestamp,
		Status:          "completed",
	}
	
	// Convert to JSON
	txJSON, err := json.Marshal(txRecord)
	if err != nil {
		return fmt.Errorf("failed to marshal transaction data: %v", err)
	}
	
	// Store in private data collection
	err = ctx.GetStub().PutPrivateData("transactionCollection", txId, txJSON)
	if err != nil {
		return fmt.Errorf("failed to store transaction data: %v", err)
	}
	
	// Store public reference to transaction
	publicData := struct {
		TxID            string  `json:"txId"`
		SolanaSignature string  `json:"solanaSignature"`
		Amount          float64 `json:"amount"`
		Currency        string  `json:"currency"`
		Timestamp       string  `json:"timestamp"`
		Status          string  `json:"status"`
	}{
		TxID:            txId,
		SolanaSignature: solanaSignature,
		Amount:          amount,
		Currency:        currency,
		Timestamp:       timestamp,
		Status:          "completed",
	}
	
	publicJSON, err := json.Marshal(publicData)
	if err != nil {
		return fmt.Errorf("failed to marshal public transaction data: %v", err)
	}
	
	// Store public reference on the main ledger
	return ctx.GetStub().PutState("tx_"+txId, publicJSON)
}

// GetTransactionSummary gets the public transaction data
func (s *NivixKYCContract) GetTransactionSummary(ctx contractapi.TransactionContextInterface, 
										txId string) (*struct {
											TxID            string  `json:"txId"`
											SolanaSignature string  `json:"solanaSignature"`
											Amount          float64 `json:"amount"`
											Currency        string  `json:"currency"`
											Timestamp       string  `json:"timestamp"`
											Status          string  `json:"status"`
										}, error) {
	txBytes, err := ctx.GetStub().GetState("tx_" + txId)
	if err != nil {
		return nil, fmt.Errorf("failed to read transaction: %v", err)
	}
	if txBytes == nil {
		return nil, fmt.Errorf("the transaction %s does not exist", txId)
	}
	
	var txSummary struct {
		TxID            string  `json:"txId"`
		SolanaSignature string  `json:"solanaSignature"`
		Amount          float64 `json:"amount"`
		Currency        string  `json:"currency"`
		Timestamp       string  `json:"timestamp"`
		Status          string  `json:"status"`
	}
	
	err = json.Unmarshal(txBytes, &txSummary)
	if err != nil {
		return nil, fmt.Errorf("failed to unmarshal transaction data: %v", err)
	}
	
	return &txSummary, nil
}

func main() {
	chaincode, err := contractapi.NewChaincode(&NivixKYCContract{})
	if err != nil {
		log.Panicf("Error creating nivix-kyc chaincode: %v", err)
	}
	
	if err := chaincode.Start(); err != nil {
		log.Panicf("Error starting nivix-kyc chaincode: %v", err)
	}
} 