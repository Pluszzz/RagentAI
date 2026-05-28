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

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 文档解析器类型枚举
 */
@Getter
@RequiredArgsConstructor
public enum ParserType {

    /**
     * Tika 解析器（支持 PDF、Word、Excel、PPT 等多种格式）
     */
    TIKA("Tika"),

    /**
     * Markdown 解析器
     */
    MARKDOWN("Markdown"),

    /**
     * MarkItDown 解析器（结构化 Markdown 输出）
     */
    MARKITDOWN("MarkItDown"),

    /**
     * Hybrid 混合解析器（先 MarkItDown 后 Tika 兜底）
     */
    HYBRID("Hybrid");

    /**
     * 解析器类型名称
     */
    private final String type;
}
