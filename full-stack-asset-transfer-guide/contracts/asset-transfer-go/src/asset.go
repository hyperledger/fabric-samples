/*
SPDX-License-Identifier: Apache-2.0
*/

package main

import "fmt"

// Asset describes basic details of what makes up a simple asset.
type Asset struct {
	ID             string `json:"ID"`
	Color          string `json:"Color"`
	Owner          string `json:"Owner"`
	AppraisedValue int    `json:"AppraisedValue"`
	Size           int    `json:"Size"`
}

// NewAsset creates a new Asset with validation, equivalent to the
// TypeScript Asset.newInstance() method.
func NewAsset(state Asset) (*Asset, error) {
	if state.ID == "" {
		return nil, fmt.Errorf("missing ID")
	}

	if state.Owner == "" {
		return nil, fmt.Errorf("missing Owner")
	}

	asset := &Asset{
		ID:             state.ID,
		Color:          state.Color,
		Owner:          state.Owner,
		AppraisedValue: state.AppraisedValue,
		Size:           state.Size,
	}

	return asset, nil
}
