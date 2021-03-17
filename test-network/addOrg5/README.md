## Adding Org5 to the test network

You can use the `addOrg5.sh` script to add another organization to the Fabric test network. The `addOrg5.sh` script generates the Org5 crypto material, creates an Org5 organization definition, and adds Org5 to a channel on the test network.

You first need to run `./network.sh up createChannel` in the `test-network` directory before you can run the `addOrg5.sh` script.

```
./network.sh up createChannel
cd addOrg5
./addOrg5.sh up
```

If you used `network.sh` to create a channel other than the default `mychannel`, you need pass that name to the `addorg5.sh` script.
```
./network.sh up createChannel -c channel1
cd addOrg5
./addOrg5.sh up -c channel1
```

You can also re-run the `addOrg5.sh` script to add Org5 to additional channels.
```
cd ..
./network.sh createChannel -c channel2
cd addOrg5
./addOrg5.sh up -c channel2
```

For more information, use `./addOrg5.sh -h` to see the `addOrg5.sh` help text.
