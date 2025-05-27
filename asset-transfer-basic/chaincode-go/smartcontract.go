package main

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"github.com/hyperledger/fabric-contract-api-go/v2/contractapi"
)

type SmartContract struct {
	contractapi.Contract
}

type Transaction struct {
	Id            string  `json:"id"`
	SrcAccountId  string  `json:"srcAccountId"`
	DestAccountId string  `json:"destAccountId"`
	Amount        float64 `json:"amount"`
	TypesOf       string  `json:"typesOf"`
	Signature     string  `json:"signature"`
	CreateTime    int64   `json:"createTime"`
}

type HonorCertificate struct {
	Id          string `json:"id"`
	UserId      string `json:"userId"`
	Title       string `json:"title"`
	Description string `json:"description"`
	Signature   string `json:"signature"`
	CreateTime  int64  `json:"createTime"`
}

func (s *SmartContract) GetTransaction(ctx contractapi.TransactionContextInterface, transactionId string) (*Transaction, error) {
	transJSON, err := ctx.GetStub().GetState(transactionId)
	if err != nil {
		return nil, fmt.Errorf("failed to read from world state: %v", err)
	}
	if transJSON == nil {
		return nil, fmt.Errorf("the transaction %s does not exist", transactionId)
	}

	var transaction Transaction
	err = json.Unmarshal(transJSON, &transaction)
	if err != nil {
		return nil, err
	}

	return &transaction, nil
}

func (s *SmartContract) UploadTransaction(ctx contractapi.TransactionContextInterface,
	srcAccountId string, destAccountId string, amount float64, typesOf string,
	signature string, createTime int64) (string, error) {

	// 生成交易元数据哈希作为ID
	hash := sha256.New()
	hash.Write([]byte(fmt.Sprintf("%s-%s-%f-%s-%d", srcAccountId, destAccountId, amount, typesOf, createTime)))
	transactionId := hex.EncodeToString(hash.Sum(nil))

	// 检查ID唯一性
	ret, err := ctx.GetStub().GetState(transactionId)
	if err != nil {
		return "", errors.New("get transaction failed")
	} else if ret != nil {
		return "", errors.New("transaction id already exists")
	}

	transaction := Transaction{
		Id:            transactionId,
		SrcAccountId:  srcAccountId,
		DestAccountId: destAccountId,
		Amount:        amount,
		TypesOf:       typesOf,
		Signature:     signature,
		CreateTime:    createTime,
	}

	transactionJSON, err := json.Marshal(transaction)
	if err != nil {
		return "", err
	}

	err = ctx.GetStub().PutState(transaction.Id, transactionJSON)
	if err != nil {
		return "", fmt.Errorf("failed to put transaction to world state: %v", err)
	}

	return transaction.Id, nil
}

func (s *SmartContract) GetHonorCert(ctx contractapi.TransactionContextInterface, certId string) (*HonorCertificate, error) {
	certJSON, err := ctx.GetStub().GetState(certId)
	if err != nil {
		return nil, fmt.Errorf("failed to read from world state: %v", err)
	}
	if certJSON == nil {
		return nil, fmt.Errorf("the honor certificate %s does not exist", certId)
	}

	var cert HonorCertificate
	err = json.Unmarshal(certJSON, &cert)
	if err != nil {
		return nil, err
	}

	return &cert, nil
}

func (s *SmartContract) MintHonorCert(ctx contractapi.TransactionContextInterface,
	userId, title, description, signature string, createTime int64) (string, error) {

	// 生成证书元数据哈希作为ID
	hash := sha256.New()
	hash.Write([]byte(fmt.Sprintf("%s-%s-%s-%d", userId, title, description, createTime)))
	certId := hex.EncodeToString(hash.Sum(nil))

	// 检查ID唯一性
	ret, err := ctx.GetStub().GetState(certId)
	if err != nil {
		return "", fmt.Errorf("failed to get certificate: %w", err)
	} else if ret != nil {
		return "", fmt.Errorf("certificate already exists")
	}

	cert := HonorCertificate{
		Id:          fmt.Sprintf("CERT%d", createTime),
		UserId:      userId,
		Title:       title,
		Description: description,
		Signature:   signature,
		CreateTime:  createTime,
	}

	certJSON, err := json.Marshal(cert)
	if err != nil {
		return "", err
	}

	err = ctx.GetStub().PutState(cert.Id, certJSON)
	if err != nil {
		return "", fmt.Errorf("failed to put honor certificate to world state: %v", err)
	}

	return cert.Id, nil
}
