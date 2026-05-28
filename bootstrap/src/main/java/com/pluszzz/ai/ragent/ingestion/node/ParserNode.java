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
package com.pluszzz.ai.ragent.ingestion.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pluszzz.ai.ragent.framework.exception.ClientException;
import com.pluszzz.ai.ragent.ingestion.domain.context.IngestionContext;
import com.pluszzz.ai.ragent.ingestion.domain.context.StructuredDocument;
import com.pluszzz.ai.ragent.ingestion.domain.enums.IngestionNodeType;
import com.pluszzz.ai.ragent.ingestion.domain.pipeline.NodeConfig;
import com.pluszzz.ai.ragent.ingestion.domain.result.NodeResult;
import com.pluszzz.ai.ragent.ingestion.domain.settings.ParserSettings;
import com.pluszzz.ai.ragent.ingestion.util.MimeTypeDetector;
import com.pluszzz.ai.ragent.core.parser.DocumentParser;
import com.pluszzz.ai.ragent.core.parser.DocumentParserSelector;
import com.pluszzz.ai.ragent.core.parser.MarkdownStructureExtractor;
import com.pluszzz.ai.ragent.core.parser.ParseResult;
import com.pluszzz.ai.ragent.core.parser.ParserStrategy;
import com.pluszzz.ai.ragent.core.parser.ParserType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档解析节点
 * 负责将输入的字节流（如 PDF、Word、Excel 等）解析为结构化的文本或文档对象
 */
@Slf4j
@Component
public class ParserNode implements IngestionNode {

    private final ObjectMapper objectMapper;
    private final DocumentParserSelector parserSelector;

    public ParserNode(ObjectMapper objectMapper, DocumentParserSelector parserSelector) {
        this.objectMapper = objectMapper;
        this.parserSelector = parserSelector;
    }

    @Override
    public String getNodeType() {
        return IngestionNodeType.PARSER.getValue();
    }

    @Override
    public NodeResult execute(IngestionContext context, NodeConfig config) {
        if (context.getRawBytes() == null || context.getRawBytes().length == 0) {
            return NodeResult.fail(new ClientException("解析器缺少原始字节"));
        }

        String mimeType = context.getMimeType();
        if (!StringUtils.hasText(mimeType)) {
            String fileName = context.getSource() == null ? null : context.getSource().getFileName();
            mimeType = MimeTypeDetector.detect(context.getRawBytes(), fileName);
            context.setMimeType(mimeType);
        }

        ParserSettings settings = parseSettings(config.getSettings());
        String fileName = context.getSource() == null ? null : context.getSource().getFileName();

        // 验证文件类型是否符合配置
        validateMimeType(settings, mimeType, fileName);

        ParserSettings.ParserRule rule = matchRule(settings, mimeType, fileName);

        ParserStrategy strategy = resolveStrategy(settings, rule);
        log.info("ParserNode 使用策略: {}, MIME: {}", strategy.getValue(), mimeType);

        DocumentParser parser = selectParserByStrategy(strategy);
        if (parser == null) {
            return NodeResult.fail(new ClientException(
                    "未找到策略 " + strategy.getValue() + " 对应的解析器"));
        }

        Map<String, Object> options = buildOptions(rule, fileName);
        ParseResult result = parser.parse(context.getRawBytes(), mimeType, options);
        context.setRawText(result.text());

        // 当输出为 Markdown 时，提取结构化信息
        StructuredDocument document = buildStructuredDocument(result, strategy);
        context.setDocument(document);

        return NodeResult.ok("解析文本长度=" + (result.text() == null ? 0 : result.text().length()));
    }

    /**
     * 验证文件类型是否符合配置的规则
     * 如果配置了规则但文件类型不匹配，则抛出异常
     */
    private void validateMimeType(ParserSettings settings, String mimeType, String fileName) {
        if (settings == null || settings.getRules() == null || settings.getRules().isEmpty()) {
            // 没有配置规则，允许所有类型
            return;
        }

        String resolvedType = resolveType(mimeType, fileName);

        // 检查是否有匹配的规则
        boolean hasMatch = false;
        for (ParserSettings.ParserRule rule : settings.getRules()) {
            if (rule == null || !StringUtils.hasText(rule.getMimeType())) {
                continue;
            }
            String configured = normalizeType(rule.getMimeType());
            if (!StringUtils.hasText(configured)) {
                continue;
            }
            if ("ALL".equals(configured) || configured.equalsIgnoreCase(resolvedType)) {
                hasMatch = true;
                break;
            }
        }

        if (!hasMatch) {
            // 构建允许的类型列表用于错误提示
            List<String> allowedTypes = settings.getRules().stream()
                    .filter(rule -> rule != null && StringUtils.hasText(rule.getMimeType()))
                    .map(rule -> normalizeType(rule.getMimeType()))
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();

            throw new ClientException(
                    String.format("文件类型不符合要求。当前文件类型: %s，允许的类型: %s",
                            resolvedType,
                            String.join(", ", allowedTypes))
            );
        }
    }

    private ParserSettings parseSettings(JsonNode node) {
        if (node == null || node.isNull()) {
            return ParserSettings.builder().rules(List.of()).build();
        }
        return objectMapper.convertValue(node, ParserSettings.class);
    }

