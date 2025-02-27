/*
 * SPDX-License-Identifier: Apache-2.0
 */

// ====CHAINCODE EXECUTION SAMPLES (CLI) ==================

// ==== Invoke assets ====
// peer chaincode invoke -C CHANNEL_NAME -n asset_transfer -c '{"Args":["CreateAsset","asset1","blue","35","Tom","100"]}'
// peer chaincode invoke -C CHANNEL_NAME -n asset_transfer -c '{"Args":["CreateAsset","asset2","red","50","Tom","150"]}'
// peer chaincode invoke -C CHANNEL_NAME -n asset_transfer -c '{"Args":["CreateAsset","asset3","blue","70","Tom","200"]}'
// peer chaincode invoke -C CHANNEL_NAME -n asset_transfer -c '{"Args":["TransferAsset","asset2","jerry"]}'
// peer chaincode invoke -C CHANNEL_NAME -n asset_transfer -c '{"Args":["TransferAssetByColor","blue","jerry"]}'
// peer chaincode invoke -C CHANNEL_NAME -n asset_transfer -c '{"Args":["DeleteAsset","asset1"]}'

// ==== Query assets ====
// peer chaincode query -C CHANNEL_NAME -n asset_transfer -c '{"Args":["ReadAsset","asset1"]}'
// peer chaincode query -C CHANNEL_NAME -n asset_transfer -c '{"Args":["GetAssetsByRange","asset1","asset3"]}'
// peer chaincode query -C CHANNEL_NAME -n asset_transfer -c '{"Args":["GetAssetHistory","asset1"]}'

// Rich Query (Only supported if CouchDB is used as state database):
// peer chaincode query -C CHANNEL_NAME -n asset_transfer -c '{"Args":["QueryAssetsByOwner","Tom"]}' output issue
// peer chaincode query -C CHANNEL_NAME -n asset_transfer -c '{"Args":["QueryAssets","{\"selector\":{\"owner\":\"Tom\"}}"]}'

// Rich Query with Pagination (Only supported if CouchDB is used as state database):
// peer chaincode query -C CHANNEL_NAME -n asset_transfer -c '{"Args":["QueryAssetsWithPagination","{\"selector\":{\"owner\":\"Tom\"}}","3",""]}'

// INDEXES TO SUPPORT COUCHDB RICH QUERIES
//
// Indexes in CouchDB are required in order to make JSON queries efficient and are required for
// any JSON query with a sort. Indexes may be packaged alongside
// chaincode in a META-INF/statedb/couchdb/indexes directory. Each index must be defined in its own
// text file with extension *.json with the index definition formatted in JSON following the
// CouchDB index JSON syntax as documented at:
// http://docs.couchdb.org/en/2.3.1/api/database/find.html#db-index
//
// This asset transfer ledger example chaincode demonstrates a packaged
// index which you can find in META-INF/statedb/couchdb/indexes/indexOwner.json.
//
// If you have access to the your peer's CouchDB state database in a development environment,
// you may want to iteratively test various indexes in support of your chaincode queries.  You
// can use the CouchDB Fauxton interface or a command line curl utility to create and update
// indexes. Then once you finalize an index, include the index definition alongside your
// chaincode in the META-INF/statedb/couchdb/indexes directory, for packaging and deployment
// to managed environments.
//
// In the examples below you can find index definitions that support asset transfer ledger
// chaincode queries, along with the syntax that you can use in development environments
// to create the indexes in the CouchDB Fauxton interface or a curl command line utility.
//

// Index for docType, owner.
//
// Example curl command line to define index in the CouchDB channel_chaincode database
// curl -i -X POST -H "Content-Type: application/json" -d "{\"index\":{\"fields\":[\"docType\",\"owner\"]},\"name\":\"indexOwner\",\"ddoc\":\"indexOwnerDoc\",\"type\":\"json\"}" http://hostname:port/myc1_assets/_index
//

// Index for docType, owner, size (descending order).
//
// Example curl command line to define index in the CouchDB channel_chaincode database
// curl -i -X POST -H "Content-Type: application/json" -d "{\"index\":{\"fields\":[{\"size\":\"desc\"},{\"docType\":\"desc\"},{\"owner\":\"desc\"}]},\"ddoc\":\"indexSizeSortDoc\", \"name\":\"indexSizeSortDesc\",\"type\":\"json\"}" http://hostname:port/myc1_assets/_index

