/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.fabcar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public final class CarTest {

    @Nested
    class Equality {

        @Test
        public void isReflexive() {
            Car car = new Car("Toyota", "Prius", "blue", "Tomoko");

            assertThat(car).isEqualTo(car);
        }

        @Test
        public void isSymmetric() {
            Car carA = new Car("Toyota", "Prius", "blue", "Tomoko");
            Car carB = new Car("Toyota", "Prius", "blue", "Tomoko");

            assertThat(carA).isEqualTo(carB);
            assertThat(carB).isEqualTo(carA);
        }

        @Test
        public void isTransitive() {
            Car carA = new Car("Toyota", "Prius", "blue", "Tomoko");
            Car carB = new Car("Toyota", "Prius", "blue", "Tomoko");
            Car carC = new Car("Toyota", "Prius", "blue", "Tomoko");

            assertThat(carA).isEqualTo(carB);
            assertThat(carB).isEqualTo(carC);
            assertThat(carA).isEqualTo(carC);
        }

        @Test
        public void handlesInequality() {
            Car carA = new Car("Toyota", "Prius", "blue", "Tomoko");
            Car carB = new Car("Ford", "Mustang", "red", "Brad");

            assertThat(carA).isNotEqualTo(carB);
        }

        @Test
        public void handlesOtherObjects() {
            Car carA = new Car("Toyota", "Prius", "blue", "Tomoko");
            String carB = "not a car";

            assertThat(carA).isNotEqualTo(carB);
        }

        @Test
        public void handlesNull() {
            Car car = new Car("Toyota", "Prius", "blue", "Tomoko");

            assertThat(car).isNotEqualTo(null);
        }
    }

    @Test
    public void toStringIdentifiesCar() {
        Car car = new Car("Toyota", "Prius", "blue", "Tomoko");

        assertThat(car.toString()).isEqualTo("Car@61a77e4f [make=Toyota, model=Prius, color=blue, owner=Tomoko]");
    }
}
