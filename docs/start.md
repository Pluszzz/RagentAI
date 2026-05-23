<!--
快速启动说明文件：start.md
位置: docs/start.md
-->
# 快速启动指南

本文档介绍如何在本地快速启动并运行该项目（后端 + 前端），包含依赖服务、构建与运行命令以及常见问题提示。

## 目标
- 在本地启动后端服务（Spring Boot）、前端开发服务器（Vite）以及必要的依赖服务（Milvus、MySQL、Redis）。

## 前提（请先安装）
- Java JDK 17
- Maven（可使用仓库内的 Maven Wrapper，即 `mvnw` / `mvnw.cmd`）
- Node.js 18+ + npm
- Docker 与 Docker Compose
- Git（可选，用于 clone 仓库）

## 目录结构（关键信息）
- 后端主模块：`bootstrap`（Spring Boot 启动）
- 前端：`frontend`（Vite + React）
- Milvus docker compose：`resources/docker/milvus/milvus-stack-2.6.6.compose.yaml`
- 数据库脚本：`resources/database/schema_table.sql`、`resources/database/init_data.sql`

## 一步步来

> 下面的命令在 Windows PowerShell 下用 `./mvnw.cmd`，在 Linux/macOS 下用 `./mvnw`（或直接使用系统的 `mvn`）。

### 1. 启动依赖服务（推荐 Docker）

1) 启动 Milvus（仓库内已包含 compose 文件）

```bash
docker compose -f resources/docker/milvus/milvus-stack-2.6.6.compose.yaml up -d
```

2) 启动 MySQL（演示用途）

```bash
docker run -d --name ragent-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=ragent -p 3306:3306 mysql:8.0
# 导入 schema
docker exec -i ragent-mysql sh -c 'exec mysql -uroot -proot ragent' < resources/database/schema_table.sql
# 导入初始数据（可选）
docker exec -i ragent-mysql sh -c 'exec mysql -uroot -proot ragent' < resources/database/init_data.sql
```

3) 启动 Redis（与默认应用配置一致：密码 `123456`）

```bash
docker run -d --name ragent-redis -p 6379:6379 redis:7 redis-server --requirepass 123456
```

注：如果你已有 MySQL/Redis 服务，可直接调整 `bootstrap/src/main/resources/application.yaml` 中的连接配置。

### 2. 检查与调整后端配置

- 后端默认配置位于 `bootstrap/src/main/resources/application.yaml`，主要项：
  - MySQL: `spring.datasource.url`、`username`、`password`
  - Redis: `spring.data.redis.host`、`port`、`password`
  - Milvus: `milvus.uri`
  - AI 提供商的 API KEY 可通过环境变量注入（参见 `application.yaml` 中的 `${...}` 占位符）。

在本地第一次运行前，请根据实际环境修改这些值（或在运行时通过环境变量覆盖）。

### 3. 构建并运行后端

在项目根目录执行：

Windows (Powershell):
```powershell
./mvnw.cmd -DskipTests package
# 运行 bootstrap 模块（开发模式）
./mvnw.cmd -pl bootstrap spring-boot:run
```

Linux/macOS:
```bash
./mvnw -DskipTests package
./mvnw -pl bootstrap spring-boot:run
```

或直接运行打包的 Jar（打包完成后）：

```bash
java -jar bootstrap/target/*bootstrap*.jar
```

后端默认监听：`http://localhost:8080`，上下文路径根据 `application.yaml` 设置为 `/api/ragent`。

### 4. 启动前端（开发模式）

进入前端目录并安装依赖：

```bash
cd frontend
npm install
npm run dev
```

默认 Vite 开发服务器地址：`http://localhost:5173`（控制台会显示实际端口）。前端已配置与后端的 API 交互（如需调整代理或 API 地址，请查看 `frontend/vite.config.*` 和 `src/services`）。

### 5. 初次使用检查项
- 后端日志查看是否连接到 MySQL、Redis、Milvus 成功。
- 若出现表不存在，请确认已成功导入 `resources/database/schema_table.sql`。
- 若想使用本地 Ollama / 本地模型，请根据 `application.yaml` 配置相应 `ai.providers` 地址与端口。

### 6. 常见快捷命令（汇总）

在 Windows PowerShell：
```powershell
# 启动依赖（docker）
docker compose -f resources/docker/milvus/milvus-stack-2.6.6.compose.yaml up -d
docker run -d --name ragent-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=ragent -p 3306:3306 mysql:8.0
docker exec -i ragent-mysql sh -c 'exec mysql -uroot -proot ragent' < resources/database/schema_table.sql
docker run -d --name ragent-redis -p 6379:6379 redis:7 redis-server --requirepass 123456

# 后端
./mvnw.cmd -DskipTests package
./mvnw.cmd -pl bootstrap spring-boot:run

# 前端
cd frontend
npm install
npm run dev
```

## 故障排查提示
- 如果端口被占用，调整 `application.yaml` 中 `server.port` 或关闭占用程序。
- 数据库连接失败：确认 MySQL 容器状态并核对 `spring.datasource.url` 与账号密码。
- Milvus 连接失败：确认 `docker compose` 中的容器已就绪并检查 `milvus.uri`。
- Redis 认证失败：确认使用了 `--requirepass` 后，应用配置里写了相同密码。

## 下一步建议
- 访问前端页面，注册/登录并在管理后台导入测试文档进行检索试验。
- 若需在生产环境部署，可将 `application.yaml` 的敏感配置改为环境变量或使用配置中心。

---

文件位置: docs/start.md

如需我把文档同步到 README 或生成更详细的“部署到生产”指南，我可以继续完善。
