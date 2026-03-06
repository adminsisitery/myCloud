//package com.ff.mycloud.Interceptor;
//
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.springframework.stereotype.Component;
//import org.springframework.web.servlet.HandlerInterceptor;
//
///**
// * @description: TODO
// * @author 飞飞
// * @date 2026/3/6 16:15
// * @version 1.0
// */
//
//@Component
//public class CorsInterceptor implements HandlerInterceptor {
//    @Override
//    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        if (request.getRequestURI().equals("/json")) {
//            response.setStatus(403); // 403禁止访问
//            response.getWriter().write("禁止访问/json路径");
//            return false; // 拒绝请求
//        }
//        return true; // 其他请求正常放行
//    }
//
//
//}
