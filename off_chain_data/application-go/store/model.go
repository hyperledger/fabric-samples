package store

// Apply writes for a given transaction to off-chain data store, ideally in a single operation for fault tolerance.
type Writer = func(data LedgerUpdate)

// Ledger update made by a specific transaction.
type LedgerUpdate struct {
	BlockNumber   uint64
	TransactionId string
	Writes        []Write
}

// Description of a ledger Write that can be applied to an off-chain data store.
type Write struct {
	// Channel whose ledger is being updated.
	ChannelName string `json:"channelName"`
	// Namespace within the ledger.
	Namespace string `json:"namespace"`
	// Key name within the ledger namespace.
	Key string `json:"key"`
	// Whether the key and associated value are being deleted.
	IsDelete bool `json:"isDelete"`
	// If `isDelete` is false, the Value written to the key; otherwise ignored.
	Value string `json:"value"`
}
