# RagentAI

基于 Spring Boot 的企业级 Agentic RAG 智能问答平台，覆盖知识入库、意图识别、多通道检索、MCP 工具调用、流式对话和后台运维的完整工程化方案。

## 核心能力

- **智能问答** — 多轮对话 + SSE 流式输出 + 深度思考模式
- **知识库管理** — 知识库/文档/分块全生命周期，支持文件上传与 URL 拉取
- **意图识别** — 树形意图分类，支持 KB 问答、系统交互、MCP 工具调用
- **多通道检索** — 意图定向检索 + 全局向量检索 + Rerank 后处理
- **摄取流水线** — 可编排的文档加工链（Fetcher → Parser → Enhancer → Chunker → Indexer）
- **会话记忆** — 摘要压缩 + 历史窗口，平衡上下文完整性与 Token 成本
- **MCP 工具** — 独立 MCP Server，补足知识检索之外的业务执行能力
- **模型路由** — 多供应商支持，候选模型自动回退，流式首包保护，健康熔断
- **限流排队** — 全局并发控制与排队机制，保障高并发下的系统稳定性
- **可观测性** — RAG Trace 链路追踪 + Dashboard 运营指标
- **管理后台** — 知识库管理、意图管理、链路追踪、用户管理、系统设置

## 技术栈

### 后端

| 类别 | 技术 |
|------|------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.5 |
| ORM | MyBatis-Plus |
| 鉴权 | Sa-Token |
| 缓存 | Redis + Redisson |
| 向量数据库 | Milvus 2.6 |
| 文档解析 | Apache Tika 3.2 |
| 对象存储 | S3 (RustFS) |

### 前端

| 类别 | 技术 |
|------|------|
| 框架 | React 18 + TypeScript |
| 构建 | Vite 5 |
| 样式 | Tailwind CSS |
| UI 组件 | Radix UI |
| 状态管理 | Zustand |
| 路由 | React Router 6 |
| 图表 | Recharts |

### 运行时依赖

| 服务 | 默认端口 | 用途 |
|------|---------|------|
| Ragent API | 9090 | 主业务服务 |
| MCP Server | 9099 | MCP 工具服务 |
| MySQL | 3306 | 业务数据 |
| Redis | 6379 | 认证/限流/分布式协调 |
| Milvus | 19530 | 向量检索 |
| RustFS | 9000 | 文档对象存储 |

## 项目结构

```
ragent/
├── bootstrap/       # 核心业务模块（控制器、RAG 业务、知识库、流水线、后台）
├── framework/       # 基础设施模块（通用异常、SSE、上下文、幂等、Trace）
├── infra-ai/        # AI 基础能力（LLM/Embedding/Rerank 客户端、模型路由、熔断）
├── mcp-server/      # 独立 MCP 工具服务（JSON-RPC 风格工具注册与执行）
├── frontend/        # 前端（聊天端 + 管理后台）
├── resources/       # 运维资源（数据库脚本、Docker Compose、格式化配置）
├── docs/            # 专题文档
└── scripts/         # 辅助脚本
```

## 快速启动

### 环境准备

- JDK 17+
- Maven 3.8+
- Node.js 18+
- MySQL 8.0+
- Redis 6+
- Milvus 2.6+
- S3 兼容存储（RustFS 或 MinIO）

### 1. 初始化数据库

```bash
mysql -u root -p < resources/database/schema_table.sql
```

### 2. 启动后端

```bash
# 编译
./mvnw -DskipTests clean package

# 启动（bootstrap 模块）
./mvnw -pl bootstrap spring-boot:run
```

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

### 4. 访问

- 前端聊天页: http://localhost:5173
- 管理后台: http://localhost:5173/admin
- 默认管理员: `admin` / `admin`

### 5. 启动 MCP Server（可选）

```bash
./mvnw -pl mcp-server spring-boot:run
```

## 配置说明

### 环境变量

密钥等敏感信息通过环境变量注入，启动前需配置（参考 `.env.example`）：

```bash
export BAILIAN_API_KEY=sk-xxxxx
export DEEPSEEK_API_KEY=sk-xxxxx
export SILICONFLOW_API_KEY=sk-xxxxx
export MYSQL_PASSWORD=your_password
export REDIS_PASSWORD=your_password
```

### 配置文件

核心配置位于 `bootstrap/src/main/resources/application.yaml`，主要包括：

| 配置域 | 说明 |
|--------|------|
| `spring.datasource` | MySQL 连接（默认 `root/1234@127.0.0.1:3306`） |
| `spring.data.redis` | Redis 连接 |
| `milvus` | Milvus 连接参数 |
| `ai.providers` | LLM 供应商配置（Chat/Embedding/Rerank），API Key 通过环境变量注入 |
| `ai.chat` | 模型路由与回退策略 |
| `rag.memory` | 会话记忆参数（保留轮次、压缩阈值） |
| `rag.rate-limit` | 并发限流与排队策略 |
| `rag.search` | 检索通道与后处理配置 |
| `rustfs` | 对象存储连接 |
| `sa-token` | 鉴权配置 |

## 架构亮点

- **业务编排 × AI 能力分层解耦** — `bootstrap` 负责业务编排，`infra-ai` 屏蔽模型供应商差异
- **多通道检索 + 后处理链** — 通道并行召回 + Rerank / 去重后处理，兼顾召回率与精度
- **流式首包保护** — 模型失败时无缝切换候选，不污染客户端输出
- **全链路可观测** — `@RagTraceRoot` / `@RagTraceNode` 注解驱动的 Trace 体系
- **摄取流水线可编排** — 节点化文档加工链，支持条件跳过与环路检测

## License

Apache License 2.0 — 详见 [LICENSE](./LICENSE)
