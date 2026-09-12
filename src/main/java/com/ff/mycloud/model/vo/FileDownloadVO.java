package com.ff.mycloud.model.vo;

/** 已完成权限校验的下载描述，供 Controller 做流式和 Range 响应。 */
public record FileDownloadVO(String path, String filename, String contentType, long size) {
}
