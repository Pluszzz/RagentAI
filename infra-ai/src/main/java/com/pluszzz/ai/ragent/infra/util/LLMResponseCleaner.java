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
package com.pluszzz.ai.ragent.infra.util;

import java.util.regex.Pattern;

/**
 * LLM 输出清理工具类
 */
public final class LLMResponseCleaner {

    private static final Pattern LEADING_CODE_FENCE = Pattern.compile("^```[\\w-]*\\s*\\n?");
    private static final Pattern TRAILING_CODE_FENCE = Pattern.compile("\\n?```\\s*$");
    private static final Pattern THINK_TAG_BLOCK = Pattern.compile("(?is)<think>.*?</think>");
    private static final Pattern ANALYSIS_TAG_BLOCK = Pattern.compile("(?is)<analysis>.*?</analysis>");

    private LLMResponseCleaner() {
    }

    /**
     * 移除 Markdown 代码块围栏（例如 ```json ... ```）
     */
    public static String stripMarkdownCodeFence(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        cleaned = LEADING_CODE_FENCE.matcher(cleaned).replaceFirst("");
        cleaned = TRAILING_CODE_FENCE.matcher(cleaned).replaceFirst("");
        return cleaned.trim();
    }

    /**
     * 绉婚櫎 <think>...</think> / <analysis>...</analysis> 绛夌被浼煎唴瀹?
     */
    public static String stripThinkTags(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = THINK_TAG_BLOCK.matcher(raw).replaceAll("");
        cleaned = ANALYSIS_TAG_BLOCK.matcher(cleaned).replaceAll("");
        return cleaned.trim();
    }

    /**
     * 灏濊瘯浠庡師濮嬭緭鍑轰腑鎻愬彇绗竴涓畬鏁寸殑 JSON 瀵硅薄鎴栨暟缁勩€?
     */
    public static String extractJsonPayload(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = stripMarkdownCodeFence(raw);
        cleaned = stripThinkTags(cleaned);
        if (cleaned == null || cleaned.isBlank()) {
            return cleaned;
        }

        int startObj = cleaned.indexOf('{');
        int startArr = cleaned.indexOf('[');
        int start;
        if (startObj < 0 && startArr < 0) {
            return cleaned.trim();
        } else if (startObj < 0) {
            start = startArr;
        } else if (startArr < 0) {
            start = startObj;
        } else {
            start = Math.min(startObj, startArr);
        }

        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                    continue;
                }
                if (c == '\\') {
                    escape = true;
                    continue;
                }
                if (c == '\"') {
                    inString = false;
                }
                continue;
            }

            if (c == '\"') {
                inString = true;
                continue;
            }
            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
                if (depth == 0) {
                    return cleaned.substring(start, i + 1).trim();
                }
            }
        }

        return cleaned.trim();
    }
}
