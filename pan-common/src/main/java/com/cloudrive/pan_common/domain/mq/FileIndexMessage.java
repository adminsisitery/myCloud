package com.cloudrive.pan_common.domain.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * ES 索引同步消息（跨服务共享，生产者与消费者必须使用同一个类，保证 __TypeId__ 一致）
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileIndexMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文件ID */
    private Long id;

    /** 带后缀的完整文件名 */
    private String fileName;

    /** 文本内容（分片/直传等拿不到文本时为空，由消费端按需重建） */
    private String content;

    /** 文件所有者 */
    private Long userId;

    /** 文件 MIME 类型 */
    private String fileType;
}
