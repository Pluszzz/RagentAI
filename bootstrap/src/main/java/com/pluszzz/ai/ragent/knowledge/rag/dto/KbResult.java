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
package com.pluszzz.ai.ragent.knowledge.rag.dto;

import com.pluszzz.ai.ragent.framework.convention.RetrievedChunk;

import java.util.List;
import java.util.Map;

/**
 * KB 检索结果
 *
 * @param groupedContext 分组后的上下文文本
 * @param intentChunks   意图 ID -> 分片列表
 */
public record KbResult(String groupedContext, Map<String, List<RetrievedChunk>> intentChunks) {
    /**
     * 空结果
     */
    public static KbResult empty() {
        return new KbResult("", Map.of());
    }
}
