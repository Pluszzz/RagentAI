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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 混合解析器
 * <p>
 * 先尝试 MarkItDown 解析，对结果做质量校验，校验不通过则自动降级到 Tika。
 * 适用于文档质量不确定的生产环境摄入流水线。
 * </p>
 */
@Slf4j
@Component
public class HybridDocumentParser implements DocumentParser {

    private static final int MIN_CONTENT_LENGTH = 50;
    private static final double MIN_PRINTABLE_RATIO = 0.7;
    private static final Pattern MARKDOWN_STRUCTURE = Pattern.compile(
            "(^#{1,6}\\s)|(^\\|.+\\|$)", Pattern.MULTILINE);

    /**
     * 需要做结构校验的 MIME 类型关键词
     */
    private static final String[] STRUCTURE_EXPECTED_TYPES = {
            "word", "msword", "powerpoint", "presentation"
    };

    private final MarkItDownDocumentParser markItDownParser;
    private final TikaDocumentParser tikaParser;

    public HybridDocumentParser(MarkItDownDocumentParser markItDownParser,
            TikaDocumentParser tikaParser) {
        this.markItDownParser = markItDownParser;
        this.tikaParser = tikaParser;
    }

    @Override
    public String getParserType() {
        return ParserType.HYBRID.getType();
    }

    @Override
    public ParseResult parse(byte[] content, String mimeType, Map<String, Object> options) {
        if (content == null || content.length == 0) {
            return ParseResult.ofText("");
        }

        if (markItDownParser.isAvailable() && markItDownParser.supports(mimeType)) {
            try {
                ParseResult result = markItDownParser.parse(content, mimeType, options);
                String fallbackReason = validateQuality(result.text(), mimeType);
                if (fallbackReason == null) {
                    log.info("Hybrid: MarkItDown 解析成功, 文本长度={}", result.text().length());
                    return result;
                }
                log.warn("Hybrid: MarkItDown 质量校验失败 ({}), 降级到 Tika", fallbackReason);
                return fallbackToTika(content, mimeType, options, "quality_failed: " + fallbackReason);
            } catch (MarkItDownException e) {
                log.warn("Hybrid: MarkItDown 解析失败 ({}), 降级到 Tika", e.getMessage());
                return fallbackToTika(content, mimeType, options,
                        "markitdown_error: " + e.getMessage());
            }
        }

        if (!markItDownParser.isAvailable()) {
            log.info("Hybrid: MarkItDown 不可用, 直接使用 Tika");
            return fallbackToTika(content, mimeType, options, "markitdown_unavailable");
        }

        log.info("Hybrid: MarkItDown 不支持此 MIME 类型 ({}), 直接使用 Tika", mimeType);
        return fallbackToTika(content, mimeType, options, "unsupported_mime_type");
    }

    @Override
    public String extractText(InputStream stream, String fileName) {
        // Hybrid 的 extractText 不做混合逻辑，直接委托 Tika
        return tikaParser.extractText(stream, fileName);
    }

    @Override
    public boolean supports(String mimeType) {
        return tikaParser.supports(mimeType);
    }

    /**
     * 质量校验，返回 null 表示通过，返回字符串表示失败原因
     */
    String validateQuality(String text, String mimeType) {
        if (text == null || text.isBlank()) {
            return "empty_output";
        }

        // 最小内容长度
        if (text.length() < MIN_CONTENT_LENGTH) {
            return "content_too_short (" + text.length() + " < " + MIN_CONTENT_LENGTH + ")";
        }

        // 可读字符比率
        if (!hasSufficientPrintableRatio(text)) {
            return "low_printable_ratio";
        }

        // 结构存在性（仅 Office 类文档要求）
        if (expectsStructure(mimeType) && !hasMarkdownStructure(text)) {
            return "no_markdown_structure";
        }

        return null;
    }

    private boolean hasSufficientPrintableRatio(String text) {
        long printable = text.chars()
                .filter(c -> Character.isLetterOrDigit(c)
                        || Character.isWhitespace(c)
                        || isCommonPunctuation(c))
                .count();
        return (double) printable / text.length() >= MIN_PRINTABLE_RATIO;
    }

    private static boolean isCommonPunctuation(int c) {
        return c == '.' || c == ',' || c == ';' || c == ':'
                || c == '!' || c == '?' || c == '-'
                || c == '(' || c == ')' || c == '[' || c == ']'
                || c == '{' || c == '}' || c == '\'' || c == '"'
                || c == '#' || c == '*' || c == '_' || c == '|'
                || c == '/' || c == '\\' || c == '@' || c == '&'
                || c == '+' || c == '=' || c == '<' || c == '>'
                || c == '`' || c == '~' || c == '^' || c == '%'
                || c == '$';
    }

    private boolean expectsStructure(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String lower = mimeType.toLowerCase();
        for (String keyword : STRUCTURE_EXPECTED_TYPES) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasMarkdownStructure(String text) {
        return MARKDOWN_STRUCTURE.matcher(text).find();
    }

    private ParseResult fallbackToTika(byte[] content, String mimeType,
            Map<String, Object> options, String reason) {
        ParseResult tikaResult = tikaParser.parse(content, mimeType, options);
        Map<String, Object> metadata = new HashMap<>(tikaResult.metadata());
        metadata.put("fallback_reason", reason);
        metadata.put("parser_used", "tika_fallback");
        return ParseResult.of(tikaResult.text(), metadata);
    }
}
