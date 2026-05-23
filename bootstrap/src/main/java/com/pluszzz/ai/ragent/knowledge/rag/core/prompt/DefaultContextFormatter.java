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
package com.pluszzz.ai.ragent.knowledge.rag.core.prompt;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.pluszzz.ai.ragent.framework.convention.RetrievedChunk;
import com.pluszzz.ai.ragent.knowledge.rag.core.intent.IntentNode;
import com.pluszzz.ai.ragent.knowledge.rag.core.intent.NodeScore;
import com.pluszzz.ai.ragent.knowledge.rag.core.mcp.MCPResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DefaultContextFormatter implements ContextFormatter {

    private static final int MAX_CONTEXT_CHARS = 2000;
    private static final int MAX_CHUNK_CHARS = 800;

    @Override
    public String formatKbContext(List<NodeScore> kbIntents, Map<String, List<RetrievedChunk>> rerankedByIntent, int topK) {
        if (rerankedByIntent == null || rerankedByIntent.isEmpty()) {
            return "";
        }
        if (CollUtil.isEmpty(kbIntents)) {
            return formatChunksWithoutIntent(rerankedByIntent, topK);
        }

        // 澶氭剰鍥惧満鏅細鍚堝苟鎵€鏈夎鍒欏拰鏂囨。
        if (kbIntents.size() > 1) {
            return formatMultiIntentContext(kbIntents, rerankedByIntent, topK);
        }

        // 鍗曟剰鍥惧満鏅細淇濇寔鍘熸湁閫昏緫
        return formatSingleIntentContext(kbIntents.get(0), rerankedByIntent, topK);
    }

    /**
     * 鏍煎紡鍖栧崟鎰忓浘涓婁笅鏂?
     */
    private String formatSingleIntentContext(NodeScore nodeScore, Map<String, List<RetrievedChunk>> rerankedByIntent, int topK) {
        List<RetrievedChunk> chunks = rerankedByIntent.get(nodeScore.getNode().getId());
        if (CollUtil.isEmpty(chunks)) {
            return "";
        }
        String snippet = StrUtil.emptyIfNull(nodeScore.getNode().getPromptSnippet()).trim();
        String body = buildLimitedBody(chunks.stream()
                .limit(topK)
                .map(RetrievedChunk::getText)
                .toList());
        StringBuilder block = new StringBuilder();
        if (StrUtil.isNotBlank(snippet)) {
            block.append("#### 鍥炵瓟瑙勫垯\n").append(snippet).append("\n\n");
        }
        block.append("#### 鐭ヨ瘑搴撶墖娈礬n````text\n").append(body).append("\n````");
        return block.toString();
    }

    /**
     * 鏍煎紡鍖栧鎰忓浘涓婁笅鏂?
     */
    private String formatMultiIntentContext(List<NodeScore> kbIntents, Map<String, List<RetrievedChunk>> rerankedByIntent, int topK) {
        StringBuilder result = new StringBuilder();

        // 1. 鍚堝苟鎵€鏈夋剰鍥剧殑鍥炵瓟瑙勫垯
        List<String> snippets = kbIntents.stream()
                .map(ns -> ns.getNode().getPromptSnippet())
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .toList();

        if (!snippets.isEmpty()) {
            result.append("#### 鍥炵瓟瑙勫垯\n");
            for (int i = 0; i < snippets.size(); i++) {
                result.append(i + 1).append(". ").append(snippets.get(i)).append("\n");
            }
            result.append("\n");
        }

        // 2. 鍚堝苟鎵€鏈夋剰鍥剧殑鏂囨。鐗囨锛堝幓閲嶏級
        List<RetrievedChunk> allChunks = rerankedByIntent.values().stream()
                .flatMap(List::stream)
                .distinct()
                .limit(topK)
                .toList();

        if (!allChunks.isEmpty()) {
            String body = buildLimitedBody(allChunks.stream()
                    .map(RetrievedChunk::getText)
                    .toList());
            result.append("#### 鐭ヨ瘑搴撶墖娈礬n````text\n").append(body).append("\n````");
        }

        return result.toString();
    }

    private String formatChunksWithoutIntent(Map<String, List<RetrievedChunk>> rerankedByIntent, int topK) {
        int limit = topK > 0 ? topK : Integer.MAX_VALUE;
        List<RetrievedChunk> chunks = new ArrayList<>();
        for (List<RetrievedChunk> list : rerankedByIntent.values()) {
            if (CollUtil.isEmpty(list)) {
                continue;
            }
            for (RetrievedChunk chunk : list) {
                chunks.add(chunk);
                if (chunks.size() >= limit) {
                    break;
                }
            }
            if (chunks.size() >= limit) {
                break;
            }
        }
        if (chunks.isEmpty()) {
            return "";
        }

        String body = buildLimitedBody(chunks.stream()
                .map(RetrievedChunk::getText)
                .toList());
        return "#### 鐭ヨ瘑搴撶墖娈礬n````text\n" + body + "\n````";
    }

    @Override
    public String formatMcpContext(List<MCPResponse> responses, List<NodeScore> mcpIntents) {
        if (CollUtil.isEmpty(responses) || responses.stream().noneMatch(MCPResponse::isSuccess)) {
            return "";
        }
        if (CollUtil.isEmpty(mcpIntents)) {
            return mergeResponsesToText(responses);
        }

        Map<String, IntentNode> toolToIntent = new LinkedHashMap<>();
        for (NodeScore ns : mcpIntents) {
            IntentNode node = ns.getNode();
            if (node == null || StrUtil.isBlank(node.getMcpToolId())) {
                continue;
            }
            toolToIntent.putIfAbsent(node.getMcpToolId(), node);
        }

        Map<String, List<MCPResponse>> grouped = responses.stream()
                .filter(MCPResponse::isSuccess)
                .filter(r -> StrUtil.isNotBlank(r.getToolId()))
                .collect(Collectors.groupingBy(MCPResponse::getToolId));

        return toolToIntent.entrySet().stream()
                .map(entry -> {
                    List<MCPResponse> toolResponses = grouped.get(entry.getKey());
                    if (CollUtil.isEmpty(toolResponses)) {
                        return "";
                    }
                    IntentNode node = entry.getValue();
                    String snippet = StrUtil.emptyIfNull(node.getPromptSnippet()).trim();
                    String body = mergeResponsesToText(toolResponses);
                    if (StrUtil.isBlank(body)) {
                        return "";
                    }
                    StringBuilder block = new StringBuilder();
                    if (StrUtil.isNotBlank(snippet)) {
                        block.append("#### 鎰忓浘瑙勫垯\n").append(snippet).append("\n");
                    }
                    block.append("#### 鍔ㄦ€佹暟鎹墖娈礬n").append(body);
                    return block.toString();
                })
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 灏嗗涓?MCP 鍝嶅簲鍚堝苟涓烘枃鏈紙鐢ㄤ簬鎷兼帴鍒?Prompt锛?
     */
    private String mergeResponsesToText(List<MCPResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return "";
        }

        List<String> successResults = new ArrayList<>();
        List<String> errorResults = new ArrayList<>();

        for (MCPResponse response : responses) {
            if (response.isSuccess() && response.getTextResult() != null) {
                successResults.add(response.getTextResult());
            } else if (!response.isSuccess()) {
                errorResults.add(String.format("宸ュ叿 %s 璋冪敤澶辫触: %s",
                        response.getToolId(), response.getErrorMessage()));
            }
        }

        StringBuilder sb = new StringBuilder();

        if (!successResults.isEmpty()) {
            for (String result : successResults) {
                sb.append(result).append("\n\n");
            }
        }

        if (!errorResults.isEmpty()) {
            sb.append("銆愰儴鍒嗘煡璇㈠け璐ャ€慭n");
            for (String error : errorResults) {
                sb.append("- ").append(error).append("\n");
            }
        }

        return sb.toString().trim();
    }
    private String buildLimitedBody(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String text : texts) {
            if (StrUtil.isBlank(text)) {
                continue;
            }
            String chunk = truncateText(text, MAX_CHUNK_CHARS);
            if (sb.length() + chunk.length() + 1 > MAX_CONTEXT_CHARS) {
                int remaining = MAX_CONTEXT_CHARS - sb.length();
                if (remaining <= 0) {
                    break;
                }
                sb.append(chunk, 0, Math.min(chunk.length(), remaining));
                break;
            }
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(chunk);
        }
        return sb.toString();
    }

    private String truncateText(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars);
    }
}

