package com.cloudrive.pan_common.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.cloudrive.pan_common.annotation.RateLimit;
import com.cloudrive.pan_common.constant.CommonConstants;
import com.cloudrive.pan_common.exceotion.RateLimitExceededException;
import com.cloudrive.pan_common.util.HttpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 限流切面，拦截标注了{@link RateLimit}注解的方法，
 * 基于Redisson的RRateLimiter实现分布式限流。支持用户、IP、全局三个维度的组合限流，
 * 以及限流后的降级处理。
 *
 * @author 飞飞
 * @date 2026/6/27
 */

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final RedissonClient redissonClient;

    public RateLimitAspect(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 环绕通知，对标注了@RateLimit注解的方法进行限流检查。
     * 根据注解配置的维度逐项检查令牌获取情况，任一维度未获取到令牌则触发限流处理。
     *
     * @param joinPoint 切点，用于获取方法信息和参数
     * @param rateLimit 限流注解实例
     * @return 原始方法的返回值或降级方法的返回值
     * @throws Throwable 当限流触发时可能抛出异常或原始方法异常
     */
    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable{
        //先根据切入点获得方法
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        //获得web上下文
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            log.warn("方法 {} 不在 Web 请求上下文中，跳过限流检查。", method.getName());
            return joinPoint.proceed();
        }
        //获得用户id
        HttpServletRequest request = requestAttributes.getRequest();
        // --- 预取用户信息和 IP ---
        String userId = null;
        boolean isUserLoggedIn = false;
        String clientIp;

        try{
            isUserLoggedIn = StpUtil.isLogin();
            if(isUserLoggedIn){
                userId = StpUtil.getLoginIdAsString();
            }
        }catch (Exception e){
            log.warn("获取登录状态或用户ID时出错. 方法: {}. 错误: {}", method.getName(), e.getMessage());
        }
        //获取ip
        try{
            clientIp = HttpUtil.getClientIp(request);
            if (!StringUtils.hasText(clientIp)||"unknown".equalsIgnoreCase(clientIp)) {
                clientIp = "unknown_ip";
            }
        }catch (Exception e){
            clientIp = "unknown_ip";
        }
        Set<RateLimit.Dimension> effectiveDimensions = new HashSet<>(Arrays.asList(rateLimit.dimensions()));

        //确认生效的维度 三个维度
        //看看用户是否登录 如果没有登录使用 ip维度

        if (effectiveDimensions.contains(RateLimit.Dimension.USER) && !isUserLoggedIn) {
            //使用ip维度
            log.debug("用户未登录，跳过方法 {} 的 USER 维度限流检查。", method.getName());
            effectiveDimensions.remove(RateLimit.Dimension.USER);
            effectiveDimensions.add(RateLimit.Dimension.IP);
        }
        if (effectiveDimensions.isEmpty()){
            //没有维度 直接放行
            log.warn("方法 {} 无生效的限流维度，直接放行。", method.getName());
            return joinPoint.proceed();
        }
        //循环查看限流
        for (RateLimit.Dimension dimension : effectiveDimensions) {

            //先拿到key
            String rateLimitKey = generateKey(dimension, method, userId, clientIp);
            boolean acquired;
            try {
                //获得注释里的速率
                double permitsPerSecond = rateLimit.permitsPerSecond();
                long rateLong = (long) permitsPerSecond;
                if (permitsPerSecond<0){
                    rateLong = CommonConstants.RateLimit.DEFAULT_RATE;
                    log.error("方法 {} 配置了无效的限流速率 {}，使用默认值 {}", method.getName(), rateLimit.permitsPerSecond(), rateLong);
                }else if (permitsPerSecond<1.0){
                    rateLong = 1L;
                    log.debug("方法 {} 配置了小于1的限流速率 {}，向上取整为 1/秒", method.getName(), rateLimit.permitsPerSecond());
                }

                RRateLimiter rateLimiter = redissonClient.getRateLimiter(rateLimitKey);
                //更新限流器配置
                rateLimiter.trySetRate(RateType.OVERALL,rateLong,1L, RateIntervalUnit.SECONDS);

                //尝试获得许可
                log.debug("尝试为 Key '{}' 获取许可. 超时: {} {}", rateLimitKey, rateLimit.timeout(), rateLimit.timeUnit());
                acquired = rateLimiter.tryAcquire(1,rateLimit.timeout(),rateLimit.timeUnit());

            } catch (RedisException e) {
                // 专门处理Redis相关异常
                log.error("执行限流检查时发生 Redis 错误 (Key: {}). 默认阻止请求.", rateLimitKey, e);
                throw new RuntimeException("限流服务异常，请稍后重试。", e);
            } catch (IllegalArgumentException e) {
                // 处理参数验证错误
                log.error("限流配置错误 (Key: {}): {}", rateLimitKey, e.getMessage());
                throw e; // 保留原始异常，允许上层处理
            } catch (Exception e) {
                // 处理所有其他错误
                log.error("执行限流检查时发生未知错误 (Key: {}). 默认阻止请求.", rateLimitKey, e);
                throw new RuntimeException("限流服务发生未知错误，请稍后重试。", e);
            }
            if (!acquired){
                //没拿到令牌 降级
                return handleRateLimitExceeded(joinPoint, method, rateLimit, dimension);

            }else {
                //拿到了 放行
                log.debug("成功获取 Key '{}' 的许可", rateLimitKey);
            }
        }
        return joinPoint.proceed();
    }

    /**
     * 处理限流触发后的降级逻辑。优先尝试调用注解中指定的降级方法，
     * 如果未指定降级方法或调用失败，则抛出注解中指定的异常类型。
     *
     * @param joinPoint 切点
     * @param method    被拦截的方法
     * @param rateLimit 限流注解
     * @param dimension 触发限流的维度
     * @return 降级方法的返回值
     * @throws Throwable 降级异常
     */
    private Object handleRateLimitExceeded(ProceedingJoinPoint joinPoint, Method method, RateLimit rateLimit, RateLimit.Dimension dimension) throws Throwable {
        log.warn("触发限流: 维度={}, 方法={}", dimension, method.getName()); // 记录更友好的日志

        //获得降级方法
        String fallback = rateLimit.fallback();
        if (StringUtils.hasText(fallback)) {
            try {
                Method fallbackMethod = joinPoint.getTarget().getClass().getMethod(fallback, method.getParameterTypes());
                if (!method.getReturnType().isAssignableFrom(fallbackMethod.getReturnType()) && !fallbackMethod.getReturnType().equals(void.class)) {
                    log.error("降级方法 {} 的返回类型 {} 与原方法 {} 的返回类型 {} 不兼容。", fallback, fallbackMethod.getReturnType().getName(), method.getName(), method.getReturnType().getName());
                    // 根据策略决定是抛异常还是继续尝试调用（可能在运行时失败）
                    // 此处选择抛出配置错误
                    throw new IllegalStateException("降级方法返回类型不兼容");
                }
                return fallbackMethod.invoke(joinPoint.getTarget(), joinPoint.getArgs());
            } catch (ReflectiveOperationException e) {
                log.error("降级方法调用失败: 方法={}, 错误={}", fallback, e.getMessage());
                throw new IllegalStateException("降级方法调用失败，未找到对应的方法或方法访问异常", e);
            }
        }
        String message = "请求过于频繁，请稍后再试。[维度: " + dimension + "]";
        try {
            // 查找接受String参数的构造函数
            throw rateLimit.exception().getConstructor(String.class).newInstance(message);
        } catch (ReflectiveOperationException e) {
            // 回退到默认异常
            throw new RateLimitExceededException(message);
        }

    }



    /**
     * 生成限流Key，格式为：{前缀}:{类名}.{方法名}:{维度}:{标识}
     *
     * @param dimension 限流维度
     * @param method    被拦截的方法
     * @param userId    用户ID（用户维度时使用）
     * @param clientIp  客户端IP（IP维度时使用）
     * @return 限流Key字符串
     */
    private String generateKey(RateLimit.Dimension dimension, Method method, String userId, String clientIp){
        StringBuilder sb = new StringBuilder(CommonConstants.RateLimit.KEY_PREFIX);
        sb.append(method.getDeclaringClass().getName()).append(".").append(method.getName());
        sb.append(switch (dimension){
            case RateLimit.Dimension.USER -> ":user:" + (userId != null ? userId : CommonConstants.RateLimit.ANONYMOUS_USER);
            case RateLimit.Dimension.IP -> ":ip:" + (clientIp != null ? clientIp : CommonConstants.RateLimit.UNKNOWN_IP);
            case RateLimit.Dimension.GLOBAL -> ":global";
            default -> ":global";
        });
        return sb.toString();
    }
}
