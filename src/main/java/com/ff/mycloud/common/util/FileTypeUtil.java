package com.ff.mycloud.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

/**
 * 文件类型工具类，用于根据文件名或扩展名确定文件的MIME类型。
 * 支持图片、文档、音视频、压缩包及各类代码/文本文件的类型识别，
 * 并提供文件预览支持判断。
 *
 * @author 飞飞
 * @date 2026/6/27
 */
public class FileTypeUtil {

    private static final Logger logger = LoggerFactory.getLogger(FileTypeUtil.class);
    
    // 文件扩展名到MIME类型的映射
    private static final Map<String, String> MIME_TYPE_MAP = new HashMap<>();

    static {
        // ==================== 图片文件 ====================
        MIME_TYPE_MAP.put("jpg", MediaType.IMAGE_JPEG_VALUE);
        MIME_TYPE_MAP.put("jpeg", MediaType.IMAGE_JPEG_VALUE);
        MIME_TYPE_MAP.put("png", MediaType.IMAGE_PNG_VALUE);
        MIME_TYPE_MAP.put("gif", MediaType.IMAGE_GIF_VALUE);
        MIME_TYPE_MAP.put("bmp", "image/bmp");
        MIME_TYPE_MAP.put("svg", "image/svg+xml");
        MIME_TYPE_MAP.put("webp", "image/webp");
        MIME_TYPE_MAP.put("ico", "image/x-icon");
        MIME_TYPE_MAP.put("tif", "image/tiff");
        MIME_TYPE_MAP.put("tiff", "image/tiff");

        // ==================== 文档文件 ====================
        MIME_TYPE_MAP.put("pdf", MediaType.APPLICATION_PDF_VALUE);
        MIME_TYPE_MAP.put("doc", "application/msword");
        MIME_TYPE_MAP.put("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        MIME_TYPE_MAP.put("xls", "application/vnd.ms-excel");
        MIME_TYPE_MAP.put("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        MIME_TYPE_MAP.put("ppt", "application/vnd.ms-powerpoint");
        MIME_TYPE_MAP.put("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        MIME_TYPE_MAP.put("rtf", "application/rtf");

        // ==================== 音视频/压缩包（仅保留MIME映射，不加入预览白名单） ====================
        MIME_TYPE_MAP.put("mp3", "audio/mpeg");
        MIME_TYPE_MAP.put("wav", "audio/wav");
        MIME_TYPE_MAP.put("ogg", "audio/ogg");
        MIME_TYPE_MAP.put("flac", "audio/flac");
        MIME_TYPE_MAP.put("aac", "audio/aac");
        MIME_TYPE_MAP.put("mp4", "video/mp4");
        MIME_TYPE_MAP.put("avi", "video/x-msvideo");
        MIME_TYPE_MAP.put("mkv", "video/x-matroska");
        MIME_TYPE_MAP.put("mov", "video/quicktime");
        MIME_TYPE_MAP.put("wmv", "video/x-ms-wmv");
        MIME_TYPE_MAP.put("webm", "video/webm");
        MIME_TYPE_MAP.put("zip", "application/zip");
        MIME_TYPE_MAP.put("rar", "application/x-rar-compressed");
        MIME_TYPE_MAP.put("7z", "application/x-7z-compressed");
        MIME_TYPE_MAP.put("tar", "application/x-tar");
        MIME_TYPE_MAP.put("gz", "application/gzip");

        // ==================== 核心：代码/文本文件（统一用 text/plain，防乱码，防白屏） ====================
        // 通用文本
        MIME_TYPE_MAP.put("txt", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("json", "application/json;charset=UTF-8");
        MIME_TYPE_MAP.put("md", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("markdown", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("csv", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("ini", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("cfg", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("conf", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("log", "text/plain;charset=UTF-8");

        // Web 开发
        MIME_TYPE_MAP.put("html", "text/html;charset=UTF-8");
        MIME_TYPE_MAP.put("htm", "text/html;charset=UTF-8");
        MIME_TYPE_MAP.put("css", "text/css;charset=UTF-8");
        MIME_TYPE_MAP.put("scss", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("less", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("js", "application/javascript;charset=UTF-8");
        MIME_TYPE_MAP.put("jsx", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("ts", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("tsx", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("vue", "text/plain;charset=UTF-8");

        // Java 生态
        MIME_TYPE_MAP.put("java", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("jsp", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("jspx", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("properties", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("gradle", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("kt", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("kts", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("groovy", "text/plain;charset=UTF-8");

        // Python 生态
        MIME_TYPE_MAP.put("py", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("pyw", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("ipynb", "application/json;charset=UTF-8");

        // C/C++ 生态
        MIME_TYPE_MAP.put("c", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("cpp", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("cc", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("h", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("hpp", "text/plain;charset=UTF-8");

        // C# 生态
        MIME_TYPE_MAP.put("cs", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("xaml", "application/xml;charset=UTF-8");

        // 其他后端/脚本
        MIME_TYPE_MAP.put("php", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("rb", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("go", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("rs", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("swift", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("scala", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("sh", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("bash", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("bat", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("cmd", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("ps1", "text/plain;charset=UTF-8");

        // 配置/数据
        MIME_TYPE_MAP.put("xml", "application/xml;charset=UTF-8");
        MIME_TYPE_MAP.put("yaml", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("yml", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("toml", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("sql", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("graphql", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("gql", "text/plain;charset=UTF-8");
        MIME_TYPE_MAP.put("proto", "text/plain;charset=UTF-8");
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


    /**
     * 直接根据无点文件后缀获取MIME类型（仅处理无点后缀）
     *
     * @param fileSuffix 无点文件后缀（如：png、jpg、pdf、txt）
     * @return MIME类型字符串，未知类型返回 application/octet-stream
     */
    public static String getContentTypeFromFileSuffix(String fileSuffix) {
        // 空值处理
        if (fileSuffix == null || fileSuffix.trim().isEmpty()) {
            logger.debug("文件后缀为空，返回默认MIME类型");
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        // 直接转小写（兼容大小写，如PNG/Jpg）
        String lowerSuffix = fileSuffix.trim().toLowerCase();

        // 获取MIME类型
        String mimeType = MIME_TYPE_MAP.get(lowerSuffix);

        // 未知类型处理
        if (mimeType == null) {
            logger.debug("未知文件后缀: {}", lowerSuffix);
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return mimeType;
    }

    /**
     * 判断指定后缀的文件是否支持在线预览。
     * 支持预览的类型包括：图片（jpg/png/gif等）、文本/代码文件、PDF、音频、视频。
     *
     * @param suffix 文件后缀（不含点号）
     * @return true表示支持预览，false表示不支持
     */
    public static boolean isPreviewSupported(String suffix) {
        if (suffix == null) {
            return false;
        }

        String ext = suffix.toLowerCase();

        // 1. 图片类型
        if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("gif")
                || ext.equals("bmp") || ext.equals("webp") || ext.equals("svg")
                || ext.equals("ico") || ext.equals("tif") || ext.equals("tiff")) {
            return true;
        }

        // 2. 文本/代码类型
        if (ext.equals("txt") || ext.equals("json") || ext.equals("md") || ext.equals("markdown")
                || ext.equals("csv") || ext.equals("ini") || ext.equals("cfg") || ext.equals("conf") || ext.equals("log")
                || ext.equals("html") || ext.equals("htm") || ext.equals("css") || ext.equals("scss") || ext.equals("less")
                || ext.equals("js") || ext.equals("jsx") || ext.equals("ts") || ext.equals("tsx") || ext.equals("vue")
                || ext.equals("java") || ext.equals("jsp") || ext.equals("jspx") || ext.equals("properties")
                || ext.equals("gradle") || ext.equals("kt") || ext.equals("kts") || ext.equals("groovy")
                || ext.equals("py") || ext.equals("pyw") || ext.equals("ipynb")
                || ext.equals("c") || ext.equals("cpp") || ext.equals("cc") || ext.equals("h") || ext.equals("hpp")
                || ext.equals("cs") || ext.equals("xaml")
                || ext.equals("php") || ext.equals("rb") || ext.equals("go") || ext.equals("rs") || ext.equals("swift")
                || ext.equals("scala") || ext.equals("sh") || ext.equals("bash") || ext.equals("bat") || ext.equals("cmd") || ext.equals("ps1")
                || ext.equals("xml") || ext.equals("yaml") || ext.equals("yml") || ext.equals("toml")
                || ext.equals("sql") || ext.equals("graphql") || ext.equals("gql") || ext.equals("proto")) {
            return true;
        }

        // 3. PDF 类型
        if (ext.equals("pdf")) {
            return true;
        }

        // 4. 音频类型（OSS 预签名 URL + <audio> 标签，浏览器原生支持流播放）
        if (ext.equals("mp3") || ext.equals("wav") || ext.equals("ogg") || ext.equals("flac")
                || ext.equals("aac") || ext.equals("m4a") || ext.equals("wma")) {
            return true;
        }

        // 5. 视频类型（OSS 支持 Range 请求，浏览器 <video> 可拖进度条）
        if (ext.equals("mp4") || ext.equals("avi") || ext.equals("mkv") || ext.equals("mov")
                || ext.equals("wmv") || ext.equals("webm") || ext.equals("flv") || ext.equals("m4v")) {
            return true;
        }

        // 其他均不支持预览
        return false;
    }

    /**
     * 根据文件后缀判断预览类型分类，用于前端选择不同渲染组件。
     *
     * @param suffix 文件后缀（不含点号）
     * @return image | video | audio | pdf | text | unknown
     */
    public static String getPreviewCategory(String suffix) {
        if (suffix == null) {
            return "unknown";
        }

        String ext = suffix.toLowerCase();

        if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("gif")
                || ext.equals("bmp") || ext.equals("webp") || ext.equals("svg")
                || ext.equals("ico") || ext.equals("tif") || ext.equals("tiff")) {
            return "image";
        }

        if (ext.equals("mp4") || ext.equals("avi") || ext.equals("mkv") || ext.equals("mov")
                || ext.equals("wmv") || ext.equals("webm") || ext.equals("flv") || ext.equals("m4v")) {
            return "video";
        }

        if (ext.equals("mp3") || ext.equals("wav") || ext.equals("ogg") || ext.equals("flac")
                || ext.equals("aac") || ext.equals("m4a") || ext.equals("wma")) {
            return "audio";
        }

        if (ext.equals("pdf")) {
            return "pdf";
        }

        // 文本/代码兜底
        if (isPreviewSupported(ext)) {
            return "text";
        }

        return "unknown";
    }

    /**
     * 根据文件后缀返回分类ID，对应 t_file_category.id
     *
     * @param suffix 文件后缀（无点号）
     * @return 1-图片 2-文档 3-视频 4-音频 5-压缩包 6-代码 7-其他
     */
    public static int getCategoryId(String suffix) {
        if (suffix == null) {
            return 7;
        }
        String ext = suffix.toLowerCase();

        if ("jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext) || "gif".equals(ext)
                || "bmp".equals(ext) || "webp".equals(ext) || "svg".equals(ext)
                || "ico".equals(ext) || "tif".equals(ext) || "tiff".equals(ext)) {
            return 1;
        }
        if ("pdf".equals(ext) || "doc".equals(ext) || "docx".equals(ext)
                || "xls".equals(ext) || "xlsx".equals(ext) || "ppt".equals(ext)
                || "pptx".equals(ext) || "txt".equals(ext) || "md".equals(ext)
                || "csv".equals(ext)) {
            return 2;
        }
        if ("mp4".equals(ext) || "avi".equals(ext) || "mkv".equals(ext)
                || "mov".equals(ext) || "wmv".equals(ext) || "webm".equals(ext)
                || "flv".equals(ext) || "m4v".equals(ext)) {
            return 3;
        }
        if ("mp3".equals(ext) || "wav".equals(ext) || "ogg".equals(ext)
                || "flac".equals(ext) || "aac".equals(ext) || "m4a".equals(ext)
                || "wma".equals(ext)) {
            return 4;
        }
        if ("zip".equals(ext) || "rar".equals(ext) || "7z".equals(ext)
                || "tar".equals(ext) || "gz".equals(ext)) {
            return 5;
        }
        if ("java".equals(ext) || "py".equals(ext) || "js".equals(ext) || "ts".equals(ext)
                || "html".equals(ext) || "css".equals(ext) || "go".equals(ext)
                || "rs".equals(ext) || "c".equals(ext) || "cpp".equals(ext)
                || "json".equals(ext) || "xml".equals(ext) || "yaml".equals(ext)
                || "yml".equals(ext) || "sql".equals(ext) || "sh".equals(ext)) {
            return 6;
        }
        return 7;
    }
}
