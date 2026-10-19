package com.cloudrive.aiService.agent.action;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.springframework.web.multipart.MultipartFile;

/** Bounded, in-memory generated document; reuses the existing authenticated upload path. */
final class AgentDocumentFile implements MultipartFile {
    private final String name;
    private final byte[] bytes;
    AgentDocumentFile(String name, String content) { this.name=name; this.bytes=content.getBytes(StandardCharsets.UTF_8); }
    public String getName() { return "file"; }
    public String getOriginalFilename() { return name; }
    public String getContentType() { return name.endsWith(".csv") ? "text/csv" : "text/plain"; }
    public boolean isEmpty() { return bytes.length==0; }
    public long getSize() { return bytes.length; }
    public byte[] getBytes() { return bytes.clone(); }
    public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
    public void transferTo(File dest) throws IOException { Files.write(dest.toPath(),bytes); }
}
