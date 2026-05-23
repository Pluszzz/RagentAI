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
package com.pluszzz.ai.ragent.infra.http;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import okhttp3.MediaType;

/**
 * HTTP 媒体类型常量类
 * 提供常用的 HTTP Content-Type 媒体类型定义
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HttpMediaTypes {

    /**
     * JSON 媒体类型，使用 UTF-8 字符集
     * 用于 OkHttp 请求中的 MediaType 对象
     */
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /**
     * JSON 媒体类型字符串，使用 UTF-8 字符集
     * 用于 HTTP 请求头的 Content-Type 值
     */
    public static final String JSON_UTF8_HEADER = "application/json; charset=UTF-8";
}
