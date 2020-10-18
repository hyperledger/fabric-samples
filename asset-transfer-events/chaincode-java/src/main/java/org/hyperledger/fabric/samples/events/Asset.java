/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.events;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;


import org.json.JSONObject;

@DataType()
public final class Asset {

    @Property()
    private final String assetID;

    @Property()
    private String color;

    @Property()
    private int size;

    @Property()
    private String owner;

    @Property()
    private int appraisedValue;

    public Asset(final String assetID, final String color,
                 final int size, final String owner, final int value) {

        this.assetID = assetID;
        this.color = color;
        this.size = size;
        this.owner = owner;
        this.appraisedValue = value;
    }

    public String getAssetID() {
        return assetID;
    }

    public String getColor() {
        return color;
    }

    public int getSize() {
        return size;
    }

    public String getOwner() {
        return owner;
    }

    public int getAppraisedValue() {
        return appraisedValue;
    }

    public void setOwner(final String newowner) {
        this.owner = newowner;
    }

    public void setAppraisedValue(final int value) {
        this.appraisedValue = value;
    }

    public void setColor(final String c) {
        this.color = c;
    }

    public void setSize(final int s) {
        this.size = s;
    }

    // Serialize asset without private properties
    public byte[] serialize() {
        return serialize(null).getBytes(UTF_8);
    }

    public String serialize(final String privateProps) {
        Map<String, Object> tMap = new HashMap();
        tMap.put("ID", assetID);
        tMap.put("Color",  color);
        tMap.put("Owner",  owner);
        tMap.put("Size", Integer.toString(size));
        tMap.put("AppraisedValue", Integer.toString(appraisedValue));
        if (privateProps != null && privateProps.length() > 0) {
            tMap.put("asset_properties", new JSONObject(privateProps));
        }
        return new JSONObject(tMap).toString();
    }

    public static Asset deserialize(final byte[] assetJSON) {
        return deserialize(new String(assetJSON, UTF_8));
    }

    public static Asset deserialize(final String assetJSON) {

        JSONObject json = new JSONObject(assetJSON);
        Map<String, Object> tMap = json.toMap();
        final String id = (String) tMap.get("ID");

        final String color = (String) tMap.get("Color");
        final String owner = (String) tMap.get("Owner");
        int size = 0;
        int appraisedValue = 0;
        if (tMap.containsKey("Size")) {
            size = Integer.parseInt((String) tMap.get("Size"));
        }
        if (tMap.containsKey("AppraisedValue")) {
            appraisedValue = Integer.parseInt((String) tMap.get("AppraisedValue"));
        }
        return new Asset(id, color, size, owner, appraisedValue);

    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }

        Asset other = (Asset) obj;

        return Objects.deepEquals(
                new String[]{getAssetID(), getColor(), getOwner()},
                new String[]{other.getAssetID(), other.getColor(), other.getOwner()})
                &&
                Objects.deepEquals(
                        new int[]{getSize(), getAppraisedValue()},
                        new int[]{other.getSize(), other.getAppraisedValue()});
    }

    @Override
    public int hashCode() {
        return Objects.hash(getAssetID(), getColor(), getSize(), getOwner(), getAppraisedValue());
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "@" + Integer.toHexString(hashCode())
                + " [assetID=" + assetID + ", appraisedValue=" + appraisedValue + ", color="
                + color + ", size=" + size + ", owner=" + owner + "]";
    }

}
