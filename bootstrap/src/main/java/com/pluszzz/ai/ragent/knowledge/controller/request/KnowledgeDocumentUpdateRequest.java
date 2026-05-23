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
package com.pluszzz.ai.ragent.knowledge.controller.request;

import lombok.Data;

@Data
public class KnowledgeDocumentUpdateRequest {

    /**
     * 文档名称
     */
    private String docName;

    /**
     * 是否启用
     */
    private Integer enabled;

    /**
     * 状态：pending / running / failed / success
     */
    private String status;

    /**
     * 分块数（可选：向量化完成后更新）
     */
    private Integer chunkCount;
}
