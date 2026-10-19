package com.cloudrive.fileAsyncService.service;

import com.cloudrive.fileAsyncService.model.entity.FileInfo;
import com.cloudrive.fileAsyncService.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 文档文本提取器（移植单体的 AgentDocumentReader）
 * <p>
 * 只支持白名单后缀；最多读取 5MB 原始内容，提取文本上限 20 万字符，
 * 避免超大文件把索引链路的内存/CPU 打满。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentTextReader {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 200_000;

    private static final Set<String> SUPPORTED_SUFFIXES =
            Set.of("pdf", "docx", "doc", "txt", "md", "csv", "xlsx", "pptx");

    private static final Set<String> PLAIN_TEXT_SUFFIXES = Set.of("txt", "md", "csv");

    private final StorageService storageService;

    /**
     * 读取并提取文本
     *
     * @param file 文件信息
     * @return 提取到的文本
     */
    public String read(FileInfo file) {
        if (file == null || file.getPath() == null) {
            throw new IllegalArgumentException("文件路径缺失");
        }
        String suffix = normalizeSuffix(file.getFileSuffix());
        if (!SUPPORTED_SUFFIXES.contains(suffix)) {
            throw new IllegalArgumentException("不支持提取文本的文件类型: " + suffix);
        }
        if (file.getFileSize() != null && file.getFileSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件过大，无法提取索引文本: " + file.getFileSize());
        }

        try (InputStream inputStream = storageService.getFile(file.getPath())) {
            byte[] bytes = inputStream.readNBytes((int) MAX_FILE_SIZE + 1);
            if (bytes.length > MAX_FILE_SIZE) {
                throw new IllegalArgumentException("文件过大，无法提取索引文本: " + file.getPath());
            }
            String text;
            if (PLAIN_TEXT_SUFFIXES.contains(suffix)) {
                text = new String(bytes, StandardCharsets.UTF_8);
            } else {
                text = new Tika().parseToString(new ByteArrayInputStream(bytes));
            }
            if (text == null) {
                return "";
            }
            if (text.length() > MAX_TEXT_LENGTH) {
                throw new IllegalArgumentException("索引文本过长: " + text.length());
            }
            return text;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("读取文件文本失败: fileId={}, path={}", file.getId(), file.getPath(), e);
            throw new IllegalStateException("INDEX_TEXT_READ_FAILED", e);
        }
    }

    static String normalizeSuffix(String suffix) {
        return suffix == null ? "" : suffix.toLowerCase(Locale.ROOT);
    }
}
