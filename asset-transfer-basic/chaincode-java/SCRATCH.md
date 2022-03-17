


# Scratch notes - Ignore - ... `fabric-cli` redux

`fabric [options] peer <group> <command> [parameters]`

```
fabric  => network 
peer    => implicit (from env/context)
channel => implicit (from env/context)
group   => chaincode 
[params] => --param=value or NETWORK_$GROUP_$COMMAND_$PARAM=value from env 
```

```shell
network chaincode package       <folder-path> <bundle-path> 
network chaincode id            <bundle-path> 
network chaincode install       <bundle-path> 
network chaincode approve       <name> <id> 
network chainocde commit        <name> 
```

```shell
network chaincode list 
network chaincode delete        <name> 
network chaincode describe      <name> 
network chaincode invoke        <name> <payload> 
network chaincode query         <name> <payload> 
```

meta / fictitious targets:
```
network chaincode launch        <name> <CC_IMAGE> 
network chaincode deploy        <name> <folder-path>    # package, install, LAUNCH, approve, commit 
```


ordinal position args vs. named parameters vs. env overrides
```shell
network chaincode package asset-transfer my-chaincode.tar.gz 

network cc package --name=asset-transfer            (or NETWORK_CHAINCODE_PACKAGE_NAME=asset-transfer)
network cc package --name=                          (or NETWORK_${GROUP}_${COMMAND}_${PARAM}=<value>)


```


