## Adding Org4 to the test network

You can use the `addOrg4.sh` script to add another organization to the Fabric test network. The `addOrg4.sh` script generates the Org4 crypto material, creates an Org4 organization definition, and adds Org4 to a channel on the test network.

You first need to run `./network.sh up createChannel` in the `test-network` directory before you can run the `addOrg4.sh` script.

```
./network.sh up createChannel
cd addOrg4
./addOrg4.sh up
```

If you used `network.sh` to create a channel other than the default `mychannel`, you need pass that name to the `addorg4.sh` script.
```
./network.sh up createChannel -c channel1
cd addOrg4
./addOrg4.sh up -c channel1
```

You can also re-run the `addOrg4.sh` script to add Org4 to additional channels.
```
cd ..
./network.sh createChannel -c channel2
cd addOrg4
./addOrg4.sh up -c channel2
```

For more information, use `./addOrg4.sh -h` to see the `addOrg4.sh` help text.
