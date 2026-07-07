package com.ff.mycloud.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 文件预览视图对象
 *
 * @author 飞飞
 * @date 2026/07/01
 */
@Data
@AllArgsConstructor
public class PreviewVO {

    /** OSS 预签名 URL */
    private String url;

    /** 预览分类：image | video | audio | pdf | text | unknown */
    private String category;

    /** 文件 MIME 类型 */
    private String mimeType;

    /** 文件名（含后缀） */
    private String filename;

    /** 文件大小（字节） */
    private Long fileSize;
}
