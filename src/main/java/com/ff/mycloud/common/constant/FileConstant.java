package com.ff.mycloud.common.constant;

/**
 * 文件相关常量定义，包含文件大小单位和上传限制。
 *
 * @author 飞飞
 * @date 2026/6/27
 */
public interface FileConstant {
    /** 1KB 字节数 */
    long KB = 1024L;
    /** 1MB 字节数 */
    long MB = 1024 * KB;
    /** 1GB 字节数 */
    long GB = 1024 * MB;
    /** 1TB 字节数 */
    long TB = 1024 * GB;

    /** 文件上传最大大小限制（10MB） */
    long MAX_SIZE = 10 * MB;
    /** 文件最小大小基准值 */
    long BASE_SIZE = 0;
}
