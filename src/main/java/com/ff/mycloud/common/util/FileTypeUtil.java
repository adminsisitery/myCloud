package com.ff.mycloud.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件类型工具类，用于根据文件名或扩展名确定文件的MIME类型
 */
public class FileTypeUtil {

    private static final Logger logger = LoggerFactory.getLogger(FileTypeUtil.class);
    
    // 文件扩展名到MIME类型的映射
    private static final Map<String, String> MIME_TYPE_MAP = new HashMap<>();
    
    static {
        // 图片文件
        MIME_TYPE_MAP.put("jpg", MediaType.IMAGE_JPEG_VALUE);
        MIME_TYPE_MAP.put("jpeg", MediaType.IMAGE_JPEG_VALUE);
        MIME_TYPE_MAP.put("png", MediaType.IMAGE_PNG_VALUE);
        MIME_TYPE_MAP.put("gif", MediaType.IMAGE_GIF_VALUE);
        MIME_TYPE_MAP.put("bmp", "image/bmp");
        MIME_TYPE_MAP.put("svg", "image/svg+xml");
        MIME_TYPE_MAP.put("webp", "image/webp");
        
        // 文档文件
        MIME_TYPE_MAP.put("pdf", MediaType.APPLICATION_PDF_VALUE);
        MIME_TYPE_MAP.put("doc", "application/msword");
        MIME_TYPE_MAP.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        MIME_TYPE_MAP.put("xls", "application/vnd.ms-excel");
        MIME_TYPE_MAP.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        MIME_TYPE_MAP.put("ppt", "application/vnd.ms-powerpoint");
        MIME_TYPE_MAP.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        MIME_TYPE_MAP.put("txt", MediaType.TEXT_PLAIN_VALUE);
        MIME_TYPE_MAP.put("rtf", "application/rtf");
        
        // 音频文件
        MIME_TYPE_MAP.put("mp3", "audio/mpeg");
        MIME_TYPE_MAP.put("wav", "audio/wav");
        MIME_TYPE_MAP.put("ogg", "audio/ogg");
        MIME_TYPE_MAP.put("flac", "audio/flac");
        MIME_TYPE_MAP.put("aac", "audio/aac");
        
        // 视频文件
        MIME_TYPE_MAP.put("mp4", "video/mp4");
        MIME_TYPE_MAP.put("avi", "video/x-msvideo");
        MIME_TYPE_MAP.put("mkv", "video/x-matroska");
        MIME_TYPE_MAP.put("mov", "video/quicktime");
        MIME_TYPE_MAP.put("wmv", "video/x-ms-wmv");
        MIME_TYPE_MAP.put("webm", "video/webm");
        
        // 压缩文件
        MIME_TYPE_MAP.put("zip", "application/zip");
        MIME_TYPE_MAP.put("rar", "application/x-rar-compressed");
        MIME_TYPE_MAP.put("7z", "application/x-7z-compressed");
        MIME_TYPE_MAP.put("tar", "application/x-tar");
        MIME_TYPE_MAP.put("gz", "application/gzip");
        
        // 代码文件
        MIME_TYPE_MAP.put("html", MediaType.TEXT_HTML_VALUE);
        MIME_TYPE_MAP.put("css", "text/css");
        MIME_TYPE_MAP.put("js", "application/javascript");
        MIME_TYPE_MAP.put("json", MediaType.APPLICATION_JSON_VALUE);
        MIME_TYPE_MAP.put("xml", MediaType.APPLICATION_XML_VALUE);
        MIME_TYPE_MAP.put("java", "text/x-java-source");
        MIME_TYPE_MAP.put("py", "text/x-python");
        MIME_TYPE_MAP.put("c", "text/x-c");
        MIME_TYPE_MAP.put("cpp", "text/x-c++");
        
        // 其他常见文件类型
        MIME_TYPE_MAP.put("exe", "application/x-msdownload");
        MIME_TYPE_MAP.put("dll", "application/x-msdownload");
        MIME_TYPE_MAP.put("iso", "application/x-iso9660-image");
    }
    
    /**
     * 根据文件名获取MIME类型
     *
     * @param filename 文件名
     * @return MIME类型字符串，如果无法确定则返回 application/octet-stream
     */
    public static String getContentTypeFromFileName(String filename) {
        if (filename == null || filename.isEmpty()) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            // 没有扩展名或扩展名为空
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        
        String extension = filename.substring(lastDotIndex + 1).toLowerCase();
        String mimeType = MIME_TYPE_MAP.get(extension);
        
        if (mimeType == null) {
            logger.debug("未知文件类型: {}", extension);
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        
        return mimeType;
    }

}
