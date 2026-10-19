package com.cloudrive;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 用户服务启动类。
 * <p>
 * 包名刻意放在 {@code com.cloudrive} 而不是 {@code com.cloudrive.userService}：
 * 共享组件在 {@code com.cloudrive.pan_common} 下，Spring Boot 默认只扫描启动类所在包及其子包，
 * 放在 {@code com.cloudrive} 才能把 {@code GlobalExceptionHandler}、{@code RateLimitAspect}、
 * {@code RequestTraceFilter} 这些公共 bean 一并加载进来。
 * </p>
 *
 * @author 飞飞
 * @date 2026/10/19
 */
@SpringBootApplication
@MapperScan("com.cloudrive.userService.mapper")
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
