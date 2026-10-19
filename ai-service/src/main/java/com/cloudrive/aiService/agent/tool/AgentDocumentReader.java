package com.cloudrive.aiService.agent.tool;

import com.cloudrive.aiService.model.entity.FileInfo;
import com.cloudrive.aiService.service.FileService;
import java.io.*;
import java.util.*;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

/** Reads original objects through the storage abstraction, never a model-provided URL/path. */
@Component
public class AgentDocumentReader {
    private final FileService files;
    public AgentDocumentReader(FileService files) { this.files=files; }
    public String read(FileInfo file) throws Exception {
        if (file.getFileSize()==null || file.getFileSize()>5*1024*1024 || file.getFileSize()<0)
            throw new IllegalArgumentException("全文读取限5MB以内文件");
        String suffix=Objects.toString(file.getFileSuffix(),"").toLowerCase(Locale.ROOT);
        if (!Set.of("pdf","docx","doc","txt","md","csv","xlsx","pptx").contains(suffix))
            throw new IllegalArgumentException("文件类型不支持全文提取");
        try (InputStream input=files.getFile(file.getPath())) {
            // Bound actual bytes as well as metadata; never read unlimited object data.
            byte[] bytes=input.readNBytes(5*1024*1024+1);
            if (bytes.length>5*1024*1024) throw new IllegalArgumentException("文件过大");
            if (Set.of("txt","md","csv").contains(suffix)) {
                String text=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);
                if(text.length()>200000) throw new IllegalArgumentException("文档过长，请使用片段检索");
                return text;
            }
            String text=new Tika().parseToString(new ByteArrayInputStream(bytes),new org.apache.tika.metadata.Metadata(),200001);
            if(text.length()>200000) throw new IllegalArgumentException("提取文本超限，不能声明全文读取");
            return text;
        }
    }
}
