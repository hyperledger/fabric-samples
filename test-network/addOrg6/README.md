## Adding Org6 to the test network

You can use the `addOrg6.sh` script to add another organization to the Fabric test network. The `addOrg6.sh` script generates the Org6 crypto material, creates an Org6 organization definition, and adds Org6 to a channel on the test network.

You first need to run `./network.sh up createChannel` in the `test-network` directory before you can run the `addOrg6.sh` script.

```
./network.sh up createChannel
cd addOrg6
./addOrg6.sh up
```

If you used `network.sh` to create a channel other than the default `mychannel`, you need pass that name to the `addorg6.sh` script.
```
./network.sh up createChannel -c channel1
cd addOrg6
./addOrg6.sh up -c channel1
```

You can also re-run the `addOrg6.sh` script to add Org6 to additional channels.
```
cd ..
./network.sh createChannel -c channel2
cd addOrg6
./addOrg6.sh up -c channel2
```

For more information, use `./addOrg6.sh -h` to see the `addOrg6.sh` help text.
