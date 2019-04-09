
# FabToken Sample Application

This is a Node.js sample application that demonstrates how to perform token operations on
a Fabric network using Fabric Node SDK.

The sample assumes an understanding of the Hyperledger Fabric network (orderers, peers,
and channels) and of Node.js application development, including the use of the Javascript
promise, async and await.

For more information about tokens on Hyperledger Fabric, see
[Using Fabtoken](https://hyperledger-fabric.readthedocs.io/en/latest/token/FabToken.html)

For more information about the Fabric SDK for Node.js, refer to
[Node SDK documentation](https://fabric-sdk-node.github.io/master/index.html)

For more information about the Node SDK TokenClient API, refer to the following:
* [TokenClient API reference](https://fabric-sdk-node.github.io/master/TokenClient.html)
* [FabToken tutorial](https://fabric-sdk-node.github.io/master/tutorial-fabtoken.html)

## Run the sample
You can find the `fabtoken.js` sample application in the `javascript` directory. You will
use this application to create and transfer tokens on a network created using the
`basic-network` sample. First, you need to have an initial setup.

### Setup
You will need to install version 8.9.x of Node.js and download the application dependencies.
* Change to `javascript` directory: `cd javascript`
* Run the following command to install the required packages: `npm install`

Now you can start the network:
* Navigate back to the main `FabToken` directory: `cd ..`
* Start fabric network: `./startFabric.sh`

This command will create a fabric network with 1 peer, an ordering service, one
channel, and two users that our application will use to issue and transfer tokens.

### Run the app right away

The `fabtoken.js` application includes a `demo` method that runs an end to end token flow
with hardcoded parameters.

* Navigate to the `javascript` directory
* Run the command `node fabtoken` without any arguments to run the demo.

You should see the output of the demo in your terminal. The demo uses user1 and user2 of
the basic network to do the following token operations:
* Issue a token worth 100 USD to user1
* Transfer 30 USD from user1 to user2
* Redeem 10 USD as user1 and 30 USD as user2
* Check that user1 has a token worth 60 USD and user2 has no tokens

### Use the sample app to create your own tokens

You can pass arguments to `fabtoken.js` to create your own tokens and follow your own
token flow.

#### Issue tokens

Tokens need to be issued before they can be spent. You can use the command
`node fabtoken issue  <username> <token_type> <quantity>` to issue tokens of any
type and quantity to user1 or user2.

* As an example, the first command issues a token worth 100 US dollars to user1. The
second command issues a token worth 200 Euros to user2:

```
node fabtoken issue user1 USD 100
node fabtoken issue user2 EURO 200
```

#### List tokens

After you issue tokens, you can use the list method to query the tokens that you own. Run
the command `node fabtoken list <username>`. You need to use this command to recover the
tokenIDs that you will need to transfer or redeem your tokens.

* As an example, you can use the command below to list the tokens owned by user1:

```
node fabtoken list user1
```
* The command returns a list of tokens, with the tokenID consisting of a tx_id and
index. You will need to use these values for future commands.

```
[ { id:
    { tx_id: 'c9b1211d9ad809e6ee1b542de6886d8d1d9e1c938d88eff23a3ddb4e8c080e4d',
      index: 0 },
    type: 'USD',
    quantity: '100' }
]
```

*  To list the tokens owned by user2, use the `node fabtoken list user2` command.

```
[ { id:
    { tx_id: 'ab5670d3b20b6247b17a342dd2c5c4416f79db95c168beccb7d32b3dd382e5a5',
      index: 0 },
    type: 'EURO',
    quantity: '200' }
]
```

#### Transfer tokens

Tokens can be transferred between users on a channel using the
`node fabtoken transfer <from_user> <to_user> <quantity> <tx_id> <index>` command.
* `<tx_id>` and `<index>` are the "tx_id" and "index" that you found using the list
command
* `<quantity>` is the quantity to be transferred

Any remaining quantity will be transferred back to the owner by creating a new token with
a new tokenID.
* As an example, the following command transfers 30 dollars from user1 to user2:

```
 node fabtoken transfer user1 user2 30 c9b1211d9ad809e6ee1b542de6886d8d1d9e1c938d88eff23a3ddb4e8c080e4d 0
 ```

You can run the command `node fabtoken list user2` to verify that user2 now owns a new token
worth 30 dollars. You can also run the command `node fabtoken list user1` to verify that
a new token worth 70 dollars now belongs to user1.


#### Redeem tokens

Tokens can be taken out of circulation by being redeemed. Redeemed tokens can no longer
be transfered to any member of the channel. Run the command
`node fabtoken redeem <username> <quantity> <tx_id> <index>` to redeem any tokens
belonging to user1 or user2.
* `<tx_id>` and `<index>` are the "tx_id" and "index" returned from the list command
* `<quantity>` is the quantity to be redeemed

Any remaining quantity will be transferred back to the owner with a new tokenID.
* As an example, the following command redeems 10 Euro's belonging to user2:

```
 node fabtoken redeem user2 10 ab5670d3b20b6247b17a342dd2c5c4416f79db95c168beccb7d32b3dd382e5a5 0
 ```

#### Clean up

If you are finished using the sample application, you can bring down the network and any
accompanying artifacts.

* Change to `fabric-samples/basic-network` directory
* To stop the network, run `./stop.sh`
* To completely remove all incriminating evidence of the network, run `./teardown.sh`

## Understanding the `fabtoken.js` application

You can examine the `fabtoken.js` file to get a better understanding of how the
sample application uses the FabToken APIs.


1. The `createFabricClient` method creates an instance of the fabric-client, and is
used to connect to the components of your network.

2. The `createUsers` method uses the certificates generated by the basic network to
create `admin`, `user1` and `user2` users for the application.

3. To perform token operations, you must create a `TokenClient` instance from a `Client`
object. Make sure the client has set the user context. Below is the code snippet.

```
	// set user context to the client
	await client.setUserContext(user, true);

	// create a TokenClient instance
	const tokenClient = client.newTokenClient(channel, 'localhost:7051');
```

4. The `issue` method creates an issue request and submits the request to issue tokens to
your network.

5. The `list` method submits the request to list tokens of a
given owner. You will need the token IDs returned from this method to transfer or redeem tokens.

6. The `transfer` method creates a transfer request and submits the request to transfer tokens
between users.

7. The `redeem` method creates a redeem request and submits the request to redeem a user's
tokens.