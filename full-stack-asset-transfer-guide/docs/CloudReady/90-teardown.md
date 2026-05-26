# Teardown 

<== [PREV: Go Bananas](40-bananas.md)

---


## Conga Test 

## Final Exercise: Build Your (Social) Network 

- Write down your email address / linkedin profile / # discord handle / etc. on the back of your Conga Card.
- Use the fabric-ca-client to enroll the user / password listed on your Conga Card.
- `create()` an NFT asset for your Conga Card on the shared workshop blockchain.
- Go around the room and meet people attending the workshop.
- Exchange your Conga Cards with workshop participants, issuing a `transfer()` transaction on the shared ledger.  
- When you exchange conga cards, record the new owners (both on the back of the card and the ledger!)
- Bring your Conga Trading Cards home, and reach out to all your new friends and colleagues. 


## Thank You!

- Discord: [#dublin-workshop](https://discord.gg/hyperledger)
- Discord: [#fabric-kubernetes](https://discord.gg/hyperledger)
- Thank you for attending the workshop!
- Safe travels home, and happy coding.


## Workshop Teardown:

- Shut down the network, but leave KIND / MP / etc running: 
```shell

just cloud-network-down 

```

- Shut down KIND, the private container registry, and Nginx: 
```shell

just unkind

```

- Shut down the Multipass VM, if running:  
```shell

multipass delete fabric-dev
multipass purge

```

## Cloud VMs

- Terminate EC2 / IKS Instances (Workshop systems will be deleted after the event)

