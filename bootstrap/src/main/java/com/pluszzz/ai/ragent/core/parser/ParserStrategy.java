Copyright 2026 Pluszzz

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
package com.pluszzz.ai.ragent.core.parser;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 解析策略枚举
 * <p>
 * 定义文档解析的路由策略：Tika 纯文本解析、MarkItDown 结构化解析、Hybrid 混合模式
 * </p>
 */
@Getter
@RequiredArgsConstructor
public enum ParserStrategy {

    /**
     * Tika 解析策略 — 纯文本输出，格式覆盖广，适合扫描件和旧格式
     */
    TIKA("tika"),

    /**
     * MarkItDown 解析策略 — Markdown 输出，保留标题/表格/列表结构，适合现代 Office 和结构化 PDF
     */
    MARKITDOWN("markitdown"),

    /**
     * 混合策略 — 先尝试 MarkItDown，质量校验不通过则自动降级到 Tika
     */
    HYBRID("hybrid");

    private final String value;

    @JsonCreator
    public static ParserStrategy fromValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = normalize(value);
        for (ParserStrategy strategy : values()) {
            if (strategy.value.equalsIgnoreCase(normalized) || strategy.name().equalsIgnoreCase(normalized)) {
                return strategy;
            }
        }
        throw new IllegalArgumentException("Unknown parser strategy: " + value);
    }

    private static String normalize(String value) {
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase();
        return lower.replace('-', '_');
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
