package com.ff.mycloud.common.util;

import com.ff.mycloud.common.exceotion.BusinessException;
import com.ff.mycloud.enums.ErrorCode;

/**
 * 异常工具类，提供便捷的业务异常抛出方法。
 * 支持条件判断抛出和空值检查抛出，简化业务代码中的异常处理逻辑。
 *
 * @author 飞飞
 * @date 2026/6/27
 */
public class ExceptionUtil {

    /**
     * 直接抛出业务异常
     *
     * @param errorCode 错误码枚举
     * @throws BusinessException 业务异常
     */
    public static void throwBizException(ErrorCode errorCode) {
        throw new BusinessException(errorCode);
    }

    /**
     * 抛出带有自定义消息的业务异常
     *
     * @param errorCode 错误码枚举
     * @param message   自定义错误消息
     * @throws BusinessException 业务异常
     */
    public static void throwBizException(ErrorCode errorCode, String message) {
        BusinessException exception = new BusinessException(errorCode);
        exception.setMessage(message);
        throw exception;
    }

    /**
     * 当条件为true时抛出业务异常
     *
     * @param condition 触发条件
     * @param errorCode 错误码枚举
     * @throws BusinessException 当condition为true时抛出
     */
    public static void throwIf(boolean condition, ErrorCode errorCode) {
        if (condition){
            throwBizException(errorCode);
        }
    }

    /**
     * 当条件为true时抛出带自定义消息的业务异常
     *
     * @param condition 触发条件
     * @param errorCode 错误码枚举
     * @param message   自定义错误消息
     * @throws BusinessException 当condition为true时抛出
     */
    public static void throwIf(boolean condition, ErrorCode errorCode, String message) {
        if (condition){
            throwBizException(errorCode, message);
        }
    }

    /**
     * 当对象为null时抛出业务异常
     *
     * @param object    待检查对象
     * @param errorCode 错误码枚举
     * @throws BusinessException 当object为null时抛出
     */
    public static void throwIfNull(Object object, ErrorCode errorCode) {
        if (object == null){
            throwBizException(errorCode);
        }
    }
}
