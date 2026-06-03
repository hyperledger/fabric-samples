package main

type Asset struct {
	ID             string `json:"ID"`
	Color          string `json:"Color"`
	Owner          string `json:"Owner"`
	AppraisedValue int    `json:"AppraisedValue"`
	Size           int    `json:"Size"`
}

type ownerIdentifier struct {
	Org  string `json:"org"`
	User string `json:"user"`
}
