package org.hyperledger.fabric;

import org.hyperledger.fabric.contract.ContractRouter;
import org.hyperledger.fabric.contract.metadata.MetadataBuilder;

public class DevRouter extends ContractRouter {

    public DevRouter(String[] args) {
        super(args);
        System.out.println("+++DevRouter Starting...... +++");
    }

    public static DevRouter getDevRouter() {
        String args[] = new String[] { "--id", "unittestchaincode" };
        DevRouter dr = new DevRouter(args);
        dr.findAllContracts();
        MetadataBuilder.initialize(dr.getRoutingRegistry(), dr.getTypeRegistry());

        // to output the metadata created
        String metadata = MetadataBuilder.debugString();
        System.out.println(metadata);
        return dr;
    }

}