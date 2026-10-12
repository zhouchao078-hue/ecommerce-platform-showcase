package com.ecommerce.common.exception;

/**
 * 业务异常
 *
 * <p>业务层出现异常时统一抛出此异常，由全局异常处理器捕获并转换为统一的Result响应。
 * 支持传入自定义消息来覆盖错误码中的默认提示消息。
 *
 * <p>使用示例：
 * <pre>{@code
 * throw new BizException(ErrorCode.USER_NOT_FOUND);
 * throw new BizException(ErrorCode.USER_NOT_FOUND, "用户ID " + userId + " 不存在");
 * }</pre>
 */
public class BizException extends RuntimeException {

    /** 关联的错误码 */
    private final ErrorCode errorCode;

    /**
     * 使用错误码构造业务异常，消息使用错误码中的默认值
     *
     * @param errorCode 错误码
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 使用错误码和自定义消息构造业务异常
     *
     * @param errorCode 错误码
     * @param message   自定义消息（会覆盖错误码中的默认消息）
     */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 获取关联的错误码
     *
     * @return 错误码枚举
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
