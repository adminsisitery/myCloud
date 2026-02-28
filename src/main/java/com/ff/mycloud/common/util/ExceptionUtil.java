package com.ff.mycloud.common.util;

import com.ff.mycloud.common.exceotion.BusinessException;
import com.ff.mycloud.enums.ErrorCode;

/**
 * @description: TODO 
 * @author 飞飞
 * @date 2026/1/13 20:28
 * @version 1.0
 */
 
public class ExceptionUtil {

    public static void throwBizException(ErrorCode errorCode) {
        throw new BusinessException(errorCode);
    }

    public static void throwBizException(ErrorCode errorCode, String message) {
        BusinessException exception = new BusinessException(errorCode);
        exception.setMessage(message);
        throw exception;
    }

    public static void throwIf(boolean condition, ErrorCode errorCode) {
        if (condition){
            throwBizException(errorCode);
        }
    }

    public static void throwIf(boolean condition, ErrorCode errorCode, String message) {
        if (condition){
            throwBizException(errorCode, message);
        }
    }
    public static void throwIfNull(Object object, ErrorCode errorCode) {
        if (object == null){
            throwBizException(errorCode);
        }
    }
}
