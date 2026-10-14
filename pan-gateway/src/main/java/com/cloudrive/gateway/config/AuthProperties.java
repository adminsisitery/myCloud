package com.cloudrive.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "pan.user")
public class AuthProperties {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private List<String> excludePaths = new ArrayList<>();

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths == null ? new ArrayList<>() : new ArrayList<>(excludePaths);
    }

    public boolean isExcluded(String path) {
        return excludePaths.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }
}