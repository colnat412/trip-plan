package com.tripbudget.tripbudget_core.uploads.enums;

public enum UploadStatus {
    PENDING("pending"),
    ATTACHED("attached"),
    DELETE_PENDING("delete_pending");

    private final String databaseValue;

    UploadStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String getDatabaseValue() {
        return databaseValue;
    }
}
