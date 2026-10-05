#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Python implementation of the asset-transfer-basic sample.

This chaincode implements the same transactions as the Go, JavaScript,
TypeScript and Java implementations in this sample:

- InitLedger
- CreateAsset
- ReadAsset
- UpdateAsset
- DeleteAsset
- AssetExists
- TransferAsset
- GetAllAssets

It is written against the high-level contract API of the
`fabric-chaincode-python <https://github.com/hyperledger/fabric-chaincode-python>`_
library: Init/Invoke transactions are dispatched automatically to the
contract methods, and the string arguments sent by the peer are converted
to the annotated parameter types.

Run it as a chaincode-as-a-service (CCAAS) by setting the following
environment variables and then running ``python main.py``:

- ``CHAINCODE_SERVER_ADDRESS`` — address to listen on, e.g. ``0.0.0.0:9999``
- ``CHAINCODE_ID`` — the chaincode package ID, e.g. from
  ``peer lifecycle chaincode queryinstalled --output json``
"""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from typing import List, Optional

from fabric_chaincode_python.fabric_contract_api import (
    Contract,
    ContractChaincode,
    TransactionContextInterface,
)


# ---------------------------------------------------------------------------
# Asset
# ---------------------------------------------------------------------------


@dataclass
class Asset:
    """Represents a simple asset with basic properties.

    Field names and ordering match the on-chain JSON used by the other
    language implementations of this sample, so an asset written by any of
    the implementations can be read by the others.
    """

    AppraisedValue: int = 0
    Color: str = ""
    ID: str = ""
    Owner: str = ""
    Size: int = 0


def _asset_from_json(data: dict, asset_id: str = "") -> Asset:
    """Build an :class:`Asset` from its JSON representation."""
    return Asset(
        AppraisedValue=int(data.get("AppraisedValue", 0) or 0),
        Color=str(data.get("Color", "")),
        ID=str(data.get("ID", asset_id)),
        Owner=str(data.get("Owner", "")),
        Size=int(data.get("Size", 0) or 0),
    )


# ---------------------------------------------------------------------------
# Contract
# ---------------------------------------------------------------------------


class AssetContract(Contract):
    """Asset management contract for the asset-transfer-basic sample.

    Every public method becomes a callable transaction. The contract API
    inspects each method's parameter and return-type annotations and
    converts the string arguments coming from the peer into typed Python
    values.
    """

    Name: str = "AssetContract"

    # ------------------------------------------------------------------
    # Helpers
    # ------------------------------------------------------------------

    async def _write_asset(self, ctx: TransactionContextInterface,
                           asset: Asset) -> None:
        """Serialise *asset* and write it to the world state under its ID."""
        payload = json.dumps(asdict(asset), separators=(",", ":")).encode("utf-8")
        await ctx.get_stub().put_state(asset.ID, payload)

    async def _read_asset(self, ctx: TransactionContextInterface,
                          asset_id: str) -> Optional[Asset]:
        """Return the :class:`Asset` stored at *asset_id* or ``None``."""
        raw = await ctx.get_stub().get_state(asset_id)
        if not raw:
            return None
        try:
            data = json.loads(raw.decode("utf-8"))
        except (TypeError, ValueError):
            return None
        return _asset_from_json(data, asset_id)

    # ------------------------------------------------------------------
    # Transactions
    # ------------------------------------------------------------------

    async def InitLedger(self, ctx: TransactionContextInterface) -> None:
        """Populate the ledger with a base set of assets."""
        assets = [
            Asset(AppraisedValue=300, Color="blue", ID="asset1",
                  Owner="Tomoko", Size=5),
            Asset(AppraisedValue=400, Color="red", ID="asset2",
                  Owner="Brad", Size=5),
            Asset(AppraisedValue=500, Color="green", ID="asset3",
                  Owner="Jin Soo", Size=10),
            Asset(AppraisedValue=600, Color="yellow", ID="asset4",
                  Owner="Max", Size=10),
            Asset(AppraisedValue=700, Color="black", ID="asset5",
                  Owner="Adriana", Size=15),
            Asset(AppraisedValue=800, Color="white", ID="asset6",
                  Owner="Michel", Size=15),
        ]
        for asset in assets:
            await self._write_asset(ctx, asset)

    async def CreateAsset(self, ctx: TransactionContextInterface,
                          asset_id: str, color: str, size: int,
                          owner: str, appraised_value: int) -> None:
        """Create a new asset on the ledger.

        Errors out if an asset with the same ID already exists.
        """
        existing = await ctx.get_stub().get_state(asset_id)
        if existing:
            raise ValueError(f"the asset {asset_id} already exists")
        asset = Asset(
            AppraisedValue=appraised_value,
            Color=color,
            ID=asset_id,
            Owner=owner,
            Size=size,
        )
        await self._write_asset(ctx, asset)

    async def ReadAsset(self, ctx: TransactionContextInterface,
                        asset_id: str) -> Asset:
        """Read an asset from the ledger."""
        asset = await self._read_asset(ctx, asset_id)
        if asset is None:
            raise ValueError(f"the asset {asset_id} does not exist")
        return asset

    async def UpdateAsset(self, ctx: TransactionContextInterface,
                          asset_id: str, color: str, size: int,
                          owner: str, appraised_value: int) -> None:
        """Update an existing asset on the ledger.

        Errors out if no asset exists at *asset_id*.
        """
        existing = await ctx.get_stub().get_state(asset_id)
        if not existing:
            raise ValueError(f"the asset {asset_id} does not exist")
        asset = Asset(
            AppraisedValue=appraised_value,
            Color=color,
            ID=asset_id,
            Owner=owner,
            Size=size,
        )
        await self._write_asset(ctx, asset)

    async def DeleteAsset(self, ctx: TransactionContextInterface,
                          asset_id: str) -> None:
        """Delete an asset from the ledger."""
        existing = await ctx.get_stub().get_state(asset_id)
        if not existing:
            raise ValueError(f"the asset {asset_id} does not exist")
        await ctx.get_stub().delete_state(asset_id)

    async def AssetExists(self, ctx: TransactionContextInterface,
                          asset_id: str) -> bool:
        """Return ``True`` if an asset with the given ID exists in the world state."""
        return bool(await ctx.get_stub().get_state(asset_id))

    async def TransferAsset(self, ctx: TransactionContextInterface,
                            asset_id: str, new_owner: str) -> str:
        """Update the owner of an asset and return the old owner."""
        asset = await self._read_asset(ctx, asset_id)
        if asset is None:
            raise ValueError(f"the asset {asset_id} does not exist")
        old_owner = asset.Owner
        asset.Owner = new_owner
        await self._write_asset(ctx, asset)
        return old_owner

    async def GetAllAssets(self, ctx: TransactionContextInterface) -> List[Asset]:
        """Return all assets currently stored in the world state."""
        assets: List[Asset] = []
        iterator = await ctx.get_stub().get_state_by_range("", "")
        async for kv in iterator:
            try:
                data = json.loads(kv.value.decode("utf-8"))
            except (TypeError, ValueError):
                continue
            assets.append(_asset_from_json(data, kv.key))
        return assets

    # ------------------------------------------------------------------
    # Optional metadata hints
    # ------------------------------------------------------------------

    def get_evaluate_transactions(self) -> List[str]:
        """Mark read-only transactions for the auto-generated metadata."""
        return ["ReadAsset", "AssetExists", "GetAllAssets"]


# ---------------------------------------------------------------------------
# CCAAS entry point
# ---------------------------------------------------------------------------


def build_chaincode() -> ContractChaincode:
    """Build the :class:`ContractChaincode` from :class:`AssetContract`."""
    return ContractChaincode.new_chaincode(AssetContract())


def main() -> None:
    """Start the gRPC chaincode server (chaincode-as-a-service)."""
    build_chaincode().start()


if __name__ == "__main__":
    main()
