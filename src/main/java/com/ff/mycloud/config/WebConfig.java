//package com.ff.mycloud.config;
//
///**
// * Web MVC 配置类（已废弃）
// * <p>
// * 原用于注册 CORS 跨域拦截器，现已被其他跨域方案替代。
// * 保留代码以供参考，不再启用。
// * </p>
// *
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
//    // 注入跨域拦截器
//    private final CorsInterceptor corsInterceptor;
//
//    // 构造器注入（Spring自动装配）
//    public WebConfig(CorsInterceptor corsInterceptor) {
//        this.corsInterceptor = corsInterceptor;
//    }
//
//    @Override
//    public void addInterceptors(InterceptorRegistry registry) {
//        // 注册跨域拦截器，拦截所有请求，优先级最高
//        registry.addInterceptor(corsInterceptor)
//                .order(-999); // 高优先级，确保在其他拦截器之前执行
//    }
//}
