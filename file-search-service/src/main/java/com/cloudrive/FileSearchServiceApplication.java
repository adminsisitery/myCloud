package com.cloudrive;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration;

/**
 * 文件搜索微服务启动类
 * <p>
 * 启动类放在 {@code com.cloudrive} 根包下，保证能扫描到 pan-common 的限流切面等公共组件。
 * 搜索策略由 {@code file.search} 配置决定（Mysql 默认 / ES）。
 * </p>
 * <p>
 * 排除 {@link ElasticsearchRepositoriesAutoConfiguration}：它默认无条件扫描 ES repository，
 * 而本项目在 {@code file.search=Mysql}（默认）时不应初始化 ES，否则启动时连不上 ES 直接失败。
 * ES repository 改由 {@link com.cloudrive.fileSearchService.config.ElasticsearchRepositoryConfig}
 * 在 {@code file.search=ES} 时按需启用。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/20
 */
@SpringBootApplication(exclude = ElasticsearchRepositoriesAutoConfiguration.class)
@MapperScan("com.cloudrive.fileSearchService.repositorys")
public class FileSearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FileSearchServiceApplication.class, args);
    }
}
