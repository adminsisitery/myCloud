package com.ff.mycloud.mq.consumer;

import com.ff.mycloud.ai.agent.tool.AgentDocumentReader;
import com.ff.mycloud.model.entity.FileDocument;
import com.ff.mycloud.model.entity.FileInfo;
import com.ff.mycloud.mq.producer.RabbitMqService;
import org.springframework.stereotype.Component;
import java.util.*;

/** Restores both search backends from one bounded extraction of the original object. */
@Component
public class FileIndexRebuilder {
    private final AgentDocumentReader reader;
    private final RabbitMqService messages;
    public FileIndexRebuilder(AgentDocumentReader reader, RabbitMqService messages) {
        this.reader = reader;
        this.messages = messages;
    }
    public String extract(FileInfo file) {
        if (!Set.of("pdf", "docx", "doc", "txt", "md", "csv", "xlsx", "pptx")
                .contains(Objects.toString(file.getFileSuffix(), "").toLowerCase(Locale.ROOT))) return "";
        try { return reader.read(file); }
        catch (Exception error) { throw new IllegalStateException("INDEX_TEXT_REBUILD_FAILED", error); }
    }
    public void restoreKeywords(FileInfo file, String text) {
        FileDocument document = new FileDocument();
        document.setId(file.getId());
        document.setUserId(file.getUserId());
        document.setFileName(file.getFilename() + "." + Objects.toString(file.getFileSuffix(), ""));
        document.setContent(text);
        messages.sendInsertMessage(document);
    }
}
