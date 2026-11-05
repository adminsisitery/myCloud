package com.cloudrive.gateway.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuthProperties 单元测试（纯逻辑，无需 Spring 上下文 / mock）
 */
class AuthPropertiesTest {

    @Test
    void isExcluded_精确匹配白名单() {
        AuthProperties props = new AuthProperties();
        props.setExcludePaths(List.of("/api/user/login", "/api/user/register"));

        assertThat(props.isExcluded("/api/user/login")).isTrue();
        assertThat(props.isExcluded("/api/user/info")).isFalse();
    }

    @Test
    void isExcluded_通配符匹配() {
        AuthProperties props = new AuthProperties();
        props.setExcludePaths(List.of("/api/**/public/**"));

        assertThat(props.isExcluded("/api/a/public/b")).isTrue();
        assertThat(props.isExcluded("/api/a/private/b")).isFalse();
    }

    @Test
    void setExcludePaths_传入null归一化为空列表() {
        AuthProperties props = new AuthProperties();
        props.setExcludePaths(null);

        assertThat(props.isExcluded("/anything")).isFalse();
    }

    @Test
    void isExcluded_无白名单时全部不豁免() {
        AuthProperties props = new AuthProperties();

        assertThat(props.isExcluded("/api/user/login")).isFalse();
    }
}
