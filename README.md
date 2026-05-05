# Blog Backend

个人博客网站后端服务，提供文章、评论、分类、标签、站点配置、媒体上传、项目展示和后台管理 REST API。

## 技术栈

- Java 17
- Spring Boot 3.5
- Spring Security
- MyBatis-Plus
- MySQL 8
- Redis 7
- Flyway
- Docker / Docker Compose

## 目录结构

```text
src/main/java/com/blog      后端源码
src/main/resources          应用配置与 Flyway migration
src/test                    单元测试
doc                         设计、API、测试与交付文档
data/uploads                本地上传文件目录，已被 git 忽略
docker-compose.yml          本地/服务器 Compose 部署配置
Dockerfile                  后端镜像构建文件
```

## 本地运行

需要先安装 Docker 和 Docker Compose v2。

```bash
docker compose up --build -d
docker compose ps
```

当前 Compose 面向同源 Nginx 部署：

- Spring Boot 容器内监听 `8080`
- 宿主机仅本机暴露 `127.0.0.1:18080 -> 8080`
- MySQL 仅本机暴露 `127.0.0.1:3306 -> 3306`
- Redis 仅本机暴露 `127.0.0.1:6379 -> 6379`
- 容器内应用通过 Compose 服务名访问 `mysql:3306`、`redis:6379`

本机验证：

```bash
curl http://127.0.0.1:18080/api/cover
```

停止服务：

```bash
docker compose down
```

## 服务器部署

推荐部署目录：

```text
/opt/Blog-backend
```

上传代码时不要上传 `target/`、`data/`、IDE 文件和日志文件。可在本机执行：

```bash
rsync -avz --delete \
  --exclude 'target/' \
  --exclude 'data/' \
  --exclude '.DS_Store' \
  --exclude '.idea/' \
  --exclude '.vscode/' \
  --exclude '*.log' \
  ./ root@39.106.15.85:/opt/Blog-backend/
```

服务器构建启动：

```bash
cd /opt/Blog-backend
docker compose down
docker compose up --build -d
docker compose ps
curl http://127.0.0.1:18080/api/cover
```

宿主机 Nginx 建议监听公网端口，例如 `http://39.106.15.85:8080`：

- `/` 托管前端 dist 静态文件
- `/api/` 反向代理到 `http://127.0.0.1:18080`
- `/uploads/` 反向代理到 `http://127.0.0.1:18080`

外网验证：

```bash
curl http://39.106.15.85:8080/api/cover
```

## 配置项

主要环境变量：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/blog...` | MySQL JDBC 地址 |
| `SPRING_DATASOURCE_USERNAME` | `blog` | MySQL 用户名 |
| `SPRING_DATASOURCE_PASSWORD` | `blog_password` | MySQL 密码 |
| `SPRING_DATA_REDIS_HOST` | `localhost` | Redis 主机 |
| `SPRING_DATA_REDIS_PORT` | `6379` | Redis 端口 |
| `APP_UPLOAD_ROOT` | `./data/uploads` | 上传文件根目录 |
| `APP_TOKEN_TTL_SECONDS` | `604800` | 登录 token 有效期 |
| `APP_ADMIN_USERNAME` | `admin` | 初始化管理员用户名 |
| `APP_ADMIN_PASSWORD` | `password123` | 初始化管理员密码 |

生产环境请修改默认管理员密码和数据库密码。

## 常用接口

公开接口：

- `GET /api/cover`
- `GET /api/profile`
- `GET /api/articles`
- `GET /api/articles/{slug}`
- `GET /api/projects`
- `GET /api/projects/{slug}`
- `GET /api/categories`
- `GET /api/tags`
- `GET /api/articles/{slug}/comments`

认证接口：

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `POST /api/auth/guest/register`

后台接口：

- `GET /api/admin/articles`
- `POST /api/admin/articles`
- `PUT /api/admin/articles/{id}`
- `DELETE /api/admin/articles/{id}`
- `GET /api/admin/projects`
- `POST /api/admin/projects`
- `PUT /api/admin/projects/{id}`
- `DELETE /api/admin/projects/{id}`
- `POST /api/admin/media/images`
- `PUT /api/admin/cover`
- `PUT /api/admin/profile`

所有接口返回统一结构 `ApiResponse<T>`，分页接口返回 `PageResponse<T>`。完整接口说明见：

- `doc/前端API接口文档.md`
- `doc/项目管理新增API文档.md`
- `doc/前端开发与测试交付文档.md`

## 测试

本地单元测试：

```bash
mvn test
```

Docker Compose 冒烟验证：

```bash
docker compose up --build -d
curl http://127.0.0.1:18080/api/cover
docker compose logs app --tail=200
```

## Git 忽略策略

仓库只提交源码、配置、Docker 构建文件和必要文档。以下内容不进入 Git：

- `target/` Maven 构建产物
- `data/` 上传文件和本地运行数据
- `.env` 本地环境变量
- IDE 配置目录
- 日志、临时文件、系统文件

Docker 构建上下文另由 `.dockerignore` 控制，避免把本地构建产物和上传文件复制进镜像构建过程。
