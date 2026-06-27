package com.ff.mycloud.common.result;

import com.ff.mycloud.common.constant.CommonConstants;
import lombok.Data;

/**
 * 统一API响应结果封装类。
 * 提供静态工厂方法快速构建成功或失败的响应对象，泛型T为响应数据的类型。
 *
 * @param <T> 响应数据的类型
 * @author 飞飞
 * @date 2026/6/27
 */
@Data
public class Result<T> {
    /** 状态码 */
    private Integer code;
    /** 响应消息 */
    private String message;
    /** 响应数据 */
    private T data;

    /**
     * 私有构造方法，通过静态工厂方法创建实例
     *
     * @param code    状态码
     * @param message 响应消息
     * @param data    响应数据
     */
    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 返回成功响应（无数据）
     *
     * @param <T> 数据类型
     * @return 成功的Result对象
     */
    public static <T> Result<T> success() {
        return new Result<>(CommonConstants.StatusCode.SUCCESS, "success", null);
    }

    /**
     * 返回成功响应（带数据）
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return 成功的Result对象
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(CommonConstants.StatusCode.SUCCESS, "success", data);
    }

    /**
     * 返回失败响应（仅消息）
     *
     * @param message 错误消息
     * @param <T>     数据类型
     * @return 失败的Result对象
     */
    public static <T> Result<T> error(String message) {
        return new Result<>(CommonConstants.StatusCode.SERVER_ERROR, message, null);
    }

    /**
     * 返回失败响应（指定错误码和消息）
     *
     * @param code    错误码
     * @param message 错误消息
     * @param <T>     数据类型
     * @return 失败的Result对象
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}