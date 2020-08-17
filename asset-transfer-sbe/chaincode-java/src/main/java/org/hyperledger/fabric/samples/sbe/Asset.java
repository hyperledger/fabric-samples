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
    private final String id;

    @Property()
    private int value;

    @Property()
    private String owner;

    @Property()
    private String ownerOrg;

    public String getId() {
        return id;
    }

    public int getValue() {
        return value;
    }

    public String getOwner() {
        return owner;
    }

    public String getOwnerOrg() {
        return ownerOrg;
    }

    public void setValue(final int value) {
        this.value = value;
    }

    public void setOwner(final String owner) {
        this.owner = owner;
    }

    public void setOwnerOrg(final String ownerOrg) {
        this.ownerOrg = ownerOrg;
    }

    public Asset(@JsonProperty("id") final String id, @JsonProperty("value") final int value,
                 @JsonProperty("owner") final String owner, @JsonProperty("ownerOrg") final String ownerOrg) {
        this.id = id;
        this.value = value;
        this.owner = owner;
        this.ownerOrg = ownerOrg;
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
                getId().equals(asset.getId())
                &&
                getOwner().equals(asset.getOwner())
                &&
                getOwnerOrg().equals(asset.getOwnerOrg());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getValue(), getOwner(), getOwnerOrg());
    }

    @Override
    public String toString() {
        return "Asset{" + "id='" + id + '\'' + ", value=" + value + ", owner='"
                + owner + '\'' + ", ownerOrg='" + ownerOrg + '\'' + '}';
    }
}
