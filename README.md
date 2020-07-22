[//]: # (SPDX-License-Identifier: CC-BY-4.0)

# Hyperledger Fabric Samples

You can use Fabric samples to get started working with Hyperledger Fabric, explore important Fabric features, and learn how to build applications that can interact with blockchain networks using the Fabric SDKs. To learn more about Hyperledger Fabric, visit the [Fabric documentation](https://hyperledger-fabric.readthedocs.io/en/master).

## Getting started with the Fabric samples

To use the Fabric samples, you need to download the Fabric Docker images and the Fabric CLI tools. First, make sure that you have installed all of the [Fabric prerequisites](https://hyperledger-fabric.readthedocs.io/en/master/prereqs.html). You can then follow the instructions to [Install the Fabric Samples, Binaries, and Docker Images](https://hyperledger-fabric.readthedocs.io/en/master/install.html) in the Fabric documentation. In addition to downloading the Fabric images and tool binaries, the instructions will make you clone the Fabric samples on your local machine.

## Guide to the Fabric samples

You can use the following table to learn more about each sample, and find the corresponding tutorial or documentation.

|  **Sample** | **Description** | **Documentation** |
| -------------|------------------------------|------------------|
| [Fabric test network](test-network) | Get started by deploying a basic Fabric network on your local machine. | [Using the Fabric test network](https://hyperledger-fabric.readthedocs.io/en/master/test_network.html) |
| [Fabcar](fabcar) | Learn how to use the Fabric SDK's to invoke smart contracts from your client applications. | [Writing your first application](https://hyperledger-fabric.readthedocs.io/en/master/write_first_app.html) |
| [Commercial paper](commercial-paper) | Explore a use case in which two organizations use a blockchain network to trade commercial paper. | [Commercial paper tutorial](https://hyperledger-fabric.readthedocs.io/en/master/tutorial/commercial_paper.html) |
| [Interest rate swaps](interest_rate_swaps) | Explore state based endorsement using a financial services use case. | [Setting Key level endorsement policies](https://hyperledger-fabric.readthedocs.io/en/master/endorsement-policies.html#setting-key-level-endorsement-policies) |
| [Off chain data](off_chain_data) | Learn how to use the Peer channel-based event services to build an off chain database for reporting and analytics. | [Peer channel-based event services](https://hyperledger-fabric.readthedocs.io/en/master/peer_event_services.html) |
| [High throughput](high-throughput) | Learn how you can design your smart contracts to process a large volume of transactions. | |
| [Chaincode](chaincode) | A set of sample smart contracts used by other samples and the tutorials in the Fabric documentation. | [Fabric tutorials](https://hyperledger-fabric.readthedocs.io/en/master/tutorials.html) |

### Asset transfer smart contract series

The asset transfer series provides a series of smart contracts and applications that you can use to create and transfer a generic asset using Hyperledger Fabric. However, each sample is built with different smart contract and application APIs in order to demonstrate different Fabric features. The **Basic** sample provides an introduction on how to write smart contracts and how to interact with a Fabric network using the Fabric SDKs. The **Secured agreement** sample demonstrates how to use more advanced capabilities to develop a more realistic transfer scenario.

|  **Smart Contract** | **Description** | **Tutorial** | **Smart contract languages** | **Application languages** |
| -----------|------------------------------|----------|---------|---------|
| [Basic](asset-transfer-basic) | The Basic sample smart contract that allows you to create and transfer an asset by putting data on the ledger and retrieving it. This sample is recommended for new Fabric users. | **Coming soon** | Go, JavaScript, Typescript | JavaScript, Typescript |
| [Ledger queries](asset-transfer-ledger-queries) | The ledger queries sample demonstrates how to deploy an index with your chaincode and issue rich queries when you are using CouchDB as your state database. | **Coming soon** | Go | **Coming soon** |
| [Private data](asset-transfer-private-data) | This sample demonstrates the use of private data collections and how the private data hash can be used to verify an agreement before executing a transfer | **Coming soon** | Go | **Coming soon** |
| [Secured agreement](asset-transfer-secured-agreement) | Smart contract that uses private data, state based endorsement, and access control to establish the ownership of an asset, guarantee immutability, and securely transfer an asset with the consent of both the buyer and the owner, while keeping the asset details private. | [Secured asset transfer in Fabric](https://hyperledger-fabric.readthedocs.io/en/master/secured_private_asset_transfer_tutorial.html)  | Go | **Coming soon** |

The asset transfer series is still a work in progress. Additional smart contract and application languages are being developed. The series will also be integrated with other Fabric samples and the documentation in the near future. For more information, see the public plan for [Fabric samples improvements](https://docs.google.com/presentation/d/1UxK2HH8SrQyZU58MnuDb9hr1nmekst8b/edit#slide=id.g776cdbfb06_0_51).

## License <a name="license"></a>

Hyperledger Project source code files are made available under the Apache
License, Version 2.0 (Apache-2.0), located in the [LICENSE](LICENSE) file.
Hyperledger Project documentation files are made available under the Creative
Commons Attribution 4.0 International License (CC-BY-4.0), available at http://creativecommons.org/licenses/by/4.0/.
