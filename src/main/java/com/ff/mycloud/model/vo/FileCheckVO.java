package com.ff.mycloud.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 文件存在性校验视图对象
 * 功能描述：用于封装文件上传或移动时，检查目标路径是否已存在同名文件的校验结果
 *
 * @author 飞飞
 * @date 2026/6/27
 */
@Builder
@Data
public class FileCheckVO {

    /** 文件是否已存在，true表示目标路径已有同名文件 */
    private Boolean exists;
}
