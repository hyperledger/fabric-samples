#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Python implementation of the asset-transfer-sbe sample.

This chaincode implements the same transactions as the Java and
TypeScript implementations in this sample:

- CreateAsset
- ReadAsset
- UpdateAsset
- DeleteAsset
- TransferAsset
- AssetExists

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

NOTE ON STATE-BASED ENDORSEMENT: the Java and TypeScript implementations
apply a key-level (state-based) endorsement policy to each asset with
``setStateValidationParameter``, so that only the organization owning an
asset can endorse updates to it. The ``fabric-chaincode-python`` shim does
not yet expose the equivalent of ``setStateValidationParameter``, so this
implementation mirrors the SBE data model and the transfer flow but does
not apply key-level endorsement policies. Until the shim supports it,
the endorsement-policy scenario of this sample (updates endorsed only by
the owning organization) cannot be demonstrated with this implementation.
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
    """Represents an asset managed with state-based endorsement.

    Field names and ordering match the on-chain JSON used by the other
    language implementations of this sample.
    """

    ID: str = ""
    Value: int = 0
    Owner: str = ""
    OwnerOrg: str = ""


def _asset_from_json(data: dict, asset_id: str = "") -> Asset:
    """Build an :class:`Asset` from its JSON representation."""
    return Asset(
        ID=str(data.get("ID", asset_id)),
        Value=int(data.get("Value", 0) or 0),
        Owner=str(data.get("Owner", "")),
        OwnerOrg=str(data.get("OwnerOrg", "")),
    )


# ---------------------------------------------------------------------------
# Contract
# ---------------------------------------------------------------------------


class AssetContract(Contract):
    """Asset transfer contract mirroring ``fabric-samples/asset-transfer-sbe``.

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

    async def _exists(self, ctx: TransactionContextInterface,
                      asset_id: str) -> bool:
        return bool(await ctx.get_stub().get_state(asset_id))

    @staticmethod
    def _client_org_id(ctx: TransactionContextInterface) -> str:
        """Return the MSP ID of the organization of the submitting client."""
        return ctx.get_client_identity().get_mspid()

    # ------------------------------------------------------------------
    # Transactions
    # ------------------------------------------------------------------

    async def CreateAsset(self, ctx: TransactionContextInterface,
                          asset_id: str, value: int, owner: str) -> None:
        """Create a new asset owned by the submitting client's organization."""
        if await self._exists(ctx, asset_id):
            raise ValueError(f"The asset {asset_id} already exists")
        owner_org = self._client_org_id(ctx)
        asset = Asset(
            ID=asset_id,
            Value=value,
            Owner=owner,
            OwnerOrg=owner_org,
        )
        await self._write_asset(ctx, asset)

    async def ReadAsset(self, ctx: TransactionContextInterface,
                        asset_id: str) -> Asset:
        """Read an asset from the ledger."""
        asset = await self._read_asset(ctx, asset_id)
        if asset is None:
            raise ValueError(f"The asset {asset_id} does not exist")
        return asset

    async def UpdateAsset(self, ctx: TransactionContextInterface,
                          asset_id: str, new_value: int) -> None:
        """Update the value of an existing asset."""
        asset = await self._read_asset(ctx, asset_id)
        if asset is None:
            raise ValueError(f"The asset {asset_id} does not exist")
        asset.Value = new_value
        await self._write_asset(ctx, asset)

    async def DeleteAsset(self, ctx: TransactionContextInterface,
                          asset_id: str) -> None:
        """Delete an asset from the ledger."""
        if not await self._exists(ctx, asset_id):
            raise ValueError(f"The asset {asset_id} does not exist")
        await ctx.get_stub().delete_state(asset_id)

    async def TransferAsset(self, ctx: TransactionContextInterface,
                            asset_id: str, new_owner: str,
                            new_owner_org: str) -> None:
        """Transfer the ownership of an asset to a new owner and organization."""
        asset = await self._read_asset(ctx, asset_id)
        if asset is None:
            raise ValueError(f"The asset {asset_id} does not exist")
        asset.Owner = new_owner
        asset.OwnerOrg = new_owner_org
        await self._write_asset(ctx, asset)

    async def AssetExists(self, ctx: TransactionContextInterface,
                          asset_id: str) -> bool:
        """Return ``True`` if an asset with the given ID exists in the world state."""
        return await self._exists(ctx, asset_id)

    # ------------------------------------------------------------------
    # Optional metadata hints
    # ------------------------------------------------------------------

    def get_evaluate_transactions(self) -> List[str]:
        """Mark read-only transactions for the auto-generated metadata."""
        return ["ReadAsset", "AssetExists"]


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
