# Asset Transfer SBE - Python

This sample implements the state-based endorsement asset transfer scenario, illustrating the use of the Python contract API to provide a smart contract as a service.

The chaincode in `main.py` is written with the [fabric-chaincode-python](https://github.com/hyperledger/fabric-chaincode-python) library and implements the same transactions as the other language implementations in this sample: `CreateAsset`, `ReadAsset`, `UpdateAsset`, `DeleteAsset`, `TransferAsset` and `AssetExists`.

> **Note on state-based endorsement:** the Java and TypeScript implementations apply a key-level endorsement policy to each asset with `setStateValidationParameter`, so that only the organization owning an asset can endorse updates to it. The `fabric-chaincode-python` shim does not yet expose the equivalent of `setStateValidationParameter`, so this implementation mirrors the SBE data model and the transfer flow but does **not** apply key-level endorsement policies. Until the shim supports it, the endorsement-policy scenario of this sample (updates endorsed only by the owning organization) cannot be demonstrated with this implementation.

## Prerequisites

- Python 3.10 or later
- The Python dependencies listed in `requirements.txt`:

  ```shell
  pip install -r requirements.txt
  ```

## Run the chaincode

The chaincode runs as a [chaincode-as-a-service](https://hyperledger-fabric.readthedocs.io/en/latest/cc_service.html) (CCAAS). Set `CHAINCODE_SERVER_ADDRESS` (e.g. `0.0.0.0:9999`) and `CHAINCODE_ID` in the environment, then run:

```shell
python main.py
```

To run this chaincode on the Fabric test network, see:

- [test-network-nano-bash](../../test-network-nano-bash/README.md) (Bash scripts)
- [End-to-end with the test-network](../../test-network/CHAINCODE_AS_A_SERVICE_TUTORIAL.md) (Docker compose)
- [Debugging chaincode as a service](../../test-network-k8s/docs/CHAINCODE_AS_A_SERVICE.md) (Kube test network)
