package com.ff.mycloud.common.exceotion;

import cn.dev33.satoken.exception.NotLoginException;
import com.ff.mycloud.common.constant.CommonConstants;
import com.ff.mycloud.enums.ErrorCode;
import com.ff.mycloud.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
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

import static cn.dev33.satoken.SaManager.log;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 未登录异常
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLoginException(NotLoginException e) {
        logger.error("Authentication error: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Result.error(401, "未登录或token无效"));
    }

    // 全局异常处理器里新增：打印请求URL
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request) {
        log.error("静态资源找不到！请求URL：{}，错误信息：{}", request.getRequestURL(), e.getMessage());
        return Result.error(500, "请求的资源不存在");
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
        logger.error("Business error: code={}, message={}", 
                e.getCode(), e.getMessage());
        
        // 根据错误码映射到合适的HTTP状态码
        HttpStatus httpStatus = mapErrorCodeToHttpStatus(e.getCode());
        
        // 返回统一格式的Result
        return ResponseEntity
                .status(httpStatus)
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    /**
     * 文件上传大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e) {
        logger.error("File upload error: file size exceeded the maximum allowed size", e);
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
     * 处理 @Valid 参数校验失败异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        // 获取第一个错误信息
        FieldError fieldError = e.getBindingResult().getFieldErrors().get(0);
        String message = fieldError.getDefaultMessage();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.error(CommonConstants.StatusCode.BAD_REQUEST, message));
    }

    /**
     * 处理 @Validated 方法级参数校验异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
        // 取第一条错误信息
        String message = e.getConstraintViolations().iterator().next().getMessage();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.error(CommonConstants.StatusCode.BAD_REQUEST, message));
    }

    /**
     * 其他异常
     */ 
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception e) {
        logger.error("Unexpected error: ", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(500, e.getMessage()));
    }
    
    /**
     * 将业务错误码映射到HTTP状态码
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