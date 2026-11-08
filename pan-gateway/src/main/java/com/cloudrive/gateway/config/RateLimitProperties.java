package com.cloudrive.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关限流配置。
 * 全局限流（每用户令牌桶）参数在 yml 的 default-filters 中声明，
 * 这里只承载登录/注册这类必须在入口处加固的敏感接口规则。
 */
@Component
@ConfigurationProperties(prefix = "pan.rate-limit")
public class RateLimitProperties {

    private final Login login = new Login();

    public Login getLogin() {
        return login;
    }

    public static class Login {

        /**
         * 是否启用敏感接口加固限流
         */
        private boolean enabled = true;

        /**
         * 单个窗口内允许的请求次数
         */
        private int permits = 5;

        /**
         * 窗口长度（秒）
         */
        private int windowSeconds = 60;

        /**
         * 需要加固的路径（Ant 风格）
         */
        private List<String> paths = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPermits() {
            return permits;
        }

        public void setPermits(int permits) {
            this.permits = permits;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public List<String> getPaths() {
            return paths;
        }

        public void setPaths(List<String> paths) {
            this.paths = paths == null ? new ArrayList<>() : new ArrayList<>(paths);
        }
    }
}
