package com.ecommerce.common.exception;

/**
 * 统一错误码枚举
 *
 * <p>定义系统中所有可能的响应状态码及其对应的提示消息。
 * 状态码遵循以下规则：
 * <ul>
 *   <li>200：操作成功</li>
 *   <li>4xx：客户端请求错误</li>
 *   <li>5xx：服务端内部错误</li>
 *   <li>1xxx：具体业务错误</li>
 * </ul>
 */
public enum ErrorCode {

    /** 操作成功 */
    SUCCESS(200, "操作成功"),

    /** 请求参数错误 */
    BAD_REQUEST(400, "请求参数错误"),

    /** 未授权 */
    UNAUTHORIZED(401, "未授权"),

    /** 无权限 */
    FORBIDDEN(403, "无权限"),

    /** 资源不存在 */
    NOT_FOUND(404, "资源不存在"),

    /** 请求过多 */
    TOO_MANY_REQUESTS(429, "请求过多，请稍后重试"),

    /** 状态冲突 */
    CONFLICT(409, "资源状态冲突"),

    /** 服务器内部错误 */
    INTERNAL_ERROR(500, "服务器内部错误"),

    /** 用户名已存在 */
    USERNAME_EXISTS(1001, "用户名已存在"),

    /** 手机号已存在 */
    PHONE_EXISTS(1002, "手机号已存在"),

    /** 用户不存在 */
    USER_NOT_FOUND(1003, "用户不存在"),

    /** 用户名或密码错误 */
    BAD_CREDENTIALS(1004, "用户名或密码错误"),

    /** 令牌无效 */
    TOKEN_INVALID(1005, "令牌无效"),

    /** 令牌已过期 */
    TOKEN_EXPIRED(1006, "令牌已过期"),

    /** 角色不存在 */
    ROLE_NOT_FOUND(1007, "角色不存在"),

    /** 账号已被禁用 */
    ACCOUNT_DISABLED(1008, "账号已被禁用"),

    /** 刷新令牌无效或已过期 */
    REFRESH_TOKEN_INVALID(1009, "刷新令牌无效或已过期");

    /** 状态码 */
    private final int code;

    /** 提示消息 */
    private final String message;

    /**
     * 构造错误码枚举
     *
     * @param code    状态码
     * @param message 提示消息
     */
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 获取状态码
     *
     * @return 状态码
     */
    public int getCode() {
        return code;
    }

    /**
     * 获取提示消息
     *
     * @return 提示消息
     */
    public String getMessage() {
        return message;
    }
}
