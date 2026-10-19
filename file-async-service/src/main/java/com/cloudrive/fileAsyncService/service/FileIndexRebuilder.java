package com.cloudrive.fileAsyncService.service;

import com.cloudrive.fileAsyncService.model.entity.FileInfo;
import com.cloudrive.fileAsyncService.mq.producer.RabbitMqService;
import com.cloudrive.pan_common.domain.mq.FileIndexMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * 索引重建辅助（移植单体的 FileIndexRebuilder）
 * <p>
 * PG 重建消息只带 fileId，消费端通过本类从 OSS 重新提取文本；
 * 重建成功后把最新文本回投 ES.insert.queue，保证 ES 与 PG 数据一致。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileIndexRebuilder {

    private static final Set<String> INDEXABLE_SUFFIXES =
            Set.of("pdf", "docx", "doc", "txt", "md", "csv", "xlsx", "pptx");

    private final DocumentTextReader documentTextReader;
    private final RabbitMqService messages;

    /**
     * 从对象存储重建文件索引文本
     *
     * @param file 文件信息
     * @return 提取文本；类型不支持时返回空串
     */
    public String extract(FileInfo file) {
        String suffix = file.getFileSuffix() == null ? "" : file.getFileSuffix().toLowerCase(Locale.ROOT);
        if (!INDEXABLE_SUFFIXES.contains(suffix)) {
            return "";
        }
        try {
            return documentTextReader.read(file);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("重建索引文本失败: fileId={}", file.getId(), e);
            throw new IllegalStateException("INDEX_TEXT_REBUILD_FAILED", e);
        }
    }

    /**
     * 重建成功后回投 ES 索引消息
     *
     * @param file 文件信息
     * @param text 重建文本
     */
    public void restoreKeywords(FileInfo file, String text) {
        FileIndexMessage message = new FileIndexMessage();
        message.setId(file.getId());
        message.setUserId(file.getUserId());
        String suffix = file.getFileSuffix() == null || file.getFileSuffix().isBlank()
                ? ""
                : "." + file.getFileSuffix();
        message.setFileName(file.getFilename() + suffix);
        message.setContent(text);
        message.setFileType(file.getFileType());
        messages.sendInsertMessage(message);
    }
}