// Rich Query with index design doc and index name specified (Only supported if CouchDB is used as state database):
//   peer chaincode query -C CHANNEL_NAME -n ledger -c '{"Args":["QueryAssets","{\"selector\":{\"docType\":\"asset\",\"owner\":\"Tom\"}, \"use_index\":[\"_design/indexOwnerDoc\", \"indexOwner\"]}"]}'

// Rich Query with index design doc specified only (Only supported if CouchDB is used as state database):
//   peer chaincode query -C CHANNEL_NAME -n ledger -c '{"Args":["QueryAssets","{\"selector\":{\"docType\":{\"$eq\":\"asset\"},\"owner\":{\"$eq\":\"Tom\"},\"size\":{\"$gt\":0}},\"fields\":[\"docType\",\"owner\",\"size\"],\"sort\":[{\"size\":\"desc\"}],\"use_index\":\"_design/indexSizeSortDoc\"}"]}'

import {
    Context,
    Contract,
    Info,
    Returns,
    Transaction,
} from 'fabric-contract-api';
import stringify from 'json-stringify-deterministic';
import sortKeysRecursive from 'sort-keys-recursive';
import { Iterators } from 'fabric-shim';
import { Asset, HistoryQueryResult, PaginatedQueryResult } from './asset';

const compositeKeyPrefix = 'color~name';

@Info({
    title: 'AssetTransfer',
    description: 'Smart Contract for asset transfer ledger sample',
})
export class AssetTransferContract extends Contract {
    // Create a new asset in the ledger
    @Transaction()
    public async CreateAsset(
        ctx: Context,
        assetID: string,
        color: string,
        size: number,
        owner: string,
        appraisedValue: number
    ): Promise<void> {
        const exists = await this.AssetExists(ctx, assetID);
        if (exists) {
            throw new Error(`Asset already exists: ${assetID}`);
        }

        const asset: Asset = {
            docType: 'asset',
            ID: assetID,
            color,
            size,
            owner,
            appraisedValue,
        };

        await ctx.stub.putState(
            assetID,
            Buffer.from(stringify(sortKeysRecursive(asset)))
        );

        //  Create an index to enable color-based range queries, e.g. return all blue assets.
        //  An 'index' is a normal key-value entry in the ledger.
        //  The key is a composite key, with the elements that you want to range query on listed first.
        //  In our case, the composite key is based on indexName~color~name.
        //  This will enable very efficient state range queries based on composite keys matching indexName~color~*
        const compositeKey = ctx.stub.createCompositeKey(compositeKeyPrefix, [
            color,
            assetID,
        ]);

        //  Save index entry to world state. Only the key name is needed, no need to store a duplicate copy of the asset.
        //  Note - passing a 'nil' value will effectively delete the key from state, therefore we pass null character as value
        await ctx.stub.putState(compositeKey, Buffer.from([0]));
    }

    // ReadAsset retrieves an asset from the ledger
    @Transaction(false)
    public async ReadAsset(ctx: Context, assetID: string): Promise<Asset> {
        const assetBytes = await ctx.stub.getState(assetID);
        if (assetBytes.length === 0) {
            throw new Error(`Asset ${assetID} does not exist`);
        }
        const asset: Asset = JSON.parse(assetBytes.toString()) as Asset;
        return asset;
    }

    // DeleteAsset removes an asset key-value pair from the ledger
    @Transaction()
    public async DeleteAsset(ctx: Context, assetID: string): Promise<void> {
        const asset = await this.ReadAsset(ctx, assetID);
        await ctx.stub.deleteState(assetID);

        const compositeKey = ctx.stub.createCompositeKey(compositeKeyPrefix, [
            asset.color,
            asset.ID,
        ]);

        // Delete index entry
        await ctx.stub.deleteState(compositeKey);
    }

    // TransferAsset transfers an asset by setting a new owner name on the asset
    @Transaction()
    public async TransferAsset(
        ctx: Context,
        assetID: string,
        newOwner: string
    ): Promise<void> {
        const asset = await this.ReadAsset(ctx, assetID);
        asset.owner = newOwner;
        await ctx.stub.putState(
            assetID,
            Buffer.from(stringify(sortKeysRecursive(asset)))
        );
    }

    // GetAssetsByRange performs a range query based on the start and end keys provided.
    // Read-only function results are not typically submitted to ordering. If the read-only
    // results are submitted to ordering, or if the query is used in an update transaction
    // and submitted to ordering, then the committing peers will re-execute to guarantee that
    // result sets are stable between endorsement time and commit time. The transaction is
    // invalidated by the committing peers if the result set has changed between endorsement
    // time and commit time.
    // Therefore, range queries are a safe option for performing update transactions based on query results.
    @Transaction(false)
    public async GetAssetsByRange(
        ctx: Context,
        startKey: string,
        endKey: string
    ): Promise<Asset[]> {
        const iterator = await ctx.stub.getStateByRange(startKey, endKey);
        const results = await this.constructQueryResponseFromIterator(iterator);

        return results;
    }

