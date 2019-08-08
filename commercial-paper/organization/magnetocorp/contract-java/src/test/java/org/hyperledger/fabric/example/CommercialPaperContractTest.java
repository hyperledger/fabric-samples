/*
SPDX-License-Identifier: Apache-2.0
*/

package org.hyperledger.fabric.example;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.hyperledger.fabric.DevRouter;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public final class CommercialPaperContractTest {

    DevRouter devRouter;

    @BeforeAll
    public void scanContracts() {
        this.devRouter = DevRouter.getDevRouter();
    }

    ChaincodeStub newStub(String[] args) {
        ChaincodeStub stub = mock(ChaincodeStub.class);
        List<String> allargs = new ArrayList<String>();
        Collections.addAll(allargs, args);
        when(stub.getArgs()).thenReturn(allargs.stream().map(String::getBytes).collect(Collectors.toList()));
        when(stub.getStringArgs()).thenReturn(allargs);

        return stub;
    }

}