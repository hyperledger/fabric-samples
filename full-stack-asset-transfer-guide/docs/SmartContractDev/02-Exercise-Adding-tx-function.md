## Adding a Transaction Function

[PREVIOUS - Getting Started](./01-Exercise-Getting-Started.md) <==>  [NEXT - Test And Debug](./03-Test-And-Debug-Reference.md)

 In this exercise, we're going to add a transaction function to check the appraised value. We're going to write this function as part of the iterative development cycle with external chaincode-as-a-service, so there is no requirement to stop fabric or worry about versions of deployed chaincode. We will simply be updating the chaincode source code, restarting the chaincode service, and testing out the new function.

The function will:

- be a 'read-only' function
- take a given asset id, and an upper and lower value
- return a true/false indication if the appraised value is within the upper/lower values

## Steps

Firstly ensure that you've run the Smart Contract and been able to issue transactions against it. It's also worth making sure that you can stop and restart the code after making some minor changes.

- In the `assetTransfer.ts` file create a new function `ValidateValue` . The `ReadAsset` function is a good one to use as a basis. This is a read-only function and already gets the asset from the ledger.
-  Add an upper and lower value to the parameters of the function
- `ReadAsset` returns the asset directly, look at the `UpdateAsset` function for how to process the data
- Check the value and return true/false depending on if the value is in the bounds.
- If you wish also set an event.

Remember to stop the running code, rebuild it and start again. Remember you can attach the debugger to help track down issues

## Testing

You can invoke this then with similar commands as in Getting Started.

For example to check if the value is between 1000 and 4200, issue something like

```
peer chaincode query -C mychannel -n asset-transfer -c '{"Args":["ValidateValue","001","1000","4200"]}'
```

## Example implementation

A possible implementation would be

```
@Transaction(false)
async ValidateValue(ctx: Context, id: string, lower:number, upper:number): Promise<boolean> {
    const existingAssetBytes = await this.#readAsset(ctx, id);
    const existingAsset = newAsset(unmarshal(existingAssetBytes));

    if (existingAsset.AppraisedValue > lower && existingAsset.AppraisedValue < upper){
        return true;
    } else {
        return false;
    }

}
```
