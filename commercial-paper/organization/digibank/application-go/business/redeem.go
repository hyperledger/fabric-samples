package business

import "log"

func Redeem() error {
	contract, err := getContract()
	if err != nil {
		return err
	}
	issueResponse, err := contract.SubmitTransaction("redeem", "MagnetoCorp", "00001", "DigiBank", "Org2MSP", "2020-11-30")
	if err != nil {
		return err
	}
	log.Println("Process issue transaction response." + string(issueResponse))
	return nil

}
