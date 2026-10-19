package com.cloudrive.fileService.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 文件秒传判定结果 VO
 * <p>
 * 迁移自单体 {@code com.ff.mycloud.model.vo.FileCheckVO}。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
@Builder
public class FileCheckVO {

    /** 文件是否已存在（同哈希 + 同大小），true 表示可秒传 */
    private Boolean exists;
}
