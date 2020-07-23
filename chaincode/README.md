[//]: # (SPDX-License-Identifier: CC-BY-4.0)

# Sample smart contracts

This folder contains example smart contracts that are used by the Hyperledger Fabric documentation and by other samples

|  **Smart Contract** | **Description** | **Tutorial** | **Languages** |
| -----------|------------------------------|----------|---------|
| [fabcar](fabcar) | Basic smart contract that allows you to add and change data on the ledger using the Fabric contract API. Also contains an example on how to run chaincode as an external service. | [Writing your first application](https://hyperledger-fabric.readthedocs.io/en/master/write_first_app.html) | Go, Java, JavaScript, Typescript |
| [marbles02](marbles02) | Sample that demonstrates how to deploy an index and use rich queries when you are using CouchDB as your state database. | [Using CouchDB](https://hyperledger-fabric.readthedocs.io/en/master/couchdb_tutorial.html) | Go |
| [marbles02_private](marbles02_private) | Sample that demonstrates the use of private data collections. | [Private data tutorial](https://hyperledger-fabric.readthedocs.io/en/master/private_data_tutorial.html) | Go |
| [marbles_transfer](marbles_transfer) | Smart contract that demonstrates the use of private data, state based endorsement, and access control to securely transfer an asset between two parties | [Marbles private asset transfer scenario](marbles_transfer/README.md) | Go |
| [abac](abac) | Smart contract that restricts access to the chaincode namespace using Attribute Based Access Control. | | Go|
| [sacc](sacc) | Simple asset chaincode that interacts with the ledger using the low-level APIs provided by the Fabric Chaincode Shim API. | [Chaincode for developers](https://hyperledger-fabric.readthedocs.io/en/master/chaincode4ade.html) | Go |
| [abstore](abstore) | Basic smart contract that allows you to transfer data (from A to B) using the Fabric contract API. |  | Go, Java, JavaScript |

## License <a name="license"></a>

Hyperledger Project source code files are made available under the Apache
License, Version 2.0 (Apache-2.0), located in the [LICENSE](LICENSE) file.
Hyperledger Project documentation files are made available under the Creative
Commons Attribution 4.0 International License (CC-BY-4.0), available at http://creativecommons.org/licenses/by/4.0/.
