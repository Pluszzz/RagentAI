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
package com.pluszzz.ai.ragent.knowledge.rag.core.mcp;

/**
 * MCP 工具执行器接口
 */
public interface MCPToolExecutor {

    /**
     * 获取工具定义
     *
     * @return 工具元信息
     */
    MCPTool getToolDefinition();

    /**
     * 执行工具调用
     *
     * @param request MCP 请求
     * @return MCP 响应
     */
    MCPResponse execute(MCPRequest request);

    /**
     * 工具 ID（快捷方法）
     */
    default String getToolId() {
        return getToolDefinition().getToolId();
    }

    /**
     * 是否支持该请求
     * 默认只检查 toolId 是否匹配
     */
    default boolean supports(MCPRequest request) {
        return getToolId().equals(request.getToolId());
    }
}
