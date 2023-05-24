/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.assettransfer;

import java.util.Objects;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import com.owlike.genson.annotation.JsonProperty;

// license-key
// email
// mac-address
// device-id
// timestamp

@DataType()
public final class Asset {

    @Property()
    private final String licenseKey;

    @Property()
    private final String email;

    @Property()
    private final String macAddress;

    @Property()
    private final String deviceID;

    @Property()
    private final String timestamp;

    public String getLicenseKey() {
        return licenseKey;
    }

    public String getEmail() {
        return email;
    }

    public int getMacAddress() {
        return macAddress;
    }

    public String getDeviceID() {
        return deviceID;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public Asset(@JsonProperty("licenseKey") final String licenseKey,
            @JsonProperty("email") final String email,
            @JsonProperty("macAddress") final String macAddress,
            @JsonProperty("deviceID") final String deviceID,
            @JsonProperty("timestamp") final String timestamp)
    {
        this.licenseKey = licenseKey;
        this.email = email;
        this.macAddress = macAddress;
        this.deviceID = deviceID;
        this.timestamp = timestamp;
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
                new String[] {getLicenseKey(), getEmail(), getMacAddress(), getDeviceID(), getTimestamp()},
                new String[] {other.getLicenseKey(), other.getEmail(), other.getMacAddress(), other.getDeviceID(), other.getTimestamp()});
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLicenseKey(), getEmail(), getMacAddress(), getDeviceID(), getTimestamp());
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "@" + Integer.toHexString(hashCode()) + " [licenseKey=" + licenseKey + ", email="
                + email + ", macAddress=" + macAddress + ", deviceID=" + deviceID + ", timestamp=" + timestamp + "]";
    }
}
