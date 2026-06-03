# Go chaincode for the full-stack asset transfer guide

This folder contains a Go implementation of the `asset-transfer` smart contract used by the full-stack asset transfer guide.

The contract implements the following operations:

- `CreateAsset`
- `ReadAsset`
- `UpdateAsset`
- `DeleteAsset`
- `AssetExists`
- `TransferAsset`
- `GetAllAssets`

The implementation preserves the same asset data model and state-based endorsement behavior as the existing TypeScript contract implementation.
