package org.papernet;

import org.example.ledger.api.Property;
import org.example.ledger.api.State;

public class CommercialPaper extends State {
	private static final String ISSUED = "1";
	private static final String TRADING = "2";
	private static final String REDEEMED = "3";

	@Property(key=true)
	private String issuer;

	@Property
	private String owner;

	@Property(key=true)
	private String paperNumber;

	@Property
	private String issueDateTime;

	@Property
	private String maturityDateTime;

	@Property
	private String faceValue;

	@Property(name="currentState")
	private String status;

	private CommercialPaper(byte[] buffer) {
		super(buffer);
	}

	public static CommercialPaper create(byte[] buffer) {
		return new CommercialPaper(buffer);
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getIssuer() {
		return issuer;
	}

	public void setIssued() {
		status = ISSUED;
	}

	public void setTrading() {
		status = TRADING;
	}

	public void setRedeemed() {
		status = REDEEMED;
	}

	public boolean isIssued() {
		return status == ISSUED;
	}

	public boolean isTrading() {
		return status == TRADING;
	}

	public boolean isRedeemed() {
		return status == REDEEMED;
	}

}
