package main

type OwnerIdentifier struct {
	Org  string `json:"org"`
	User string `json:"user"`
}

type Asset struct {
	AppraisedValue int    `json:"AppraisedValue"`
	Color          string `json:"Color"`
	ID             string `json:"ID"`
	Owner          string `json:"Owner"` // JSON-encoded OwnerIdentifier
	Size           int    `json:"Size"`
}
