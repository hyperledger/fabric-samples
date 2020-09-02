/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.sbe;

import java.util.Objects;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import com.owlike.genson.annotation.JsonProperty;

@DataType()
public final class Asset {

    @Property()
    private final String ID;

    @Property()
    private int Value;

    @Property()
    private String Owner;

    @Property()
    private String OwnerOrg;

    @JsonProperty("ID")
    public String getID() {
        return ID;
    }

    @JsonProperty("Value")
    public int getValue() {
        return Value;
    }

    public void setValue(final int Value) {
        this.Value = Value;
    }

    @JsonProperty("Owner")
    public String getOwner() {
        return Owner;
    }

    public void setOwner(final String Owner) {
        this.Owner = Owner;
    }

    @JsonProperty("OwnerOrg")
    public String getOwnerOrg() {
        return OwnerOrg;
    }

    public void setOwnerOrg(final String OwnerOrg) {
        this.OwnerOrg = OwnerOrg;
    }

    public Asset(@JsonProperty("ID") final String ID, @JsonProperty("Value") final int Value,
                 @JsonProperty("Owner") final String Owner, @JsonProperty("OwnerOrg") final String OwnerOrg) {
        this.ID = ID;
        this.Value = Value;
        this.Owner = Owner;
        this.OwnerOrg = OwnerOrg;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Asset asset = (Asset) o;
        return getValue() == asset.getValue()
                &&
                getID().equals(asset.getID())
                &&
                getOwner().equals(asset.getOwner())
                &&
                getOwnerOrg().equals(asset.getOwnerOrg());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getID(), getValue(), getOwner(), getOwnerOrg());
    }

    @Override
    public String toString() {
        return "Asset{" + "ID='" + ID + '\'' + ", Value=" + Value + ", Owner='"
                + Owner + '\'' + ", OwnerOrg='" + OwnerOrg + '\'' + '}';
    }
}
