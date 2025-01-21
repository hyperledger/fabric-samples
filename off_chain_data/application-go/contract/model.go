/*
 * Copyright 2024 IBM All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package contract

type Asset struct {
	ID             string `json:"ID"`
	Color          string `json:"Color"`
	Size           uint64 `json:"Size"`
	Owner          string `json:"Owner"`
	AppraisedValue uint64 `json:"AppraisedValue"`
}
