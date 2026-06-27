package com.ff.mycloud.enums;

/**
 * 文件对象状态枚举类。
 * 用于标识云存储中文件对象的生命周期状态，包括上传中、可用、失败、已取消、删除中、已删除等状态。
 *
 * @author 飞飞
 * @date 2026/06/27
 */
public enum FileObjectStatus {
    /** 上传中 */
    UPLOADING(0),
    /** 可用 */
    AVAILABLE(1),
    /** 上传失败 */
    FAILED(2),
    /** 已取消 */
    CANCELLED(3),
    /** 删除中 */
    DELETING(4),
    /** 已删除 */
    DELETED(5);

    /** 状态对应的数值编码 */
    private final int code;

    /**
     * 构造方法，使用指定的状态码初始化文件对象状态枚举值。
     *
     * @param code 状态编码
     */
    FileObjectStatus(int code) {
        this.code = code;
    }

    /**
     * 获取当前文件对象状态对应的数值编码。
     *
     * @return 状态编码
     */
    public int getCode() {
        return code;
    }
}
