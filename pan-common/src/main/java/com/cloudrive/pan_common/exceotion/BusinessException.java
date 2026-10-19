package com.cloudrive.pan_common.exceotion;


import com.cloudrive.pan_common.enums.ErrorCode;

/**
 * 业务异常类，用于封装业务逻辑中的异常信息。
 * 包含错误码和自定义错误消息，由全局异常处理器统一捕获并返回标准响应。
 *
 * @author 飞飞
 * @date 2026/6/27
 */
public class BusinessException extends RuntimeException{
    /** 业务错误码 */
    private Integer code;
    /** 业务错误消息 */
    private String message;

    /**
     * 使用ErrorCode构造业务异常
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getHttpStatus();
        this.message = errorCode.getMessage();
    }

    /**
     * 使用ErrorCode和自定义消息构造业务异常
     *
     * @param errorCode 错误码枚举
     * @param message   自定义错误消息
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(errorCode.getMessage());
        this.code = errorCode.getHttpStatus();
        this.message = message;
    }

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public Integer getCode() {
        return code;
    }

    /**
     * 设置错误码
     *
     * @param code 错误码
     */
    public void setCode(Integer code) {
        this.code = code;
    }

    /**
     * 获取错误消息
     *
     * @return 错误消息
     */
    @Override
    public String getMessage() {
        return message;
    }

    /**
     * 设置错误消息
     *
     * @param message 错误消息
     */
    public void setMessage(String message) {
        this.message = message;
    }
}
