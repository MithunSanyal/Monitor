package com.trov.monitor;

public class CloudAccount {
	
	private String cloudAccountNumber;
	
	private String cloudName;

	private String apiSecretKey;

	private String apiSharedKey;

	private String cloudAccountName;

	private String endPoint;

	private int userId;

	public String getCloudAccountNumber() {
		return cloudAccountNumber;
	}

	public void setCloudAccountNumber(String cloudAccountNumber) {
		this.cloudAccountNumber = cloudAccountNumber;
	}

	public String getCloudName() {
		return cloudName;
	}

	public void setCloudName(String cloudName) {
		this.cloudName = cloudName;
	}

	public String getApiSecretKey() {
		return apiSecretKey;
	}

	public void setApiSecretKey(String apiSecretKey) {
		this.apiSecretKey = apiSecretKey;
	}

	public String getApiSharedKey() {
		return apiSharedKey;
	}

	public void setApiSharedKey(String apiSharedKey) {
		this.apiSharedKey = apiSharedKey;
	}

	public String getCloudAccountName() {
		return cloudAccountName;
	}

	public void setCloudAccountName(String cloudAccountName) {
		this.cloudAccountName = cloudAccountName;
	}

	public String getEndPoint() {
		return endPoint;
	}

	public void setEndpoint(String endPoint) {
		this.endPoint = endPoint;
	}

	public int getUserId() {
		return userId;
	}

	public void setUserId(int userId) {
		this.userId = userId;
	}

    
}