package com.ff.mycloud.common.exceotion;

import com.ff.mycloud.enums.ErrorCode;

public class BusinessException extends RuntimeException{
    private Integer code;
    private String message;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getHttpStatus();
        this.message = errorCode.getMessage();
    }
    public BusinessException(ErrorCode errorCode, String message) {
        super(errorCode.getMessage());
        this.code = errorCode.getHttpStatus();
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
