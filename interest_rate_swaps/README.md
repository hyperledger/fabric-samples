# Interest-rate swaps

This is a sample of how interest-rate swaps can be handled on a blockchain using
fabric and state-based endorsement. [State-based endorsement](https://hyperledger-fabric.readthedocs.io/en/release-1.3/endorsement-policies.html#key-level-endorsement)
is a new feature released in Hyperledger Fabric 1.3.

An interest-rate swap is a financial swap traded over the counter. It is a
contractual agreement between two parties, where two parties (A and B) exchange
payments. The amount of individual payments is based on the principal amount of the
swap and an interest rate. The interest rates of the two parties differ. In a
typical scenario, one payment (A to B) is based on a fixed rate set in the
contract. The other payment (B to A) is based on a floating rate. This rate is
defined through a reference rate, such as LIBOR from LSE, and an offset to this
rate.

## Network

We assume organizations of the following roles participate in our network:
 * Parties that want to exchange payments
 * Parties that provide reference rates
 * Auditors that need to audit certain swaps

The chaincode-level endorsement policy is set to require an endorsement from an
auditor as well as an endorsement from any swap participant.

## Data model
We represent a swap on the ledger as a JSON with the following fields:
 * `StartDate` and `EndDate` of the swap
 * `PaymentInterval` - the time interval of the payments
 * `PrincipalAmount` - the principal amount of the swap
 * `FixedRate` - the fixed rate of the swap
 * `FloatingRate` - the floating rate of the swap (offset to the reference rate)
 * `ReferenceRate` - the key name of the KVS pair that holds the reference rate

The key for the swap is a unique identifier combined with a common prefix `swap`
that identifies swap entries in the KVS namespace. Upon creation the key-level
endorsement policy for the swap is set to the participants of the swap and,
potentially, an auditor.

We represent the payment information as a single KVS entry per swap with the
same unique identifier as the swap itself and a common prefix `payment` for payments.
If payments are due, the entry states the amount due. Otherwise, it is "none".
A payment information KVS entry has the same key-level endorsement policy
set as its corresponding swap entry.

We represent the reference rates as a KVS entry per rate with an identifier per
rate and a common prefix for reference rates. The key-level endorsement policy
for a reference rate entry is set to the provider of the corresponding reference
rate, such as LSE for LIBOR.
The reference rate could also be modeled via a separate chaincode, where the
chaincode-level endorsement policies only allows reference rate providers to
create keys.

Taken together, here is an example of the KVS entries involved in a swap:
```
KEY          | VALUE
-------------|-----------------------------------------------------
swap1        | {StartDate: 2018-10-01, ..., ReferenceRate: "libor"}
payment1     | "none"
rr_libor     | 0.27
```
In this example, the swap with ID 1 is represented by the `swap1` and `payment1`
KVS entries. The reference rate is set to `libor`, which will cause the chaincode
to look up the `rr_libor` entry in the KVS to calculate the rate for the
floating leg of the swap.

## Chaincode
The interest-rate swap chaincode provides the following API:
 * `createSwap(swapID, swap_info, partyA, partyB)` - create a new swap with the
   given identifier and swap parameters among the two parties specified. This
   function creates the entry for the swap and the corresponding payment. It
   also sets the key-level endorsement policies for both keys to the participants
   to the swap. In case the swap's principal amount exceeds a certain threshold,
   it adds an auditor to the endorsement policy for the keys.
 * `calculatePayment(swapID)` - calculate the net payment from party A to party
   B and set the payment entry accordingly. If the payment information is negative,
   the payment due flows from B to A. The payment information is calculated based
   on the rates specified in the swap and the principal amount. If the payment
   key is not "none", this function returns an error, indicating that a prior
   payment has not been settled yet.
 * `settlePayment(swapID)` - set the payment entry for the given swap ID to "none".
   This function is supposed to be invoked after the two parties have settled the
   payment off-chain.
 * `setReferenceRate(rrID, value)` - set a given reference rate to a given value.
 * `Init(auditor, threshold, rrProviders...)` - the chaincode namespace is initialized
   with a threshold for the principal amount above which a designated auditor
   needs to be involved as well as a list of reference rate providers and rate IDs.

## Trust model
The state-based endorsement policies used in this sample ensure the following
trust model:
 * All operations related to a specific swap need to be endorsed (at least) by
   the participants to that swap. This includes both creation of a swap, as well
   as calculating the payment information and agreeing that the payments have
   been settled.
 * Operations related to a reference rate need to be endorsed by the provider of
   a reference rate.
 * Under certain circumstances an auditor needs to endorse operations for a swap,
   e.g., if it exceeds a threshold for the principal amount.

The chaincode-level endorsement policy requires at least one potential swap
participant and an auditor. This endorsement policy sets the trust relationship
for creating a swap.

## Sample network

The `network` subdirectory contains scripts that will launch a sample network
and run a swap transaction flow from creation to settlement.

### Prerequisites

The following prerequisites are needed to run this sample:
* Fabric docker images. By default the `network/network.sh` script will look for
  fabric images with the `latest` tag, this can be adapted with the `-i` command
  line parameter of the script.
* A local installation of `configtxgen` and `cryptogen` in the `PATH` environment,
  or included in `fabric-samples/bin` directory.

### Bringing up the network

Navigate to the `network` folder. Run the command `./network.sh up` to bring up
the network. This will spawn docker containers running a network of 3 "regular"
organizations, one auditor organization and one reference rate provider as well
as a solo orderer.

An additional CLI container will run `network/scripts/script.sh` to join the
peers to the `irs` channel and deploy the chaincode. In the init parameters it
supplies the audit threshold, the auditor organization and the reference rate
provider with the corresponding reference rate ID. In the following transactions
it sets the reference rate, creates a swap, calculates payment information for
the swap and marks them as settled afterwards. We will show the corresponding
commands in the following section.

### Transactions

The chaincode is initialized as follows:
```
peer chaincode invoke -o irs-orderer:7050 --isInit -C irs --waitForEvent -n irscc --peerAddresses irs-rrprovider:7051 --peerAddresses irs-partya:7051 --peerAddresses irs-partyb:7051 --peerAddresses irs-partyc:7051 --peerAddresses irs-auditor:7051 -c '{"Args":["init","auditor","1000000","rrprovider","myrr"]}'
```

This sets an auditing threshold of 1M, above which the `auditor` organization
needs to be involved. It also specifies the `myrr` reference rate provided by
the `rrprovider` organization.

To set a reference rate:
```
peer chaincode invoke -o irs-orderer:7050 -C irs --waitForEvent -n irscc --peerAddresses irs-rrprovider:7051 -c '{"Args":["setReferenceRate","myrr","300"]}'
```
Note that the transaction is endorsed by a peer of the organization we have
specified as providing this reference rate in the init parameters.

To create a swap named "myswap":
```
peer chaincode invoke -o irs-orderer:7050 -C irs --waitForEvent -n irscc --peerAddresses irs-partya:7051 --peerAddresses irs-partyb:7051 --peerAddresses irs-auditor:7051 -c '{"Args":["createSwap","myswap","{\"StartDate\":\"2018-09-27T15:04:05Z\",\"EndDate\":\"2018-09-30T15:04:05Z\",\"PaymentInterval\":395,\"PrincipalAmount\":100000,\"FixedRate\":400,\"FloatingRate\":500,\"ReferenceRate\":\"myrr\"}", "partya", "partyb"]}'
```
Note that the transaction is endorsed by both parties that are part of this
swap as well as the auditor. Since the principal amount in this case is lower
than the audit threshold we set as init parameters, no auditor will be required
to endorse changes to the payment info or swap details.

To calculate payment info for "myswap":
```
peer chaincode invoke -o irs-orderer:7050 -C irs --waitForEvent -n irscc --peerAddresses irs-partya:7051 --peerAddresses irs-partyb:7051 -c '{"Args":["calculatePayment","myswap"]}'
```
Note that we target only peers of
party A and party B, since the swap is below the auditing threshold.

To settle payment of "myswap":
```
peer chaincode invoke -o irs-orderer:7050 -C irs --waitForEvent -n irscc `--peerAddresses irs-partya:7051 --peerAddresses irs-partyb:7051 -c '{"Args":["settlePayment","myswap"]}'
```

As an exercise, try to create a new swap above the auditing threshold and see
how validation fails if the auditor is not involved in every operation on the
swap. Also try to calculate payment info before settling a prior payment to a
swap. You can run the commands yourself using the CLI container by issuing the
command ``docker exec -it cli bash``. You will need to set the corresponding
environment variables for the organization issuing the command. You refer to the
`network/scripts/script.sh` file for more information.

## Clean up

When you are finished using the network, you can bring down the docker images
and remove any artifacts by running the command `./network.sh down` from the
`network` folder.
