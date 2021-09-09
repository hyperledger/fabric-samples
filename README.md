# Fabric REST sample

Prototype sample REST server to demonstrate good Fabric Node SDK practices for parts of [FAB-18511](https://jira.hyperledger.org/browse/FAB-18511)

The intention is to deliver the sample to the [asset-transfer-basic/rest-api-typescript directory of the fabric-samples repository](https://github.com/hyperledger/fabric-samples/tree/main/asset-transfer-basic)

See the [sample readme for usage intructions](asset-transfer-basic/rest-api-typescript/README.md)

## Overview

The primary aim of this sample is to show how to write a long running client application using the Fabric Node SDK, i.e. without reconnecting for each transaction

It should also show:

- basic transaction retries
- long running event handling
- requests from multiple users

## Next steps

### Handling transaction errors

Should transactions be retried _unless they fail_ with specific errors, e.g. duplicate transaction (the current implementation)?

**or**

Should transactions be retried _when they fail_ with specific errors?

Also, transactions are currently only retried if they are successfully endorsed- does that seem reasonable?

If the transaction failed because of MVCC_READ_CONFLICT, is a chance that it could pass when retrying? (Is MVCC_READ_CONFLICT an endorsement error?)

### Handling other errors

Need to make sure it's clear what went wrong and fail properly it necessary, for example when starting without a redis instance

### Finish off unit tests

Coverage is looking much better now but there are a few more todos

### More comments

Need to document what's going on and why, especially in the fabric.ts file!

### Feedback

- More people trying out the sample (and ideally trying to break it a bit!)
- Code review to merge sample into fabric-samples

### Known problems

See [issues](https://github.com/hyperledgendary/fabric-rest-sample/issues)
