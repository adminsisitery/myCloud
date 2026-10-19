package com.cloudrive.pan_common.enums;

import lombok.Getter;

/**
 * 错误码枚举类
 * <p>
 * 统一定义业务异常的错误码和对应的用户友好消息。
 * 错误码采用HTTP状态码风格，涵盖文件操作、认证授权、OSS存储等各类业务场景。
 * 每个枚举值包含httpStatus（错误码）和message（中文提示信息）。
 * </p>
 *
 * @author 飞飞
 * @date 2026/06/27
 */
@Getter
public enum ErrorCode {

    // ==================== 文件相关错误 (404) ====================
    /** 文件不存在 */
    FILE_NOT_FOUND(404, "文件不存在"),
    /** 分享链接不存在 */
    SHARE_NOT_FOUND(404, "分享链接不存在"),
    /** 用户不存在 */
    USER_NOT_FOUND(404, "用户不存在"),

    // ==================== 过期相关错误 (410) ====================
    /** 分享链接已过期 */
    SHARE_EXPIRED(410, "分享链接已过期"),

    // ==================== 认证相关错误 (401) ====================
    /** 无效的访问令牌（Token） */
    INVALID_TOKEN(401, "无效的访问令牌"),
    /** 分享密码错误 */
    INVALID_PASSWORD(401, "密码错误"),
    /** 用户未登录 */
    USER_NOT_LOGGED_IN(401, "用户未登录"),
    /** 缺少访问密码 */
    MISSING_PASSWORD(401, "请提供访问密码"),

    // ==================== 邮件相关错误 ====================
    /** 邮箱已被注册 */
    EMAIL_ALREADY_EXIST(41004, "邮箱已被注册"),
    /** 验证码错误或已过期 */
    VERIFICATION_CODE_ERROR(41005, "验证码错误或已过期"),
    /** 邮件发送失败 */
    EMAIL_SEND_ERROR(41006, "邮件发送失败"),

    // ==================== 权限相关错误 (403) ====================
    /** 无权访问此文件 */
    NO_PERMISSION(403, "无权访问此文件"),
    /** 无权分享此文件 */
    NO_SHARE_PERMISSION(403, "无权分享此文件"),
    /** 无权取消此分享 */
    NO_CANCEL_PERMISSION(403, "无权取消此分享"),

    // ==================== 其他业务错误 (400) ====================
    /** 不能将文件夹移动到自身的子目录下 */
    CANNOT_MOVE_INTO_OWN_SUBTREE(400, "不能移动到自身的子目录下"),
    /** 文件不在回收站中 */
    FILE_NOT_IN_TRASH(400, "该文件不在回收站中"),
    /** 文件夹不为空，无法删除 */
    FOLDER_NOT_EMPTY(400, "文件夹不为空，无法删除"),
    /** 不支持下载文件夹 */
    CANNOT_DOWNLOAD_FOLDER(400, "不能下载文件夹"),
    /** 新文件名不能为空 */
    INVALID_FILENAME(400, "新文件名不能为空"),
    /** 重命名失败 */
    RENAME_FILL(400, "修改失败"),
    /** 用户名已存在 */
    USERNAME_EXISTS(400, "用户名已存在"),
    /** 邮箱已被注册 */
    EMAIL_EXISTS(400, "邮箱已被注册"),
    /** 账号已被禁用 */
    ACCOUNT_DISABLED(400, "账号已被禁用"),
    /** 账号已被临时锁定（爆破登录保护） */
    ACCOUNT_LOCKED(423, "账号已被临时锁定，请稍后再试"),
    /** 文件超出上传大小限制 */
    FILE_TOO_LARGE(413, "文件过大，超出上传限制"),
    /** 请求参数不合法 */
    INVALID_PARAM(400, "请求参数不合法"),
    /** 秒传不可用：服务端找不到可复用的物理对象 */
    FAST_UPLOAD_UNAVAILABLE(400, "该文件在云端不存在，无法秒传"),

    // ==================== OSS存储相关错误 ====================
    /** OSS存储服务未启用 */
    OSS_DISABLED(503, "OSS存储服务未启用"),
    /** 文件上传到OSS失败 */
    OSS_UPLOAD_FAILED(500, "文件上传到OSS失败"),
    /** 从OSS下载文件失败 */
    OSS_DOWNLOAD_FAILED(500, "从OSS下载文件失败"),
    /** 从OSS删除文件失败 */
    OSS_DELETE_FAILED(500, "从OSS删除文件失败"),
    /** 文件删除失败 */
    FILE_DELETE_FAILED(500, "文件删除失败"),
    /** 文件上传失败（通用） */
    FILE_UPLOAD_FAILED(500, "文件上传失败"),

    // ==================== 系统错误 (500) ====================
    /** 系统内部错误 */
    SYSTEM_ERROR(500, "系统错误"),
    /** 文件下载失败 */
    FILE_DOWNLOAD_FAILED(500, "文件下载失败"),
    /** 文件格式不支持预览 */
    FILE_NOT_SUPPORT_PREVIEW(500, "文件不支持预览");

    /** HTTP状态码作为错误码 */
    private final Integer httpStatus;
    /** 用户友好的错误消息 */
    private final String message;

    /**
     * 构造函数
     *
     * @param httpStatus HTTP错误码
     * @param message    中文错误描述
     */
    ErrorCode(Integer httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    /**
     * 根据错误消息文本查找对应的错误码枚举
     * <p>用于从消息反查错误码的场景。</p>
     *
     * @param message 错误消息文本
     * @return 匹配的ErrorCode枚举，未匹配时返回SYSTEM_ERROR
     */
    public static ErrorCode getByMessage(String message) {
        for (ErrorCode errorCode : ErrorCode.values()) {
            if (errorCode.getMessage().equals(message)) {
                return errorCode;
            }
        }
        // 未匹配到，返回默认的系统错误
        return SYSTEM_ERROR;
    }
}