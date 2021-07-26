package main

// Asset describes basic details of what makes up a simple asset
type Asset struct {
	ID       string `json:"ID"`
	Value    string `json:"color"`
	Owner    string `json:"owner"`
	OwnerOrg string `json:"appraisedValue"`
}
