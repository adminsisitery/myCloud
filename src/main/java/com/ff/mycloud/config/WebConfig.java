//package com.ff.mycloud.config;
//
///**
// * @description: TODO
// * @author 飞飞
// * @date 2026/3/6 16:21
// * @version 1.0
// */
//import com.ff.mycloud.Interceptor.CorsInterceptor;
//import org.springframework.context.annotation.Configuration;
////import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
//import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
//
//@Configuration
//public class WebConfig implements WebMvcConfigurer {
//    // 注入拦截器
//    private final CorsInterceptor corsInterceptor;
//
//    // 构造器注入（Spring自动装配）
//    public WebConfig(CorsInterceptor corsInterceptor) {
//        this.corsInterceptor = corsInterceptor;
//    }
//
//    @Override
//    public void addInterceptors(InterceptorRegistry registry) {
//        // 注册拦截器，拦截所有请求，优先级最高
//        registry.addInterceptor(corsInterceptor)
//                .order(-999); // 高优先级，确保先执行
//    }
//}
