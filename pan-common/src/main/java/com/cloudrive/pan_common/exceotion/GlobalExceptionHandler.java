package com.cloudrive.pan_common.exceotion;

import cn.dev33.satoken.exception.NotLoginException;

import com.cloudrive.pan_common.constant.CommonConstants;
import com.cloudrive.pan_common.enums.ErrorCode;
import com.cloudrive.pan_common.result.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;


/**
 * 全局异常处理器，统一拦截Controller层抛出的异常并返回标准化的Result响应。
 * 涵盖未登录、业务异常、文件上传超限、参数校验失败及未知异常等场景。
 *
 * @author 飞飞
 * @date 2026/6/27
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<java.util.Map<String, Object>> handleHttpStatus(org.springframework.web.server.ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(java.util.Map.of("code", e.getStatusCode().value(), "message", e.getReason() == null ? "请求失败" : e.getReason()));
    }
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理未登录异常（Sa-Token）
     *
     * @param e 未登录异常
     * @return 401 未授权响应
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLoginException(NotLoginException e) {
        logger.warn("身份验证失败: type={}", e.getType());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Result.error(401, "未登录或token无效"));
    }



    /**
     * 处理业务异常，根据错误码映射到对应的HTTP状态码
     *
     * @param e 业务异常
     * @return 对应HTTP状态码的错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        if (mapErrorCodeToHttpStatus(e.getCode()).is5xxServerError()) {
            logger.error("业务处理异常: code={}", e.getCode(), e);
        } else {
            logger.warn("业务请求拒绝: code={}", e.getCode());
        }
        
        // 根据错误码映射到合适的HTTP状态码
        HttpStatus httpStatus = mapErrorCodeToHttpStatus(e.getCode());
        
        // 返回统一格式的Result
        return ResponseEntity
                .status(httpStatus)
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    /**
     * 处理文件上传大小超过限制异常
     *
     * @param e 文件上传大小超限异常
     * @return 413 请求载荷过大响应
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        logger.warn("上传请求超过允许大小: maxBytes={}", e.getMaxUploadSize());
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Result.error(ErrorCode.FILE_TOO_LARGE.getHttpStatus(), ErrorCode.FILE_TOO_LARGE.getMessage()));
    }

    /**
     * 限流异常
     */
//    @ExceptionHandler(RateLimitExceededException.class)
//    public ResponseEntity<Result<Void>> handleRateLimitExceeded(RateLimitExceededException e) {
//        return ResponseEntity
//                .status(HttpStatus.TOO_MANY_REQUESTS)
//                .body(Result.error(429, "请求过于频繁，请稍后再试"));
//    }

    /**
     * 处理 @Valid 注解触发的参数校验失败异常（如请求体DTO校验）
     *
     * @param e 方法参数校验异常
     * @return 400 错误请求响应，包含第一条校验错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        // 获取第一个错误信息
        FieldError fieldError = e.getBindingResult().getFieldErrors().get(0);
        String message = fieldError.getDefaultMessage();
        logger.warn("参数校验失败: field={}, errorCount={}", fieldError.getField(), e.getBindingResult().getErrorCount());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.error(CommonConstants.StatusCode.BAD_REQUEST, message));
    }

    /**
     * 处理 @Validated 方法级参数校验异常（如Controller方法参数校验）
     *
     * @param e 约束违例异常
     * @return 400 错误请求响应，包含第一条校验错误信息
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        logger.warn("方法参数校验失败: violationCount={}", e.getConstraintViolations().size());
        // 取第一条错误信息
        String message = e.getConstraintViolations().iterator().next().getMessage();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.error(CommonConstants.StatusCode.BAD_REQUEST, message));
    }

    /**
     * 处理静态资源缺失（如 favicon.ico），不污染日志
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.notFound().build();
    }

    /**
     * 兜底处理所有未被上述处理器捕获的异常
     *
     * @param e 未知异常
     * @return 500 服务器内部错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        logger.error("Unexpected error: ", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, e.getMessage()));
    }
    
    /**
     * 将业务错误码映射为HTTP状态码，支持精确匹配和范围匹配
     *
     * @param errorCode 业务错误码
     * @return 对应的HTTP状态码
     */
    private HttpStatus mapErrorCodeToHttpStatus(int errorCode) {
        // 基于错误码的范围或特定值进行映射
        return switch (errorCode) {
            case 401 -> HttpStatus.UNAUTHORIZED; // 未授权，如密码错误
            case 403 -> HttpStatus.FORBIDDEN; // 权限不足
            case 404 -> HttpStatus.NOT_FOUND; // 资源未找到
            case 400 -> HttpStatus.BAD_REQUEST; // 请求参数错误
            case 413 -> HttpStatus.PAYLOAD_TOO_LARGE; // 请求实体过大
            case 429 -> HttpStatus.TOO_MANY_REQUESTS; // 请求频率过高
            default -> {
                if (errorCode >= 400 && errorCode < 500) {
                    yield HttpStatus.BAD_REQUEST; // 其他客户端错误
                } else {
                    yield HttpStatus.INTERNAL_SERVER_ERROR; // 服务器内部错误
                }
            }
        };
    }
} 
