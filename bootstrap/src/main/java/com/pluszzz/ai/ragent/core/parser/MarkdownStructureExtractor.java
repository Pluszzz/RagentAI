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

import com.pluszzz.ai.ragent.ingestion.domain.context.StructuredDocument;
import com.pluszzz.ai.ragent.ingestion.domain.context.StructuredDocument.StructuredSection;
import com.pluszzz.ai.ragent.ingestion.domain.context.StructuredDocument.StructuredTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 结构提取工具
 * <p>
 * 从 Markdown 文本中提取标题层级（sections）和表格（tables），
 * 用于填充 {@link StructuredDocument} 的结构化字段
 * </p>
 */
public final class MarkdownStructureExtractor {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern TABLE_LINE_PATTERN = Pattern.compile("^\\|.+\\|\\s*$");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\|[-:\\s|]+\\|\\s*$");

    private MarkdownStructureExtractor() {
    }

    /**
     * 从 Markdown 文本中提取章节结构
     */
    public static List<StructuredSection> extractSections(String markdown) {
        List<StructuredSection> sections = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return sections;
        }

        List<String> lines = Arrays.asList(markdown.split("\n", -1));
        List<Integer> lineOffsets = computeLineOffsets(markdown);

        int lastHeadingIdx = -1;
        String lastHeadingTitle = "";
        int lastHeadingLevel = 0;
        StringBuilder contentBuilder = new StringBuilder();
        int contentStartOffset = -1;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = HEADING_PATTERN.matcher(line);
            if (m.matches()) {
                // save previous section
                if (lastHeadingIdx >= 0) {
                    sections.add(buildSection(lastHeadingTitle, lastHeadingLevel,
                            contentBuilder.toString(), contentStartOffset,
                            lineOffsets.get(Math.min(i, lineOffsets.size() - 1))));
                }

                lastHeadingIdx = i;
                lastHeadingTitle = m.group(2).trim();
                lastHeadingLevel = m.group(1).length();
                contentBuilder = new StringBuilder();
                contentStartOffset = -1;
            } else if (lastHeadingIdx >= 0) {
                if (contentStartOffset < 0 && !line.isBlank()) {
                    contentStartOffset = lineOffsets.get(i);
                }
                if (contentBuilder.length() > 0) {
                    contentBuilder.append('\n');
                }
                contentBuilder.append(line);
            }
        }

        // save last section
        if (lastHeadingIdx >= 0) {
            sections.add(buildSection(lastHeadingTitle, lastHeadingLevel,
                    contentBuilder.toString(), contentStartOffset,
                    markdown.length()));
        }

        return sections;
    }

    /**
     * 从 Markdown 文本中提取表格结构
     */
    public static List<StructuredTable> extractTables(String markdown) {
        List<StructuredTable> tables = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return tables;
        }

        List<String> lines = Arrays.asList(markdown.split("\n", -1));
        List<Integer> lineOffsets = computeLineOffsets(markdown);

        int i = 0;
        while (i < lines.size()) {
            // find start of a table: a line matching |...|
            if (TABLE_LINE_PATTERN.matcher(lines.get(i)).matches()
                    && !TABLE_SEPARATOR.matcher(lines.get(i)).matches()) {

                int tableStart = i;
                String title = findPrecedingHeading(lines, i);
                List<List<String>> rows = new ArrayList<>();

                // collect all table lines
                while (i < lines.size() && TABLE_LINE_PATTERN.matcher(lines.get(i)).matches()) {
                    String line = lines.get(i);
                    if (TABLE_SEPARATOR.matcher(line).matches()) {
                        // separator line, skip but don't add to rows
                        i++;
                        continue;
                    }
                    List<String> cells = parseTableRow(line);
                    if (!cells.isEmpty()) {
                        rows.add(cells);
                    }
                    i++;
                }

                if (!rows.isEmpty()) {
                    int startOffset = lineOffsets.get(tableStart);
                    int endOffset = i < lineOffsets.size()
                            ? lineOffsets.get(i)
                            : markdown.length();
                    tables.add(StructuredTable.builder()
                            .title(title)
                            .rows(rows)
                            .startOffset(startOffset)
                            .endOffset(endOffset)
                            .build());
                }
            } else {
                i++;
            }
        }

        return tables;
    }

    /**
     * 用提取的 sections 和 tables 填充 StructuredDocument
     */
    public static StructuredDocument enrich(StructuredDocument document) {
        if (document == null) {
            return null;
        }
        String text = document.getText();
        if (text != null && !text.isBlank()) {
            document.setSections(extractSections(text));
            document.setTables(extractTables(text));
        }
        return document;
    }

    private static StructuredSection buildSection(String title, int level,
            String content, int startOffset, int endOffset) {
        return StructuredSection.builder()
                .title(title)
                .level(level)
                .content(!content.isBlank() ? content.trim() : "")
                .startOffset(startOffset >= 0 ? startOffset : null)
                .endOffset(endOffset)
                .build();
    }

    private static String findPrecedingHeading(List<String> lines, int tableStart) {
        for (int j = tableStart - 1; j >= 0; j--) {
            Matcher m = HEADING_PATTERN.matcher(lines.get(j));
            if (m.matches()) {
                return m.group(2).trim();
            }
            if (!lines.get(j).isBlank()) {
                break; // non-heading non-blank line stops search
            }
        }
        return "";
    }

    private static List<String> parseTableRow(String line) {
        String trimmed = line.trim();
        // strip leading and trailing |
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return Arrays.stream(trimmed.split("\\|"))
                .map(String::trim)
                .toList();
    }

    private static List<Integer> computeLineOffsets(String text) {
        List<Integer> offsets = new ArrayList<>();
        int pos = 0;
        offsets.add(0);
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                pos = i + 1;
                offsets.add(pos);
            }
        }
        return offsets;
    }
}