    // TransferAssetByColor will transfer assets of a given color to a certain new owner.
    // Uses GetStateByPartialCompositeKey (range query) against color~name 'index'.
    // Committing peers will re-execute range queries to guarantee that result sets are stable
    // between endorsement time and commit time. The transaction is invalidated by the
    // committing peers if the result set has changed between endorsement time and commit time.
    // Therefore, range queries are a safe option for performing update transactions based on query results.
    // Example: GetStateByPartialCompositeKey/RangeQuery
    @Transaction()
    public async TransferAssetByColor(
        ctx: Context,
        color: string,
        newOwner: string
    ): Promise<void> {
        // Execute a key range query on all keys starting with 'color'
        const iterator = await ctx.stub.getStateByPartialCompositeKey(
            compositeKeyPrefix,
            [color]
        );

        let result = await iterator.next();

        while (!result.done) {
            const key = result.value.key;
            const keyParts = ctx.stub.splitCompositeKey(key).attributes;

            if (keyParts.length >= 2) {
                const assetID = keyParts[1];
                const asset = await this.ReadAsset(ctx, assetID);
                asset.owner = newOwner;
                await ctx.stub.putState(
                    assetID,
                    Buffer.from(stringify(sortKeysRecursive(asset)))
                );
            }

            result = await iterator.next();
        }
        await iterator.close();
    }

    // QueryAssetsByOwner queries for assets based on the owners name.
    // This is an example of a parameterized query where the query logic is baked into the chaincode,
    // and accepting a single query parameter (owner).
    // Only available on state databases that support rich query (e.g. CouchDB)
    // Example: Parameterized rich query
    @Transaction(false)
    public async QueryAssetsByOwner(
        ctx: Context,
        owner: string
    ): Promise<Asset[]> {
        const queryString = JSON.stringify({
            selector: {
                docType: 'asset',
                owner: owner,
            },
        });
        return await this.getQueryResultForQueryString(ctx, queryString);
    }

    // QueryAssets uses a query string to perform a query for assets.
    // Query string matching state database syntax is passed in and executed as is.
    // Supports ad hoc queries that can be defined at runtime by the client.
    // If this is not desired, follow the QueryAssetsForOwner example for parameterized queries.
    // Only available on state databases that support rich query (e.g. CouchDB)
    // Example: Ad hoc rich query
    @Transaction(false)
    public async QueryAssets(
        ctx: Context,
        queryString: string
    ): Promise<Asset[]> {
        return await this.getQueryResultForQueryString(ctx, queryString);
    }

    // GetAssetsByRangeWithPagination performs a range query based on the start and end key,
    // page size and a bookmark.
    // The number of fetched records will be equal to or lesser than the page size.
    // Paginated range queries are only valid for read only transactions.
    // Example: Pagination with Range Query
    @Transaction(false)
    public async GetAssetsByRangeWithPagination(
        ctx: Context,
        startKey: string,
        endKey: string,
        pageSize: number,
        bookmark: string
    ): Promise<PaginatedQueryResult> {
        const { iterator, metadata } =
            await ctx.stub.getStateByRangeWithPagination(
                startKey,
                endKey,
                pageSize,
                bookmark
            );
        const records = await this.constructQueryResponseFromIterator(iterator);

        return {
            records,
            fetchedRecordsCount: metadata.fetchedRecordsCount,
            bookmark: metadata.bookmark,
        };
    }

    // QueryAssetsWithPagination uses a query string, page size and a bookmark to perform a query
    // for assets. Query string matching state database syntax is passed in and executed as is.
    // The number of fetched records would be equal to or lesser than the specified page size.
    // Supports ad hoc queries that can be defined at runtime by the client.
    // If this is not desired, follow the QueryAssetsForOwner example for parameterized queries.
    // Only available on state databases that support rich query (e.g. CouchDB)
    // Paginated queries are only valid for read only transactions.
    // Example: Pagination with Ad hoc Rich Query
    @Transaction(false)
    public async QueryAssetsWithPagination(
        ctx: Context,
        queryString: string,
        pageSize: number,
        bookmark: string
    ): Promise<PaginatedQueryResult> {
        return await this.getQueryResultForQueryStringWithPagination(
            ctx,
            queryString,
            pageSize,
            bookmark
        );
    }

