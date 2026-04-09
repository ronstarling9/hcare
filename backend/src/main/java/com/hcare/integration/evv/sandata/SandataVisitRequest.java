package com.hcare.integration.evv.sandata;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SandataVisitRequest {

    @JsonProperty("visitId")
    private String visitId;

    @JsonProperty("memberId")
    private String memberId;

    @JsonProperty("providerId")
    private String providerId;

    @JsonProperty("caregiverId")
    private String caregiverId;

    @JsonProperty("serviceCode")
    private String serviceCode;

    @JsonProperty("timeIn")
    private String timeIn;

    @JsonProperty("timeOut")
    private String timeOut;

    @JsonProperty("stateCode")
    private String stateCode;

    public SandataVisitRequest() {}

    public String getVisitId() {
        return visitId;
    }

    public void setVisitId(String visitId) {
        this.visitId = visitId;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getCaregiverId() {
        return caregiverId;
    }

    public void setCaregiverId(String caregiverId) {
        this.caregiverId = caregiverId;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public void setServiceCode(String serviceCode) {
        this.serviceCode = serviceCode;
    }

    public String getTimeIn() {
        return timeIn;
    }

    public void setTimeIn(String timeIn) {
        this.timeIn = timeIn;
    }

    public String getTimeOut() {
        return timeOut;
    }

    public void setTimeOut(String timeOut) {
        this.timeOut = timeOut;
    }

    public String getStateCode() {
        return stateCode;
    }

    public void setStateCode(String stateCode) {
        this.stateCode = stateCode;
    }
}
