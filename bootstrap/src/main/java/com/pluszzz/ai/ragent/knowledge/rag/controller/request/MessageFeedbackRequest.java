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
package com.pluszzz.ai.ragent.knowledge.rag.controller.request;

import lombok.Data;

/**
 * 会话消息反馈请求
 */
@Data
public class MessageFeedbackRequest {

    /**
     * 反馈值：1=点赞，-1=点踩
     */
    private Integer vote;

    /**
     * 反馈原因（可选）
     */
    private String reason;

    /**
     * 补充说明（可选）
     */
    private String comment;
}