    // GetAssetHistory returns the chain of custody for an asset since issuance.
    @Transaction(false)
    public async GetAssetHistory(
        ctx: Context,
        assetID: string
    ): Promise<HistoryQueryResult[]> {
        const iterator = await ctx.stub.getHistoryForKey(assetID);
        const results: HistoryQueryResult[] = [];
        let result = await iterator.next();

        while (!result.done) {
            const historyRecord = result.value;
            let asset: Asset | null = null;
            if (historyRecord.value.length > 0) {
                asset = JSON.parse(historyRecord.value.toString()) as Asset;
            }
            // Convert the timestamp to a Date object.
            const seconds = historyRecord.timestamp.seconds;
            const nanos = historyRecord.timestamp.nanos;

            const secondsNumber =
                typeof seconds === 'number' ? seconds : Number(seconds.low);
            const timestamp = new Date(secondsNumber * 1000 + nanos / 1e6);
            results.push({
                txId: historyRecord.txId,
                timestamp: timestamp,
                record: asset,
                isDelete: historyRecord.isDelete,
            });
            result = await iterator.next();
        }
        await iterator.close();

        return results;
    }

    // AssetExists returns true when asset with given ID exists in the ledger.
    @Transaction(false)
    @Returns('boolean')
    public async AssetExists(ctx: Context, assetID: string): Promise<boolean> {
        const assetBytes = await ctx.stub.getState(assetID);
        return !!(assetBytes.length > 0);
    }

    // InitLedger creates the initial set of assets in the ledger.
    @Transaction()
    public async InitLedger(ctx: Context): Promise<void> {
        const assets: Asset[] = [
            {
                docType: 'asset',
                ID: 'asset1',
                color: 'blue',
                size: 5,
                owner: 'Tomoko',
                appraisedValue: 300,
            },
            {
                docType: 'asset',
                ID: 'asset2',
                color: 'red',
                size: 5,
                owner: 'Brad',
                appraisedValue: 400,
            },
            {
                docType: 'asset',
                ID: 'asset3',
                color: 'green',
                size: 10,
                owner: 'Jin Soo',
                appraisedValue: 500,
            },
            {
                docType: 'asset',
                ID: 'asset4',
                color: 'yellow',
                size: 10,
                owner: 'Max',
                appraisedValue: 600,
            },
            {
                docType: 'asset',
                ID: 'asset5',
                color: 'black',
                size: 15,
                owner: 'Adriana',
                appraisedValue: 700,
            },
            {
                docType: 'asset',
                ID: 'asset6',
                color: 'white',
                size: 15,
                owner: 'Michel',
                appraisedValue: 800,
            },
        ];
        for (const asset of assets) {
            await this.CreateAsset(
                ctx,
                asset.ID,
                asset.color,
                asset.size,
                asset.owner,
                asset.appraisedValue
            );
        }
    }

    // ---------------------- Helper Functions -----------------------

    // constructQueryResponseFromIterator constructs a slice of assets from the resultsIterator
    private async constructQueryResponseFromIterator(
        iterator: Iterators.StateQueryIterator
    ): Promise<Asset[]> {
        const allResults: Asset[] = [];
        let result = await iterator.next();
        while (!result.done) {
            const strValue = Buffer.from(result.value.value).toString('utf8');
            let record: Asset;
            try {
                record = JSON.parse(strValue) as Asset;
            } catch (err) {
                record = {} as Asset;
            }
            allResults.push(record);
            result = await iterator.next();
        }
        await iterator.close();

        return allResults;
    }

    // getQueryResultForQueryString executes the passed in query string.
    // The result set is built and returned as a byte array containing the JSON results.
    private async getQueryResultForQueryString(
        ctx: Context,
        queryString: string
    ): Promise<Asset[]> {
        const iterator = await ctx.stub.getQueryResult(queryString);
        const results = await this.constructQueryResponseFromIterator(iterator);
        return results;
    }

    // getQueryResultForQueryStringWithPagination executes the passed in query string with
    // pagination info. The result set is built and returned as a byte array containing the JSON results.
    private async getQueryResultForQueryStringWithPagination(
        ctx: Context,
        queryString: string,
        pageSize: number,
        bookmark: string
    ): Promise<PaginatedQueryResult> {
        const { iterator, metadata } =
            await ctx.stub.getQueryResultWithPagination(
                queryString,
                pageSize,
                bookmark
            );
        const records = await this.constructQueryResponseFromIterator(iterator);
        return {
            records,
            fetchedRecordsCount: metadata.fetchedRecordsCount,
            bookmark: metadata.bookmark,
        };
    }
}