    private ParserSettings.ParserRule matchRule(ParserSettings settings, String mimeType, String fileName) {
        if (settings == null || settings.getRules() == null || settings.getRules().isEmpty()) {
            return null;
        }
        String resolvedType = resolveType(mimeType, fileName);
        for (ParserSettings.ParserRule rule : settings.getRules()) {
            if (rule == null || !StringUtils.hasText(rule.getMimeType())) {
                continue;
            }
            String configured = normalizeType(rule.getMimeType());
            if (!StringUtils.hasText(configured)) {
                continue;
            }
            if ("ALL".equals(configured) || configured.equalsIgnoreCase(resolvedType)) {
                return rule;
            }
        }
        return null;
    }

    private String resolveType(String mimeType, String fileName) {
        String byName = resolveTypeByName(fileName);
        if (StringUtils.hasText(byName)) {
            return byName;
        }
        if (!StringUtils.hasText(mimeType)) {
            return "UNKNOWN";
        }
        String lower = mimeType.trim().toLowerCase();
        if (lower.contains("pdf")) {
            return "PDF";
        }
        if (lower.contains("markdown")) {
            return "MARKDOWN";
        }
        if (lower.contains("word") || lower.contains("msword") || lower.contains("wordprocessingml")) {
            return "WORD";
        }
        if (lower.contains("excel") || lower.contains("spreadsheetml")) {
            return "EXCEL";
        }
        if (lower.contains("powerpoint") || lower.contains("presentation")) {
            return "PPT";
        }
        if (lower.startsWith("image/")) {
            return "IMAGE";
        }
        if (lower.startsWith("text/")) {
            return "TEXT";
        }
        return "UNKNOWN";
    }

    private String resolveTypeByName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "PDF";
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return "MARKDOWN";
        }
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return "WORD";
        }
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) {
            return "EXCEL";
        }
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) {
            return "PPT";
        }
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp")) {
            return "IMAGE";
        }
        if (lower.endsWith(".txt")) {
            return "TEXT";
        }
        return null;
    }

    private String normalizeType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim().toUpperCase();
        return switch (value) {
            case "*", "ALL", "DEFAULT" -> "ALL";
            case "MD", "MARKDOWN" -> "MARKDOWN";
            case "DOC", "DOCX", "WORD" -> "WORD";
            case "XLS", "XLSX", "EXCEL" -> "EXCEL";
            case "PPT", "PPTX", "POWERPOINT" -> "PPT";
            case "TXT", "TEXT" -> "TEXT";
            case "PNG", "JPG", "JPEG", "GIF", "BMP", "WEBP", "IMAGE", "IMG" -> "IMAGE";
            case "PDF" -> "PDF";
            default -> value;
        };
    }

    /**
     * 解析策略，优先级：规则配置 > 默认配置 > Tika（向后兼容）
     */
    private ParserStrategy resolveStrategy(ParserSettings settings, ParserSettings.ParserRule rule) {
        if (rule != null && StringUtils.hasText(rule.getStrategy())) {
            try {
                return ParserStrategy.fromValue(rule.getStrategy());
            } catch (IllegalArgumentException e) {
                log.warn("未知的解析策略 '{}', 回退到默认配置", rule.getStrategy());
            }
        }
        if (settings != null && StringUtils.hasText(settings.getDefaultStrategy())) {
            try {
                return ParserStrategy.fromValue(settings.getDefaultStrategy());
            } catch (IllegalArgumentException e) {
                log.warn("未知的默认解析策略 '{}', 回退到 Tika", settings.getDefaultStrategy());
            }
        }
        return ParserStrategy.TIKA;
    }

    /**
     * 根据策略选择对应的解析器
     */
    private DocumentParser selectParserByStrategy(ParserStrategy strategy) {
        return switch (strategy) {
            case TIKA -> parserSelector.select(ParserType.TIKA.getType());
            case MARKITDOWN -> parserSelector.select(ParserType.MARKITDOWN.getType());
            case HYBRID -> parserSelector.select(ParserType.HYBRID.getType());
        };
    }

    /**
     * 构建解析选项，将文件名透传给解析器（用于临时文件扩展名推断）
     */
    private Map<String, Object> buildOptions(ParserSettings.ParserRule rule, String fileName) {
        Map<String, Object> options = new HashMap<>();
        if (rule != null && rule.getOptions() != null) {
            options.putAll(rule.getOptions());
        }
        if (StringUtils.hasText(fileName)) {
            options.putIfAbsent("fileName", fileName);
        }
        return options;
    }

    /**
     * 构建结构化文档，当策略为 MarkItDown 或 Hybrid 且输出含 Markdown 结构时
     * 提取章节和表格信息
     */
    private StructuredDocument buildStructuredDocument(ParseResult result, ParserStrategy strategy) {
        StructuredDocument document = StructuredDocument.builder()
                .text(result.text())
                .metadata(result.metadata())
                .build();

        if (isMarkdownOutput(strategy, result.text())) {
            MarkdownStructureExtractor.enrich(document);
        }

        return document;
    }

    /**
     * 判断是否为 Markdown 格式的输出
     */
    private static boolean isMarkdownOutput(ParserStrategy strategy, String text) {
        return (strategy == ParserStrategy.MARKITDOWN || strategy == ParserStrategy.HYBRID)
                && text != null
                && (text.contains("#") || text.contains("|"));
    }
}
