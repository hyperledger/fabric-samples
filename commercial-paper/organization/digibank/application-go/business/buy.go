package business

import (
	"log"
)

func Buy() error {
	contract, err := getContract()
	if err != nil {
		return err
	}
	issueResponse, err := contract.SubmitTransaction("buy", "MagnetoCorp", "00001", "MagnetoCorp", "DigiBank", "4900000", "2020-05-31")
	if err != nil {
		return err
	}
	log.Println("Process issue transaction response." + string(issueResponse))
	return nil
}
