/*
 * Copyright 2026 Pluszzz
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pluszzz.ai.ragent.framework.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.pluszzz.ai.ragent.framework.convention.Result;
import com.pluszzz.ai.ragent.framework.errorcode.BaseErrorCode;
import com.pluszzz.ai.ragent.framework.exception.AbstractException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Optional;

/**
 * 全局异常处理器
 * 拦截指定异常并通过优雅构建方式返回前端信息
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 拦截参数验证异常
     */
    @SneakyThrows
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public Result<Void> validExceptionHandler(HttpServletRequest request, MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();
        FieldError firstFieldError = CollectionUtil.getFirst(bindingResult.getFieldErrors());
        String exceptionStr = Optional.ofNullable(firstFieldError)
                .map(FieldError::getDefaultMessage)
                .orElse(StrUtil.EMPTY);
        log.error("[{}] {} [ex] {}", request.getMethod(), getUrl(request), exceptionStr);
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), exceptionStr);
    }

    /**
     * 拦截应用内抛出的异常
     */
    @ExceptionHandler(value = {AbstractException.class})
    public Object abstractException(HttpServletRequest request, AbstractException ex) {
        if (ex.getCause() != null) {
            log.error("[{}] {} [ex] {}", request.getMethod(), request.getRequestURL().toString(), ex, ex.getCause());
            return isSseRequest(request) ? sseErrorResponse(ex.getErrorMessage()) : Results.failure(ex);
        }
        StringBuilder stackTraceBuilder = new StringBuilder();
        stackTraceBuilder.append(ex.getClass().getName()).append(": ").append(ex.getErrorMessage()).append("\n");
        StackTraceElement[] stackTrace = ex.getStackTrace();
        for (int i = 0; i < Math.min(5, stackTrace.length); i++) {
            stackTraceBuilder.append("\tat ").append(stackTrace[i]).append("\n");
        }
        log.error("[{}] {} [ex] {} \n\n{}", request.getMethod(), request.getRequestURL().toString(), ex, stackTraceBuilder);
        return isSseRequest(request) ? sseErrorResponse(ex.getErrorMessage()) : Results.failure(ex);
    }

    /**
     * 拦截未登录异常
     */
    @ExceptionHandler(value = NotLoginException.class)
    public Result<Void> notLoginException(HttpServletRequest request, NotLoginException ex) {
        log.warn("[{}] {} [auth] not-login: {}", request.getMethod(), getUrl(request), ex.getMessage());
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), "未登录或登录已过期");
    }

    /**
     * 拦截无角色权限异常
     */
    @ExceptionHandler(value = NotRoleException.class)
    public Result<Void> notRoleException(HttpServletRequest request, NotRoleException ex) {
        log.warn("[{}] {} [auth] no-role: {}", request.getMethod(), getUrl(request), ex.getMessage());
        return Results.failure(BaseErrorCode.CLIENT_ERROR.code(), "权限不足");
    }

    /**
     * 拦截未捕获异常
     */
    @ExceptionHandler(value = Throwable.class)
    public Object defaultErrorHandler(HttpServletRequest request, Throwable throwable) {
        log.error("[{}] {} ", request.getMethod(), getUrl(request), throwable);
        return isSseRequest(request) ? sseErrorResponse("系统异常，请稍后重试") : Results.failure();
    }

    private String getUrl(HttpServletRequest request) {
        if (StrUtil.isBlank(request.getQueryString())) {
            return request.getRequestURL().toString();
        }
        return request.getRequestURL().toString() + "?" + request.getQueryString();
    }

    private boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE)) {
            return true;
        }
        String contentType = request.getContentType();
        return contentType != null && contentType.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    private ResponseEntity<String> sseErrorResponse(String message) {
        String safeMessage = message == null ? "请求失败" : message.replace("\n", "\\n").replace("\r", "");
        String payload = "event: error\ndata: " + safeMessage + "\n\n";
        return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(payload);
    }
}
