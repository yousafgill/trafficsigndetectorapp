package com.duvallsoftware.trafficsigndetector;

public class TrafficSign {	
	
	private String signId = null;
	private long detectedTimestamp = -1;

	public TrafficSign(String signId, long timestampt) {
		this.signId = signId;
		this.detectedTimestamp = timestampt;		
	}

	/**
	 * @return the detectedTimestamp
	 */
	public long getDetectedTimestamp() {
		return detectedTimestamp;
	}
	
	/**
	 * @return the signId
	 */
	public String getSignId() {
		return signId;
	}

	/**
	 * @param signId
	 *            the signId to set
	 */
	public void setSignId(String signId) {
		this.signId = signId;
	}
}
