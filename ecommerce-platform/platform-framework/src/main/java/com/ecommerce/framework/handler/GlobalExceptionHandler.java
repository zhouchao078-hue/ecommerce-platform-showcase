package com.ecommerce.framework.handler;

import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * <p>
 * 使用 @RestControllerAdvice 统一拦截 Controller 层抛出的各类异常，
 * 并返回标准化的 Result 响应。涵盖业务异常、参数校验异常、权限异常和未知异常。
 * </p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理自定义业务异常（BizException）。
     * <p>
     * 从异常对象中提取错误码和错误消息，构造统一的错误响应。
     * HTTP 状态码根据业务错误码确定，通常为 200（业务层面区分成功与失败）。
     * </p>
     *
     * @param e 业务异常
     * @return 包含错误码和错误消息的 Result 对象
     */
    @ExceptionHandler(BizException.class)
    public Result<?> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return Result.error(e.getErrorCode(), e.getMessage());
    }

    /**
     * 处理 @Valid 注解校验失败异常（MethodArgumentNotValidException）。
     * <p>
     * 针对请求体中的字段校验失败，提取各字段的错误信息并拼接返回。
     * 返回 HTTP 400 Bad Request。
     * </p>
     *
     * @param e 方法参数校验异常
     * @return 包含字段级错误信息的 Result 对象
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", errorMsg);
        return Result.error(400, errorMsg);
    }

    /**
     * 处理表单绑定校验异常（BindException）。
     * <p>
     * 针对表单参数绑定和校验失败时的统一处理。
     * 返回 HTTP 400 Bad Request。
     * </p>
     *
     * @param e 参数绑定异常
     * @return 包含校验错误信息的 Result 对象
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e) {
        String errorMsg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定异常: {}", errorMsg);
        return Result.error(400, errorMsg);
    }

    /**
     * 处理 Spring Security 权限拒绝异常（AccessDeniedException）。
     * <p>
     * 当前用户没有足够的权限访问资源时触发。
     * 返回 HTTP 403 Forbidden。
     * </p>
     *
     * @param e 权限拒绝异常
     * @return 包含权限错误信息的 Result 对象
     */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(AccessDeniedException.class)
    public Result<?> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return Result.error(403, "没有访问权限");
    }

    /**
     * 处理 JSON 请求体解析失败异常（HttpMessageNotReadableException）。
     * <p>请求体格式错误、字段类型不匹配等场景，返回 HTTP 400。</p>
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public Result<?> handleHttpMessageNotReadableException(org.springframework.http.converter.HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.error(400, "请求体格式错误，请检查 JSON 格式");
    }

    /**
     * 处理路径/参数类型不匹配异常（MethodArgumentTypeMismatchException）。
     * <p>如 /products/abc 中 id 应为数字，返回 HTTP 400。</p>
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public Result<?> handleMethodArgumentTypeMismatchException(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: name={}, value={}", e.getName(), e.getValue());
        return Result.error(400, "参数 " + e.getName() + " 类型不正确");
    }

    /**
     * 处理缺少必填请求参数异常（MissingServletRequestParameterException）。
     * <p>如未传必填的 query 参数，返回 HTTP 400。</p>
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public Result<?> handleMissingServletRequestParameterException(org.springframework.web.bind.MissingServletRequestParameterException e) {
        log.warn("缺少请求参数: {}", e.getParameterName());
        return Result.error(400, "缺少必填参数：" + e.getParameterName());
    }

    /**
     * 处理所有未捕获的未知异常（Exception）。
     * <p>
     * 兜底异常处理器，防止敏感信息泄露给客户端，统一返回 HTTP 500 Internal Server Error。
     * 详细异常信息记录在服务端日志中。
     * </p>
     *
     * @param e 未知异常
     * @return 包含通用错误信息的 Result 对象
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常: ", e);
        return Result.error(500, "服务器内部错误，请稍后重试");
    }
}
