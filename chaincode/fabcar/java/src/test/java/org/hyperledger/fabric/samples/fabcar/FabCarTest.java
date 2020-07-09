/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.fabcar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

public final class FabCarTest {

    private final class MockKeyValue implements KeyValue {

        private final String key;
        private final String value;

        MockKeyValue(final String key, final String value) {
            super();
            this.key = key;
            this.value = value;
        }

        @Override
        public String getKey() {
            return this.key;
        }

        @Override
        public String getStringValue() {
            return this.value;
        }

        @Override
        public byte[] getValue() {
            return this.value.getBytes();
        }

    }

    private final class MockCarResultsIterator implements QueryResultsIterator<KeyValue> {

        private final List<KeyValue> carList;

        MockCarResultsIterator() {
            super();

            carList = new ArrayList<KeyValue>();

            carList.add(new MockKeyValue("CAR0",
                    "{\"color\":\"blue\",\"make\":\"Toyota\",\"model\":\"Prius\",\"owner\":\"Tomoko\"}"));
            carList.add(new MockKeyValue("CAR1",
                    "{\"color\":\"red\",\"make\":\"Ford\",\"model\":\"Mustang\",\"owner\":\"Brad\"}"));
            carList.add(new MockKeyValue("CAR2",
                    "{\"color\":\"green\",\"make\":\"Hyundai\",\"model\":\"Tucson\",\"owner\":\"Jin Soo\"}"));
            carList.add(new MockKeyValue("CAR7",
                    "{\"color\":\"violet\",\"make\":\"Fiat\",\"model\":\"Punto\",\"owner\":\"Pari\"}"));
            carList.add(new MockKeyValue("CAR9",
                    "{\"color\":\"brown\",\"make\":\"Holden\",\"model\":\"Barina\",\"owner\":\"Shotaro\"}"));
        }

        @Override
        public Iterator<KeyValue> iterator() {
            return carList.iterator();
        }

        @Override
        public void close() throws Exception {
            // do nothing
        }

    }

    @Test
    public void invokeUnknownTransaction() {
        FabCar contract = new FabCar();
        Context ctx = mock(Context.class);

        Throwable thrown = catchThrowable(() -> {
            contract.unknownTransaction(ctx);
        });

        assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause()
                .hasMessage("Undefined contract method called");
        assertThat(((ChaincodeException) thrown).getPayload()).isEqualTo(null);

        verifyZeroInteractions(ctx);
    }

    @Nested
    class InvokeQueryCarTransaction {

        @Test
        public void whenCarExists() {
            FabCar contract = new FabCar();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getStringState("CAR0"))
                    .thenReturn("{\"color\":\"blue\",\"make\":\"Toyota\",\"model\":\"Prius\",\"owner\":\"Tomoko\"}");

            Car car = contract.queryCar(ctx, "CAR0");

            assertThat(car).isEqualTo(new Car("Toyota", "Prius", "blue", "Tomoko"));
        }

        @Test
        public void whenCarDoesNotExist() {
            FabCar contract = new FabCar();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getStringState("CAR0")).thenReturn("");

            Throwable thrown = catchThrowable(() -> {
                contract.queryCar(ctx, "CAR0");
            });

            assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause()
                    .hasMessage("Car CAR0 does not exist");
            assertThat(((ChaincodeException) thrown).getPayload()).isEqualTo("CAR_NOT_FOUND".getBytes());
        }
    }

    @Test
    void invokeInitLedgerTransaction() {
        FabCar contract = new FabCar();
        Context ctx = mock(Context.class);
        ChaincodeStub stub = mock(ChaincodeStub.class);
        when(ctx.getStub()).thenReturn(stub);

        contract.initLedger(ctx);

        InOrder inOrder = inOrder(stub);
        inOrder.verify(stub).putStringState("CAR0",
                "{\"color\":\"blue\",\"make\":\"Toyota\",\"model\":\"Prius\",\"owner\":\"Tomoko\"}");
        inOrder.verify(stub).putStringState("CAR1",
                "{\"color\":\"red\",\"make\":\"Ford\",\"model\":\"Mustang\",\"owner\":\"Brad\"}");
        inOrder.verify(stub).putStringState("CAR2",
                "{\"color\":\"green\",\"make\":\"Hyundai\",\"model\":\"Tucson\",\"owner\":\"Jin Soo\"}");
        inOrder.verify(stub).putStringState("CAR3",
                "{\"color\":\"yellow\",\"make\":\"Volkswagen\",\"model\":\"Passat\",\"owner\":\"Max\"}");
        inOrder.verify(stub).putStringState("CAR4",
                "{\"color\":\"black\",\"make\":\"Tesla\",\"model\":\"S\",\"owner\":\"Adrian\"}");
        inOrder.verify(stub).putStringState("CAR5",
                "{\"color\":\"purple\",\"make\":\"Peugeot\",\"model\":\"205\",\"owner\":\"Michel\"}");
        inOrder.verify(stub).putStringState("CAR6",
                "{\"color\":\"white\",\"make\":\"Chery\",\"model\":\"S22L\",\"owner\":\"Aarav\"}");
        inOrder.verify(stub).putStringState("CAR7",
                "{\"color\":\"violet\",\"make\":\"Fiat\",\"model\":\"Punto\",\"owner\":\"Pari\"}");
        inOrder.verify(stub).putStringState("CAR8",
                "{\"color\":\"indigo\",\"make\":\"Tata\",\"model\":\"nano\",\"owner\":\"Valeria\"}");
        inOrder.verify(stub).putStringState("CAR9",
                "{\"color\":\"brown\",\"make\":\"Holden\",\"model\":\"Barina\",\"owner\":\"Shotaro\"}");
    }

    @Nested
    class InvokeCreateCarTransaction {

        @Test
        public void whenCarExists() {
            FabCar contract = new FabCar();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getStringState("CAR0"))
                    .thenReturn("{\"color\":\"blue\",\"make\":\"Toyota\",\"model\":\"Prius\",\"owner\":\"Tomoko\"}");

            Throwable thrown = catchThrowable(() -> {
                contract.createCar(ctx, "CAR0", "Nissan", "Leaf", "green", "Siobhán");
            });

            assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause()
                    .hasMessage("Car CAR0 already exists");
            assertThat(((ChaincodeException) thrown).getPayload()).isEqualTo("CAR_ALREADY_EXISTS".getBytes());
        }

        @Test
        public void whenCarDoesNotExist() {
            FabCar contract = new FabCar();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getStringState("CAR0")).thenReturn("");

            Car car = contract.createCar(ctx, "CAR0", "Nissan", "Leaf", "green", "Siobhán");

            assertThat(car).isEqualTo(new Car("Nissan", "Leaf", "green", "Siobhán"));
        }
    }

    @Test
    void invokeQueryAllCarsTransaction() {
        FabCar contract = new FabCar();
        Context ctx = mock(Context.class);
        ChaincodeStub stub = mock(ChaincodeStub.class);
        when(ctx.getStub()).thenReturn(stub);
        when(stub.getStateByRange("CAR1", "CAR99")).thenReturn(new MockCarResultsIterator());

        String cars = contract.queryAllCars(ctx);

        assertThat(cars).isEqualTo("[{\"key\":\"CAR0\","
                + "\"record\":{\"color\":\"blue\",\"make\":\"Toyota\",\"model\":\"Prius\",\"owner\":\"Tomoko\"}},"
                + "{\"key\":\"CAR1\","
                + "\"record\":{\"color\":\"red\",\"make\":\"Ford\",\"model\":\"Mustang\",\"owner\":\"Brad\"}},"
                + "{\"key\":\"CAR2\","
                + "\"record\":{\"color\":\"green\",\"make\":\"Hyundai\",\"model\":\"Tucson\",\"owner\":\"Jin Soo\"}},"
                + "{\"key\":\"CAR7\","
                + "\"record\":{\"color\":\"violet\",\"make\":\"Fiat\",\"model\":\"Punto\",\"owner\":\"Pari\"}},"
                + "{\"key\":\"CAR9\","
                + "\"record\":{\"color\":\"brown\",\"make\":\"Holden\",\"model\":\"Barina\",\"owner\":\"Shotaro\"}}]");
    }

    @Nested
    class ChangeCarOwnerTransaction {

        @Test
        public void whenCarExists() {
            FabCar contract = new FabCar();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getStringState("CAR0"))
                    .thenReturn("{\"color\":\"blue\",\"make\":\"Toyota\",\"model\":\"Prius\",\"owner\":\"Tomoko\"}");

            Car car = contract.changeCarOwner(ctx, "CAR0", "Dr Evil");

            assertThat(car).isEqualTo(new Car("Toyota", "Prius", "blue", "Dr Evil"));
        }

        @Test
        public void whenCarDoesNotExist() {
            FabCar contract = new FabCar();
            Context ctx = mock(Context.class);
            ChaincodeStub stub = mock(ChaincodeStub.class);
            when(ctx.getStub()).thenReturn(stub);
            when(stub.getStringState("CAR0")).thenReturn("");

            Throwable thrown = catchThrowable(() -> {
                contract.changeCarOwner(ctx, "CAR0", "Dr Evil");
            });

            assertThat(thrown).isInstanceOf(ChaincodeException.class).hasNoCause()
                    .hasMessage("Car CAR0 does not exist");
            assertThat(((ChaincodeException) thrown).getPayload()).isEqualTo("CAR_NOT_FOUND".getBytes());
        }
    }
}
