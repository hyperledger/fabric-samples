/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.fabcar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public final class CarQueryResultTest {

    @Nested
    class Equality {

        @Test
        public void isReflexive() {
            CarQueryResult cqr = new CarQueryResult("CAR1", new Car("Toyota", "Prius", "blue", "Tomoko"));

            assertThat(cqr).isEqualTo(cqr);
        }

        @Test
        public void isSymmetric() {
            Car car = new Car("Toyota", "Prius", "blue", "Tomoko");
            CarQueryResult cqrA = new CarQueryResult("CAR1", car);
            CarQueryResult cqrB = new CarQueryResult("CAR1", car);

            assertThat(cqrA).isEqualTo(cqrB);
            assertThat(cqrB).isEqualTo(cqrA);
        }

        @Test
        public void isTransitive() {
            Car car = new Car("Toyota", "Prius", "blue", "Tomoko");
            CarQueryResult cqrA = new CarQueryResult("CAR1", car);
            CarQueryResult cqrB = new CarQueryResult("CAR1", car);
            CarQueryResult cqrC = new CarQueryResult("CAR1", car);

            assertThat(cqrA).isEqualTo(cqrB);
            assertThat(cqrB).isEqualTo(cqrC);
            assertThat(cqrA).isEqualTo(cqrC);
        }

        @Test
        public void handlesKeyInequality() {
            CarQueryResult cqrA = new CarQueryResult("CAR1", new Car("Toyota", "Prius", "blue", "Tomoko"));
            CarQueryResult cqrB = new CarQueryResult("CAR2", new Car("Toyota", "Prius", "blue", "Tomoko"));

            assertThat(cqrA).isNotEqualTo(cqrB);
        }

        @Test
        public void handlesRecordInequality() {
            CarQueryResult cqrA = new CarQueryResult("CAR1", new Car("Toyota", "Prius", "blue", "Tomoko"));
            CarQueryResult cqrB = new CarQueryResult("CAR1", new Car("Ford", "Mustang", "red", "Brad"));

            assertThat(cqrA).isNotEqualTo(cqrB);
        }

        @Test
        public void handlesKeyRecordInequality() {
            CarQueryResult cqrA = new CarQueryResult("CAR1", new Car("Toyota", "Prius", "blue", "Tomoko"));
            CarQueryResult cqrB = new CarQueryResult("CAR2", new Car("Ford", "Mustang", "red", "Brad"));

            assertThat(cqrA).isNotEqualTo(cqrB);
        }

        @Test
        public void handlesOtherObjects() {
            CarQueryResult cqrA = new CarQueryResult("CAR1", new Car("Toyota", "Prius", "blue", "Tomoko"));
            String cqrB = "not a car";

            assertThat(cqrA).isNotEqualTo(cqrB);
        }

        @Test
        public void handlesNull() {
            CarQueryResult cqr = new CarQueryResult("CAR1", new Car("Toyota", "Prius", "blue", "Tomoko"));

            assertThat(cqr).isNotEqualTo(null);
        }
    }

    @Test
    public void toStringIdentifiesCarQueryResult() {
        CarQueryResult cqr = new CarQueryResult("CAR1", new Car("Toyota", "Prius", "blue", "Tomoko"));

        assertThat(cqr.toString()).isEqualTo("CarQueryResult@65766eb3 [key=CAR1, "
                + "record=Car@61a77e4f [make=Toyota, model=Prius, color=blue, owner=Tomoko]]");
    }
}
