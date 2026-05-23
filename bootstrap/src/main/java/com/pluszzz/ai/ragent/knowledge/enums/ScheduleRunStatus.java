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
package com.pluszzz.ai.ragent.knowledge.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 定时任务执行状态
 */
@Getter
@RequiredArgsConstructor
public enum ScheduleRunStatus {

    /**
     * 正在运行
     */
    RUNNING("running"),

    /**
     * 执行成功
     */
    SUCCESS("success"),

    /**
     * 执行失败
     */
    FAILED("failed"),

    /**
     * 已跳过
     */
    SKIPPED("skipped");

    private final String code;
}
