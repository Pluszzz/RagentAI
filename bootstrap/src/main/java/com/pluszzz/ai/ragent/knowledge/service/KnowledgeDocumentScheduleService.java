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
package com.pluszzz.ai.ragent.knowledge.service;

import com.pluszzz.ai.ragent.knowledge.dao.entity.KnowledgeDocumentDO;

/**
 * 知识库文档定时任务服务
 */
public interface KnowledgeDocumentScheduleService {

    /**
     * 根据文档信息创建或更新定时任务记录
     *
     * @param documentDO 文档实体
     */
    void upsertSchedule(KnowledgeDocumentDO documentDO);

    /**
     * 当文档启用/禁用时同步定时任务（仅更新已存在的任务）
     *
     * @param documentDO 文档实体
     */
    void syncScheduleIfExists(KnowledgeDocumentDO documentDO);
}
