package main

import (
	"flag"
	"fmt"
	"os"

	"github.com/hyperledger/fabric-samples/commercial-paper/organization/magnetocorp/application-go/business"
)

var (
	op string
)

func init() {
	flag.StringVar(&op, "op", "", "Execute the action: addUser, issue")
}

func main() {
	flag.Parse()
	var err error
	switch op {
	case "addUser":
		err = business.AddUser()
	case "issue":
		err = business.Issue()
	}
	if err != nil {
		fmt.Printf("Failed to op : %s\n", err)
		os.Exit(1)
	}
}
