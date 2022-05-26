/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.Gson;
import org.hyperledger.fabric.client.CommitException;
import org.hyperledger.fabric.client.CommitStatusException;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.EndorseException;
import org.hyperledger.fabric.client.SubmitException;

public final class AssetTransferBasic {
    private static final Gson GSON = new Gson();
    private final Contract contract;

    public AssetTransferBasic(final Contract contract) {
        this.contract = contract;
    }

    public void createAsset(final Asset asset) throws EndorseException, CommitException, SubmitException, CommitStatusException {
        contract.submitTransaction(
                "CreateAsset",
                asset.getId(),
                asset.getColor(),
                Integer.toString(asset.getSize()),
                asset.getOwner(),
                Integer.toString(asset.getAppraisedValue())
        );
    }

    public String transferAsset(final String id, final String newOwner) throws EndorseException, CommitException, SubmitException, CommitStatusException {
        byte[] resultBytes = contract.submitTransaction("TransferAsset", id, newOwner);
        return new String(resultBytes, StandardCharsets.UTF_8);
    }

    public void deleteAsset(final String id) throws EndorseException, CommitException, SubmitException, CommitStatusException {
        contract.submitTransaction("DeleteAsset", id);
    }

    public List<Asset> getAllAssets() throws EndorseException, CommitException, SubmitException, CommitStatusException {
        byte[] resultBytes = contract.submitTransaction("GetAllAssets");
        String resultJson = new String(resultBytes, StandardCharsets.UTF_8);
        Asset[] assets = GSON.fromJson(resultJson, Asset[].class);
        return assets != null ? List.of(assets) : List.of();
    }
}
