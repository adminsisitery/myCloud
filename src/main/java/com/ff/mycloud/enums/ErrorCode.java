package com.ff.mycloud.enums;

import lombok.Getter;

/**
 * 错误码枚举类
 */
@Getter
public enum ErrorCode {
    // 文件相关错误 (404)
    FILE_NOT_FOUND(404, "文件不存在"),
    SHARE_NOT_FOUND(404, "分享链接不存在"),
    USER_NOT_FOUND(404, "用户不存在"),
    
    // 过期相关错误 (410)
    SHARE_EXPIRED(410, "分享链接已过期"),
    
    // 认证相关错误 (401)
    INVALID_TOKEN(401, "无效的访问令牌"),
    INVALID_PASSWORD(401, "密码错误"),
    USER_NOT_LOGGED_IN(401, "用户未登录"),
    MISSING_PASSWORD(401, "请提供访问密码"),

    // 邮件相关错误
    EMAIL_ALREADY_EXIST(41004, "邮箱已被注册"),
    VERIFICATION_CODE_ERROR(41005, "验证码错误或已过期"),
    EMAIL_SEND_ERROR(41006, "邮件发送失败"),
    
    // 权限相关错误 (403)
    NO_PERMISSION(403, "无权访问此文件"),
    NO_SHARE_PERMISSION(403, "无权分享此文件"),
    NO_CANCEL_PERMISSION(403, "无权取消此分享"),
    
    // 其他业务错误 (400)
    FOLDER_NOT_EMPTY(400, "文件夹不为空，无法删除"),
    CANNOT_DOWNLOAD_FOLDER(400, "不能下载文件夹"),
    INVALID_FILENAME(400, "新文件名不能为空"),
    RENAME_FILL(400,"修改失败"),
    USERNAME_EXISTS(400, "用户名已存在"),
    EMAIL_EXISTS(400, "邮箱已被注册"),
    ACCOUNT_DISABLED(400, "账号已被禁用"),
    FILE_TOO_LARGE(413, "文件过大，超出上传限制"),
    
    // OSS相关错误
    OSS_DISABLED(503, "OSS存储服务未启用"),
    OSS_UPLOAD_FAILED(500, "文件上传到OSS失败"),
    OSS_DOWNLOAD_FAILED(500, "从OSS下载文件失败"),
    OSS_DELETE_FAILED(500, "从OSS删除文件失败"),
    FILE_DELETE_FAILED(500, "文件删除失败"),
    FILE_UPLOAD_FAILED(500, "文件上传失败"),
    
    // 系统错误 (500)
    SYSTEM_ERROR(500, "系统错误"),
    FILE_DOWNLOAD_FAILED(500, "文件下载失败"),
    FILE_NOT_SUPPORT_PREVIEW(500, "文件不支持预览");
    
    private final Integer httpStatus; // HTTP状态码作为错误码
    private final String message;    // 错误消息
    
    ErrorCode(Integer httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
    
    /**
     * 根据错误消息查找对应的枚举
     */
    public static ErrorCode getByMessage(String message) {
        for (ErrorCode errorCode : ErrorCode.values()) {
            if (errorCode.getMessage().equals(message)) {
                return errorCode;
            }
        }
        // 默认返回系统错误
        return SYSTEM_ERROR;
    }
} 