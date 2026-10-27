package com.exam.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {
    SUCCESS(200, "操作成功"),
    CREATED(201, "创建成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权，请重新登录"),
    FORBIDDEN(403, "无权限访问此资源"),
    NOT_FOUND(404, "请求的资源不存在"),
    CONFLICT(409, "业务冲突：该时段已被预约"),
    ACCOUNT_LOCKED(423, "账号已锁定，请稍后再试"),
    TOO_MANY_REQUESTS(429, "请求过于频繁或重复提交"),
    INTERNAL_SERVER_ERROR(500, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;
}
