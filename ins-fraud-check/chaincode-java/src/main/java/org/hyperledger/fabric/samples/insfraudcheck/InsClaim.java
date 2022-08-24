/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.insfraudcheck;

import java.util.Objects;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import com.owlike.genson.annotation.JsonProperty;

@DataType()
public final class InsClaim {

    @Property()
    private final String claimID;

    @Property()
    private final String vin;

    @Property()
    private final int custId;

    @Property()
    private final String insuranceId;

    @Property()
    private final int claimAmount;


    public String getClaimID() {
        return claimID;
    }

    public String getVin() {
        return vin;
    }

    public int getCustId() {
        return custId;
    }

    public String getInsuranceId() {
        return insuranceId;
    }

    public int getClaimAmount() {
        return claimAmount;
    }

    

    public InsClaim(@JsonProperty("claimID") final String claimID, @JsonProperty("vin") final String vin, @JsonProperty("custId") final int custId, @JsonProperty("insuranceId") final String insuranceId, @JsonProperty("claimAmount") final int claimAmount) {
        this.claimID = claimID;
        this.vin = vin;
        this.custId = custId;
        this.insuranceId = insuranceId;
        this.claimAmount = claimAmount;
    }    


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + claimAmount;
        result = prime * result + ((claimID == null) ? 0 : claimID.hashCode());
        result = prime * result + custId;
        result = prime * result + ((insuranceId == null) ? 0 : insuranceId.hashCode());
        result = prime * result + ((vin == null) ? 0 : vin.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        InsClaim other = (InsClaim) obj;
        if (claimAmount != other.claimAmount)
            return false;
        if (claimID == null) {
            if (other.claimID != null)
                return false;
        } else if (!claimID.equals(other.claimID))
            return false;
        if (custId != other.custId)
            return false;
        if (insuranceId == null) {
            if (other.insuranceId != null)
                return false;
        } else if (!insuranceId.equals(other.insuranceId))
            return false;
        if (vin == null) {
            if (other.vin != null)
                return false;
        } else if (!vin.equals(other.vin))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "InsClaim [claimAmount=" + claimAmount + ", claimID=" + claimID + ", custId=" + custId + ", insuranceId="
                + insuranceId + ", vin=" + vin + "]";
    }

    
}
