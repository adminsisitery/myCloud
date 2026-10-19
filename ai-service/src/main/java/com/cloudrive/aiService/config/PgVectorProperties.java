package com.cloudrive.aiService.config;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Component
@Validated
@ConfigurationProperties("ai.pgvector")
public class PgVectorProperties {
    @NotBlank private String host = "localhost";
    @Min(1) @Max(65535) private int port = 5432;
    @NotBlank private String user = "mycloud";
    @NotBlank private String password;
    @NotBlank private String database = "mycloud";
    @Pattern(regexp = "[a-zA-Z_][a-zA-Z0-9_]*") private String table = "file_segment";
    private boolean createTable = false;
}
