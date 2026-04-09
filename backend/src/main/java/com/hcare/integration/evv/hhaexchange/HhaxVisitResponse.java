package com.hcare.integration.evv.hhaexchange;

import com.fasterxml.jackson.annotation.JsonProperty;

public class HhaxVisitResponse {

    @JsonProperty("visitId")
    private String visitId;

    /** HHA Exchange internal visit ID — used for subsequent void/update operations. */
    @JsonProperty("evvmsid")
    private String evvmsid;

    @JsonProperty("status")
    private String status;

    @JsonProperty("errorCode")
    private String errorCode;

    @JsonProperty("errorMessage")
    private String errorMessage;

    public HhaxVisitResponse() {}

    public String getVisitId() {
        return visitId;
    }

    public void setVisitId(String visitId) {
        this.visitId = visitId;
    }

    public String getEvvmsid() {
        return evvmsid;
    }

    public void setEvvmsid(String evvmsid) {
        this.evvmsid = evvmsid;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
