/**
 * Copyright 2017 Kapil Sachdeva All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

declare enum Status {
  UNKNOWN = 0,
  SUCCESS = 200,
  BAD_REQUEST = 400,
  FORBIDDEN = 403,
  NOT_FOUND = 404,
  REQUEST_ENTITY_TOO_LARGE = 413,
  INTERNAL_SERVER_ERROR = 500,
  SERVICE_UNAVAILABLE = 503
}

type ChaicodeType = "golang" | "car" | "java";

interface ProtoBufObject {
  toBuffer(): Buffer;
}

interface KeyOpts {
  ephemeral: boolean;
}

interface ConnectionOptions {

}

interface ConfigSignature extends ProtoBufObject {
  signature_header: Buffer;
  signature: Buffer;
}

interface ICryptoKey {
  getSKI(): string;
  isSymmetric(): boolean;
  isPrivate(): boolean;
  getPublicKey(): ICryptoKey;
  toBytes(): string;
}

interface ICryptoKeyStore {
  getKey(ski: string): Promise<string>;
  putKey(key: ICryptoKey): Promise<ICryptoKey>;
}

interface IKeyValueStore {
  getValue(name: string): Promise<string>;
  setValue(name: string, value: string): Promise<string>;
}

interface IdentityFiles {
  privateKey: string;
  signedCert: string;
}

interface IdentityPEMs {
  privateKeyPEM: string;
  signedCertPEM: string;
}

interface UserOptions {
  username: string;
  mspid: string;
  cryptoContent: IdentityFiles | IdentityPEMs;
}

interface ICryptoSuite {
  decrypt(key: ICryptoKey, cipherText: Buffer, opts: any): Buffer;
  deriveKey(key: ICryptoKey): ICryptoKey;
  encrypt(key: ICryptoKey, plainText: Buffer, opts: any): Buffer;
  getKey(ski: string): Promise<ICryptoKey>;
  generateKey(opts: KeyOpts): Promise<ICryptoKey>;
  hash(msg: string, opts: any): string;
  importKey(pem: string, opts: KeyOpts): ICryptoKey | Promise<ICryptoKey>;
  sign(key: ICryptoKey, digest: Buffer): Buffer;
  verify(key: ICryptoKey, signature: Buffer, digest: Buffer): boolean;
}

interface ChannelRequest {
  name: string;
  orderer: Orderer;
  envelope?: Buffer;
  config?: Buffer;
  txId?: TransactionId;
  signatures: ConfigSignature[];
}

interface TransactionRequest {
  proposalResponses: ProposalResponse[];
  proposal: Proposal;
}

interface BroadcastResponse {
  status: string;
}

interface IIdentity {
  serialize(): Buffer;
  getMSPId(): string;
  isValid(): boolean;
  getOrganizationUnits(): string;
  verify(msg: Buffer, signature: Buffer, opts: any): boolean;
}

interface ISigningIdentity {
  sign(msg: Buffer, opts: any): Buffer;
}

interface ChaincodeInstallRequest {
  targets: Peer[];
  chaincodePath: string;
  chaincodeId: string;
  chaincodeVersion: string;
  chaincodePackage?: Buffer;
  chaincodeType?: ChaicodeType;
}

interface ChaincodeInstantiateUpgradeRequest {
  targets?: Peer[];
  chaincodeType?: string;
  chaincodeId: string;
  chaincodeVersion: string;
  txId: TransactionId;
  fcn?: string;
  args?: string[];
  'endorsement-policy'?: any;
}

interface ChaincodeInvokeRequest {
  targets?: Peer[];
  chaincodeId: string;
  txId: TransactionId;
  fcn?: string;
  args: string[];
}

interface ChaincodeQueryRequest {
  targets?: Peer[];
  chaincodeId: string;
  txId: TransactionId;
  fcn?: string;
  args: string[];
}

interface ChaincodeInfo {
  name: string;
  version: string;
  path: string;
  input: string;
  escc: string;
  vscc: string;
}

interface ChannelInfo {
  channel_id: string;
}

interface ChaincodeQueryResponse {
  chaincodes: ChaincodeInfo[];
}

interface ChannelQueryResponse {
  channels: ChannelInfo[];
}

interface OrdererRequest {
  txId: TransactionId;
}

interface JoinChannelRequest {
  txId: TransactionId;
  targets: Peer[];
  block: Buffer;
}

interface ResponseObject {
  status: Status;
  message: string;
  payload: Buffer;
}

interface Proposal {
  header: ByteBuffer;
  payload: ByteBuffer;
  extension: ByteBuffer;
}

interface Header {
  channel_header: ByteBuffer;
  signature_header: ByteBuffer;
}

interface ProposalResponse {
  version: number;
  timestamp: Date;
  response: ResponseObject;
  payload: Buffer;
  endorsement: any;
}

type ProposalResponseObject = [Array<ProposalResponse>, Proposal, Header];

declare class Orderer {
}

declare class Peer {
  setName(name: string): void;
  getName(): string;
}

declare class EventHub {
  connect(): void;
  disconnect(): void;
  getPeerAddr(): string;
  setPeerAddr(url: string, opts: ConnectionOptions): void;
  isconnected(): boolean;
  registerBlockEvent(onEvent: (b: any) => void, onError?: (err: Error) => void): number;
  registerTxEvent(txId: string, onEvent: (txId: any, code: string) => void, onError?: (err: Error) => void): void;
  unregisterTxEvent(txId: string): void;
}

declare class Channel {
  initialize(): Promise<void>;
  addOrderer(orderer: Orderer): void;
  addPeer(peer: Peer): void;
  getGenesisBlock(request: OrdererRequest): Promise<any>;
  getChannelConfig(): Promise<any>;
  joinChannel(request: JoinChannelRequest): Promise<ProposalResponse>;
  sendInstantiateProposal(request: ChaincodeInstantiateUpgradeRequest): Promise<ProposalResponseObject>;
  sendTransactionProposal(request: ChaincodeInvokeRequest): Promise<ProposalResponseObject>;
  sendTransaction(request: TransactionRequest): Promise<BroadcastResponse>;
  queryByChaincode(request: ChaincodeQueryRequest): Promise<Buffer[]>;
  queryBlock(blockNumber: number, target: Peer): Promise<any>;
  queryTransaction(txId: string, target: Peer): Promise<any>;
  queryInstantiatedChaincodes(target: Peer): Promise<ChaincodeQueryResponse>;
  queryInfo(target: Peer): Promise<any>;
  getOrderers(): Orderer[];
  getPeers(): Peer[];
}

declare abstract class BaseClient {
  static setLogger(logger: any): void;
  static addConfigFile(path: string): void;
  static getConfigSetting(name: string, default_value?: any): any;
  static newCryptoSuite(): ICryptoSuite;
  static newCryptoKeyStore(obj?: { path: string }): ICryptoKeyStore;
  static newDefaultKeyValueStore(obj?: { path: string }): Promise<IKeyValueStore>;
  setCryptoSuite(suite: ICryptoSuite): void;
  getCryptoSuite(): ICryptoSuite;
}

declare class TransactionId {
  getTransactionID(): string;
}

interface UserConfig {
  enrollmentID: string;
  name: string
  roles?: string[];
  affiliation?: string;
}

declare class User {
  isEnrolled(): boolean;
  getName(): string;
  getRoles(): string[];
  setRoles(roles: string[]): void;
  getAffiliation(): string;
  setAffiliation(affiliation: string): void;
  getIdentity(): IIdentity;
  getSigningIdentity(): ISigningIdentity;
  setCryptoSuite(suite: ICryptoSuite): void;
  setEnrollment(privateKey: ICryptoKey, certificate: string, mspId: string): Promise<void>;
}

declare class Client extends BaseClient {
  isDevMode(): boolean;
  getUserContext(name: string, checkPersistence: boolean): Promise<User> | User;
  setUserContext(user: User, skipPersistence?: boolean): Promise<User>;
  setDevMode(mode: boolean): void;
  newOrderer(url: string, opts: ConnectionOptions): Orderer;
  newChannel(name: string): Channel;
  newPeer(url: string, opts: ConnectionOptions): Peer;
  newEventHub(): EventHub;
  newTransactionID(): TransactionId;
  extractChannelConfig(envelope: Buffer): Buffer;
  createChannel(request: ChannelRequest): Promise<BroadcastResponse>;
  createUser(opts: UserOptions): Promise<User>;
  signChannelConfig(config: Buffer): ConfigSignature;
  setStateStore(store: IKeyValueStore): void;
  installChaincode(request: ChaincodeInstallRequest): Promise<ProposalResponseObject>;
  queryInstalledChaincodes(target: Peer): Promise<ChaincodeQueryResponse>;
  queryChannels(target: Peer): Promise<ChannelQueryResponse>;
}

declare module 'fabric-client' {
  export = Client;
}
