/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Object representation of an asset. Note that the private member variable names don't follow the normal Java naming
 * convention as they map to the JSON format expected by the smart contract.
 */
public final class Asset {
    private final String ID; // checkstyle:ignore-line:MemberName
    private String Color; // checkstyle:ignore-line:MemberName
    private int Size; // checkstyle:ignore-line:MemberName
    private String Owner; // checkstyle:ignore-line:MemberName
    private int AppraisedValue; // checkstyle:ignore-line:MemberName

    public Asset(final String id) {
        this.ID = id;
    }

    public String getId() {
        return ID;
    }

    public String getColor() {
        return Color;
    }

    public void setColor(final String color) {
        this.Color = color;
    }

    public int getSize() {
        return Size;
    }

    public void setSize(final int size) {
        this.Size = size;
    }

    public String getOwner() {
        return Owner;
    }

    public void setOwner(final String owner) {
        this.Owner = owner;
    }

    public int getAppraisedValue() {
        return AppraisedValue;
    }

    public void setAppraisedValue(final int appraisedValue) {
        this.AppraisedValue = appraisedValue;
    }
}
