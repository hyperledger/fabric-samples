/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import { Identity } from '@hyperledger/fabric-gateway';
import { common, ledger, msp, peer } from '@hyperledger/fabric-protos';
import { assertDefined, cache } from './utils';

export interface Block {
    getNumber(): bigint;
    getTransactions(): Transaction[];
    toProto(): common.Block;
}

export interface Transaction {
    getChannelHeader(): common.ChannelHeader;
    getCreator(): Identity;
    getValidationCode(): number;
    isValid(): boolean;
    getNamespaceReadWriteSets(): NamespaceReadWriteSet[];
    toProto(): common.Payload;
}

export interface NamespaceReadWriteSet {
    getNamespace(): string;
    getReadWriteSet(): ledger.rwset.kvrwset.KVRWSet;
    toProto(): ledger.rwset.NsReadWriteSet;
}

export function parseBlock(block: common.Block): Block {
    const validationCodes = getTransactionValidationCodes(block);
    const header = assertDefined(block.getHeader(), 'Missing block header');

    return {
        getNumber: () => BigInt(header.getNumber()),
        getTransactions: cache(
            () => getPayloads(block)
                .map((payload, i) => parsePayload(payload, validationCodes[i]))
                .filter(payload => payload.isEndorserTransaction())
                .map(newTransaction)
        ),
        toProto: () => block,
    };
}

interface Payload {
    getChannelHeader(): common.ChannelHeader;
    getEndorserTransaction(): EndorserTransaction;
    getSignatureHeader(): common.SignatureHeader;
    getTransactionValidationCode(): number;
    isEndorserTransaction(): boolean;
    isValid(): boolean;
    toProto(): common.Payload;
}

interface EndorserTransaction {
    getReadWriteSets(): ReadWriteSet[];
    toProto(): peer.Transaction;
}

interface ReadWriteSet {
    getNamespaceReadWriteSets(): NamespaceReadWriteSet[];
    toProto(): ledger.rwset.TxReadWriteSet;
}

function parsePayload(payload: common.Payload, statusCode: number): Payload {
    const cachedChannelHeader = cache(() => getChannelHeader(payload));
    const isEndorserTransaction = (): boolean => cachedChannelHeader().getType() === common.HeaderType.ENDORSER_TRANSACTION;

    return {
        getChannelHeader: cachedChannelHeader,
        getEndorserTransaction: () => {
            if (!isEndorserTransaction()) {
                throw new Error(`Unexpected payload type: ${cachedChannelHeader().getType()}`);
            }
            const transaction = peer.Transaction.deserializeBinary(payload.getData_asU8());
            return parseEndorserTransaction(transaction);
        },
        getSignatureHeader: cache(() => getSignatureHeader(payload)),
        getTransactionValidationCode: () => statusCode,
        isEndorserTransaction,
        isValid: () => statusCode === peer.TxValidationCode.VALID,
        toProto: () => payload,
    };
}

function parseEndorserTransaction(transaction: peer.Transaction): EndorserTransaction {
    return {
        getReadWriteSets: cache(
            () => getChaincodeActionPayloads(transaction)
                .map(payload => assertDefined(payload.getAction(), 'Missing chaincode endorsed action'))
                .map(endorsedAction => endorsedAction.getProposalResponsePayload_asU8())
                .map(bytes => peer.ProposalResponsePayload.deserializeBinary(bytes))
                .map(responsePayload => peer.ChaincodeAction.deserializeBinary(responsePayload.getExtension_asU8()))
                .map(chaincodeAction => chaincodeAction.getResults_asU8())
                .map(bytes => ledger.rwset.TxReadWriteSet.deserializeBinary(bytes))
                .map(parseReadWriteSet)
        ),
        toProto: () => transaction,
    };
}

function newTransaction(payload: Payload): Transaction {
    const transaction = payload.getEndorserTransaction();

    return {
        getChannelHeader: () => payload.getChannelHeader(),
        getCreator: () => {
            const creatorBytes = payload.getSignatureHeader().getCreator_asU8();
            const creator = msp.SerializedIdentity.deserializeBinary(creatorBytes);
            return {
                mspId: creator.getMspid(),
                credentials: creator.getIdBytes_asU8(),
            };
        },
        getNamespaceReadWriteSets: () => transaction.getReadWriteSets()
            .flatMap(readWriteSet => readWriteSet.getNamespaceReadWriteSets()),
        getValidationCode: () => payload.getTransactionValidationCode(),
        isValid: () => payload.isValid(),
        toProto: () => payload.toProto(),
    };
}

function parseReadWriteSet(readWriteSet: ledger.rwset.TxReadWriteSet): ReadWriteSet {
    return {
        getNamespaceReadWriteSets: () => {
            if (readWriteSet.getDataModel() !== ledger.rwset.TxReadWriteSet.DataModel.KV) {
                throw new Error(`Unexpected read/write set data model: ${readWriteSet.getDataModel()}`);
            }

            return readWriteSet.getNsRwsetList().map(parseNamespaceReadWriteSet);
        },
        toProto: () => readWriteSet,
    };
}

function parseNamespaceReadWriteSet(nsReadWriteSet: ledger.rwset.NsReadWriteSet): NamespaceReadWriteSet {
    return {
        getNamespace: () => nsReadWriteSet.getNamespace(),
        getReadWriteSet: cache(
            () => ledger.rwset.kvrwset.KVRWSet.deserializeBinary(nsReadWriteSet.getRwset_asU8())
        ),
        toProto: () => nsReadWriteSet,
    };
}

function getTransactionValidationCodes(block: common.Block): Uint8Array {
    const metadata = assertDefined(block.getMetadata(), 'Missing block metadata');
    return metadata.getMetadataList_asU8()[common.BlockMetadataIndex.TRANSACTIONS_FILTER];
}

function getPayloads(block: common.Block): common.Payload[] {
    return (block.getData()?.getDataList_asU8() ?? [])
        .map(bytes => common.Envelope.deserializeBinary(bytes))
        .map(envelope => envelope.getPayload_asU8())
        .map(bytes => common.Payload.deserializeBinary(bytes));
}

function getChannelHeader(payload: common.Payload): common.ChannelHeader {
    const header = assertDefined(payload.getHeader(), 'Missing payload header');
    return common.ChannelHeader.deserializeBinary(header.getChannelHeader_asU8());
}

function getSignatureHeader(payload: common.Payload): common.SignatureHeader {
    const header = assertDefined(payload.getHeader(), 'Missing payload header');
    return common.SignatureHeader.deserializeBinary(header.getSignatureHeader_asU8());
}

function getChaincodeActionPayloads(transaction: peer.Transaction): peer.ChaincodeActionPayload[] {
    return transaction.getActionsList()
        .map(transactionAction => transactionAction.getPayload_asU8())
        .map(bytes => peer.ChaincodeActionPayload.deserializeBinary(bytes));
}
