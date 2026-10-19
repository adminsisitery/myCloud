package com.cloudrive.fileService.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 秒传执行结果 VO
 * <p>
 * 迁移自单体 {@code com.ff.mycloud.model.vo.FastUploadVO}。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
@Builder
public class FastUploadVO {

    /** 新建（或幂等命中）的文件记录ID，客户端据此刷新列表 */
    private Long fileId;

    /** 最终落库的文件名（含后缀），同级重名时已自动加数字后缀 */
    private String filename;

    /** 文件大小（字节） */
    private Long fileSize;

    /** true 表示目标目录已有同内容同名的记录，本次直接复用，未新建 */
    private Boolean reused;
}
