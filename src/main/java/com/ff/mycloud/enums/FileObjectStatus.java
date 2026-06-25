package com.ff.mycloud.enums;

public enum FileObjectStatus {
    UPLOADING(0),
    AVAILABLE(1),
    FAILED(2),
    CANCELLED(3),
    DELETING(4),
    DELETED(5);

    private final int code;

    FileObjectStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
