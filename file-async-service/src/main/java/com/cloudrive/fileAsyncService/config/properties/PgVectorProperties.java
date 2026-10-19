package com.cloudrive.fileAsyncService.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PG 向量库配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.pgvector")
public class PgVectorProperties {

    private String host = "localhost";

    private int port = 5432;

    private String user = "mycloud";

    private String password;

    private String database = "mycloud";

    /** 向量表名 */
    private String table = "file_segment";

    /** 是否自动建表（生产环境建议 false，由 DBA 建表） */
    private boolean createTable = false;
}
