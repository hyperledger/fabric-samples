/*
 * Copyright contributors to the Hyperledgendary Full Stack Asset Transfer Guide project
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import { ChaincodeEvent, checkpointers, Gateway } from '@hyperledger/fabric-gateway';
import * as path from 'path';
import { CHAINCODE_NAME, CHANNEL_NAME } from '../config';
import { Asset } from '../contract';
import { assertDefined } from '../utils';
import { TextDecoder } from 'util';

const axios = require('axios');
const utf8Decoder = new TextDecoder();

const checkpointFile = path.resolve(process.env.CHECKPOINT_FILE ?? 'checkpoint.json');

const startBlock = BigInt(0);

// Webhook / bot display names for create
const createUsername = 'King Conga';
const createAvatar = 'https://avatars.githubusercontent.com/u/49026922?s=200&v=4';

const transferUsername = createUsername;
const transferAvatar = createAvatar;

const deleteUsername = createUsername;
const deleteAvatar = createAvatar;

export default async function main(gateway: Gateway): Promise<void> {
    const webhookURL = assertDefined(process.env['WEBHOOK_URL'], () => { return 'WEBHOOK_URL is not defined in the env' });
    const network = gateway.getNetwork(CHANNEL_NAME);
    const checkpointer = await checkpointers.file(checkpointFile);

    console.log(`Connecting to #discord webhook ${webhookURL}`);
    console.log(`Starting event discording from block ${checkpointer.getBlockNumber() ?? startBlock}`);
    console.log('Last processed transaction ID within block:', checkpointer.getTransactionId());

    const events = await network.getChaincodeEvents(CHAINCODE_NAME, {
        checkpoint: checkpointer,
        startBlock, // Used only if there is no checkpoint block number
    });

    try {
        for await (const event of events) {
            await discord(webhookURL, event);

            await checkpointer.checkpointChaincodeEvent(event)

            // Slow down the event iterator to avoid rate limitations imposed by discord.
            // This could be improved to catch the "try again" error from discord and resubmit the event before
            // checkpointing the iterator.
            await new Promise(resolve => setTimeout(resolve, 1000));
        }
    } finally {
        events.close();
    }
}

// Relay a quick message to the discord webhook to indicate the transaction has been processed.
async function discord(webhookURL: string, event: ChaincodeEvent): Promise<void> {

    const asset = parseJson(event.payload);
    console.log(`\n<-- Chaincode event received: ${event.eventName}: `, asset);

    // const message = boringLogMessage(event, asset);
    const message = splashyShoutMessage(event, asset);

    deliverMessage(webhookURL, message);
}

// Send an event to a discord webhook.
async function deliverMessage(webhookURL: string, message: any): Promise<void> {
    console.log('--> Sending to discord webhook: ' + webhookURL);
    console.log(JSON.stringify(message));

    try {
        await axios.post(webhookURL, message);

    } catch (error) {
        console.log(error);
    }
}

function boringLogMessage(event: ChaincodeEvent, asset: Asset): any {
    const owner = ownerNickname(asset);
    const text = format(event, asset, owner);

    return {
        username: 'Ledger Troll',
        // avatar_url: avatarURL,
        content: text,
    }
}

function splashyShoutMessage(event: ChaincodeEvent, asset: Asset): any {

    const owner:any = JSON.parse(asset.Owner);

    if (event.eventName == 'CreateAsset') {
        return {
            username: createUsername,
            avatar_url: createAvatar,
            content: `${bold(owner.user)} has caught a wild ${bold(asset.ID)}!` + getRandomEmoji(),
            embeds: [
                {
                    title: `${owner.org}`,
                    image: {
                        // an actual conga comic (sometimes png and sometimes jpg)
                        // url: `https://congacomic.github.io/assets/img/blockheight-${offset}.png`
                        url: `https://github.com/hyperledgendary/full-stack-asset-transfer-guide/blob/main/applications/conga-cards/assets/${asset.ID}.png?raw=true`
                    }
                }
            ],
        };
    }

    if (event.eventName == 'TransferAsset') {
        return {
            username: transferUsername,
            avatar_url: transferAvatar,
            content: `${bold(owner.user)} is now the owner of ${bold(asset.ID)}: ✈️ ${snippet(JSON.stringify(asset, null, 2))}`,
        };
    }

    if (event.eventName == 'DeletaAsset') {
        return {
            username: deleteUsername,
            avatar_url: deleteAvatar,
            content: `${bold(asset.ID)} ran away from ${bold(owner.user)}! 😮`,
        };
    }

    return {};
}

function format(event: ChaincodeEvent, asset: Asset, owner: string): string {
    return `${quote(event.transactionId)} ${italic(event.eventName)}(${bold(asset.ID)}, ${owner})`;
}

function parseJson(jsonBytes: Uint8Array): Asset {
    const json = utf8Decoder.decode(jsonBytes);
    return JSON.parse(json);
}

function quote(s: string): string {
    return `\`${s}\``
}

function italic(s: string): string {
    return `_${s}_`;
}

function bold(s: string) {
     return `**${s}**`;
}

function snippet(s: string) {
   return "```" + s + "```";
}

function ownerNickname(asset: Asset): string {
    const owner:any = JSON.parse(asset.Owner);

    return `${owner.org}, ${owner.user}`;
}

// https://github.com/discord/discord-example-app/blob/main/utils.js#L43
// Simple method that returns a random emoji from list
function getRandomEmoji(): string {
  const emojiList = ['😭','😄','😌','🤓','😎','😤','🤖','😶‍🌫', '🌏','📸','💿','👋','🌊','✨'];
  return emojiList[Math.floor(Math.random() * emojiList.length)];
}


