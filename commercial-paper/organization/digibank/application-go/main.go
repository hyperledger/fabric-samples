package main

import (
	"flag"
	"fmt"
	"os"

	"github.com/fabric-samples/commercial-paper/organization/digibank/application-go/business"
)

var (
	op string
)

func init() {
	flag.StringVar(&op, "op", "", "Execute the action: addUser, buy, redeem")
}

func main() {
	flag.Parse()
	var err error
	switch op {
	case "addUser":
		err = business.AddUser()
	case "buy":
		err = business.Buy()
	case "redeem":
		err = business.Redeem()
	}
	if err != nil {
		fmt.Printf("Failed to op : %s\n", err)
		os.Exit(1)
	}
}
