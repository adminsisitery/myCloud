//package com.ff.mycloud.Interceptor;
//
//import jakarta.servlet.http.HttpServletRequest;
//import jakarta.servlet.http.HttpServletResponse;
//import org.springframework.stereotype.Component;
//import org.springframework.web.servlet.HandlerInterceptor;
//
///**
// * 跨域拦截器。
// * 实现HandlerInterceptor接口，在请求到达控制器之前进行拦截校验。
// * 主要功能：拦截并禁止对 /json 路径的访问，其他请求正常放行。
// *
// * @author 飞飞
// * @date 2026/06/27
// */
//
//@Component
//public class CorsInterceptor implements HandlerInterceptor {
//    /**
//     * 请求预处理拦截方法。
//     * 在控制器方法执行前调用，用于判断请求是否允许访问。
//     * 当请求路径为 /json 时返回403禁止访问，其他路径正常放行。
//     *
//     * @param request  当前HTTP请求对象
//     * @param response 当前HTTP响应对象
//     * @param handler  请求处理器对象
//     * @return true表示放行请求，false表示拦截请求
//     * @throws Exception 处理过程中可能抛出的异常
//     */
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
