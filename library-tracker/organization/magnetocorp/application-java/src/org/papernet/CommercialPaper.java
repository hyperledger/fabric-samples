/*
 *  SPDX-License-Identifier: Apache-2.0
 */

package org.papernet;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;
import org.json.JSONObject;
import org.json.JSONPropertyIgnore;
import org.papernet.ledgerapi.State;

@DataType()
public class CommercialPaper extends State {

    // Enumerate commercial paper state values
    public final static String ISSUED = "ISSUED";
    public final static String TRADING = "TRADING";
    public final static String REDEEMED = "REDEEMED";

    @Property()
    private String state="";

    public String getState() {
        return state;
    }

    public CommercialPaper setState(String state) {
        this.state = state;
        return this;
    }

    @JSONPropertyIgnore()
    public boolean isIssued() {
        return this.state.equals(CommercialPaper.ISSUED);
    }

    @JSONPropertyIgnore()
    public boolean isTrading() {
        return this.state.equals(CommercialPaper.TRADING);
    }

    @JSONPropertyIgnore()
    public boolean isRedeemed() {
        return this.state.equals(CommercialPaper.REDEEMED);
    }

    public CommercialPaper setIssued() {
        this.state = CommercialPaper.ISSUED;
        return this;
    }

    public CommercialPaper setTrading() {
        this.state = CommercialPaper.TRADING;
        return this;
    }

    public CommercialPaper setRedeemed() {
        this.state = CommercialPaper.REDEEMED;
        return this;
    }

    @Property()
    private String paperNumber;

    @Property()
    private String issuer;

    @Property()
    private String issueDateTime;

    @Property()
    private int faceValue;

    @Property()
    private String maturityDateTime;

    @Property()
    private String owner;

    public String getOwner() {
        return owner;
    }

    public CommercialPaper setOwner(String owner) {
        this.owner = owner;
        return this;
    }

    public CommercialPaper() {
        super();
    }

    public CommercialPaper setKey() {
        this.key = State.makeKey(new String[] { this.paperNumber });
        return this;
    }

    public String getPaperNumber() {
        return paperNumber;
    }

    public CommercialPaper setPaperNumber(String paperNumber) {
        this.paperNumber = paperNumber;
        return this;
    }

    public String getIssuer() {
        return issuer;
    }

    public CommercialPaper setIssuer(String issuer) {
        this.issuer = issuer;
        return this;
    }

    public String getIssueDateTime() {
        return issueDateTime;
    }

    public CommercialPaper setIssueDateTime(String issueDateTime) {
        this.issueDateTime = issueDateTime;
        return this;
    }

    public int getFaceValue() {
        return faceValue;
    }

    public CommercialPaper setFaceValue(int faceValue) {
        this.faceValue = faceValue;
        return this;
    }

    public String getMaturityDateTime() {
        return maturityDateTime;
    }

    public CommercialPaper setMaturityDateTime(String maturityDateTime) {
        this.maturityDateTime = maturityDateTime;
        return this;
    }

    @Override
    public String toString() {
        return "Paper::" + this.key + "   " + this.getPaperNumber() + " " + getIssuer() + " " + getFaceValue();
    }

    /**
     * Deserialize a state data to commercial paper
     *
     * @param {Buffer} data to form back into the object
     */
    public static CommercialPaper deserialize(byte[] data) {
        JSONObject json = new JSONObject(new String(data, UTF_8));

        String issuer = json.getString("issuer");
        String paperNumber = json.getString("paperNumber");
        String issueDateTime = json.getString("issueDateTime");
        String maturityDateTime = json.getString("maturityDateTime");
        String owner = json.getString("owner");
        int faceValue = json.getInt("faceValue");
        String state = json.getString("state");
        return createInstance(issuer, paperNumber, issueDateTime, maturityDateTime, faceValue, owner, state);
    }

    public static byte[] serialize(CommercialPaper paper) {
        return State.serialize(paper);
    }

    /**
     * Factory method to create a commercial paper object
     */
    public static CommercialPaper createInstance(String issuer, String paperNumber, String issueDateTime,
            String maturityDateTime, int faceValue, String owner, String state) {
        return new CommercialPaper().setIssuer(issuer).setPaperNumber(paperNumber).setMaturityDateTime(maturityDateTime)
                .setFaceValue(faceValue).setKey().setIssueDateTime(issueDateTime).setOwner(issuer).setState(state);
    }


}
