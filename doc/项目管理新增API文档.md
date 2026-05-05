# 项目管理新增 API 文档

本文档记录本次新增的项目管理后端能力，供前端将 `/projects` 项目页和后台“项目管理”从 `localStorage` 切换到真实后端接口。

基础地址默认：

```text
http://localhost:8080
```

统一响应、分页结构、鉴权方式与现有后端保持一致。完整通用约定可参考 `doc/前端API接口文档.md`。

## 1. 数据模型

新增表：`project`。

| 字段 | 说明 |
| --- | --- |
| `id` | 项目 ID |
| `title` | 项目标题 |
| `slug` | 项目唯一标识；创建/更新时为空则由标题生成 |
| `detailUrl` | 前端项目详情页路径，格式为 `/projects/{slug}` |
| `description` | 项目短描述，适合列表卡片展示 |
| `contentMarkdown` | 可选项目长正文，Markdown 格式，适合详情页展示 |
| `imageUrl` | 项目展示图片 URL |
| `projectUrl` | 项目外部链接，如 GitHub、Demo；不应作为整张项目卡片的默认点击地址 |
| `tags` | 标签数组，数据库中以 JSON 保存 |
| `sortOrder` | 排序值，越小越靠前 |
| `status` | `DRAFT` / `PUBLISHED` / `HIDDEN` |
| `createdAt` | 创建时间 |
| `updatedAt` | 更新时间 |

前台只返回 `PUBLISHED` 且未软删除的项目。后台返回所有未软删除项目。

图片上传可复用现有接口：

```http
POST /api/admin/media/images
```

本次已新增 `MediaUsageType.PROJECT`，前端上传项目图时可传：

```text
usageType=PROJECT
```

## 2. 枚举

```text
ProjectStatus: DRAFT, PUBLISHED, HIDDEN
MediaUsageType: ARTICLE, COVER, PROFILE, PROJECT, OTHER
```

## 3. 返回结构

### ProjectView

```json
{
  "id": 1,
  "title": "Compiler WebUI",
  "slug": "compiler-webui",
  "detailUrl": "/projects/compiler-webui",
  "description": "一个在线编译器与调试工具界面",
  "contentMarkdown": "## 项目背景\n\n这里可以写更完整的项目说明、技术方案、截图说明等。",
  "imageUrl": "/uploads/project/2026/05/xx.png",
  "projectUrl": "https://github.com/example/compiler-webui",
  "tags": ["TypeScript", "WebAssembly", "Compiler"],
  "sortOrder": 1,
  "status": "PUBLISHED",
  "createdAt": "2026-05-05T10:00:00",
  "updatedAt": "2026-05-05T10:00:00"
}
```

分页接口的 `data` 结构：

```json
{
  "items": [],
  "page": 1,
  "size": 10,
  "total": 0,
  "pages": 0
}
```

## 4. 前台接口

### 4.1 项目列表

```http
GET /api/projects
```

权限：`PUBLIC`

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `page` | 否 | 页码，从 1 开始，默认 `1` |
| `size` | 否 | 每页数量，默认 `10`，最大 `100` |
| `keyword` | 否 | 按标题或描述搜索 |

排序：

```text
sortOrder ASC, id DESC
```

示例：

```bash
curl 'http://localhost:8080/api/projects?page=1&size=10&keyword=compiler' | jq
```

成功响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "items": [
      {
        "id": 1,
        "title": "Compiler WebUI",
        "slug": "compiler-webui",
        "detailUrl": "/projects/compiler-webui",
        "description": "一个在线编译器与调试工具界面",
        "contentMarkdown": "## 项目背景\n\n这里可以写更完整的项目说明、技术方案、截图说明等。",
        "imageUrl": "/uploads/project/2026/05/xx.png",
        "projectUrl": "https://github.com/example/compiler-webui",
        "tags": ["TypeScript", "WebAssembly", "Compiler"],
        "sortOrder": 1,
        "status": "PUBLISHED",
        "createdAt": "2026-05-05T10:00:00",
        "updatedAt": "2026-05-05T10:00:00"
      }
    ],
    "page": 1,
    "size": 10,
    "total": 1,
    "pages": 1
  }
}
```

### 4.2 项目详情

```http
GET /api/projects/{slug}
```

权限：`PUBLIC`

说明：

- 仅能访问 `PUBLISHED` 项目。
- `DRAFT`、`HIDDEN`、已删除或不存在的项目返回 `NOT_FOUND`。

示例：

```bash
curl 'http://localhost:8080/api/projects/compiler-webui' | jq
```

成功响应中的 `data` 为 `ProjectView`。

## 5. 后台接口

后台接口均要求管理员 token：

```http
Authorization: Bearer <admin-token>
```

### 5.1 后台项目列表

```http
GET /api/admin/projects
```

权限：`ADMIN`

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `page` | 否 | 页码，从 1 开始，默认 `1` |
| `size` | 否 | 每页数量，默认 `10`，最大 `100` |
| `status` | 否 | `DRAFT` / `PUBLISHED` / `HIDDEN` |
| `keyword` | 否 | 按标题或描述搜索 |

示例：

```bash
curl 'http://localhost:8080/api/admin/projects?status=PUBLISHED&page=1&size=10' \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq
```

成功响应中的 `data` 为分页结构，`items[]` 为 `ProjectView`。

### 5.2 后台项目详情

```http
GET /api/admin/projects/{id}
```

权限：`ADMIN`

说明：

- 可查看 `DRAFT`、`PUBLISHED`、`HIDDEN` 项目。
- 已删除或不存在的项目返回 `NOT_FOUND`。

### 5.3 创建项目

```http
POST /api/admin/projects
```

权限：`ADMIN`

请求体：

```json
{
  "title": "Compiler WebUI",
  "slug": "compiler-webui",
  "description": "一个在线编译器与调试工具界面",
  "contentMarkdown": "## 项目背景\n\n这里可以写更完整的项目说明、技术方案、截图说明等。",
  "imageUrl": "/uploads/project/2026/05/xx.png",
  "projectUrl": "https://github.com/example/compiler-webui",
  "tags": ["TypeScript", "WebAssembly", "Compiler"],
  "sortOrder": 1,
  "status": "PUBLISHED"
}
```

字段校验：

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `title` | 是 | 1-200 字符 |
| `slug` | 否 | 最多 200 字符；为空则由标题生成 |
| `description` | 否 | 最多 1000 字符 |
| `contentMarkdown` | 否 | Markdown 长正文，不限制长度 |
| `imageUrl` | 否 | 最多 500 字符 |
| `projectUrl` | 否 | 最多 500 字符 |
| `tags` | 否 | 最多 30 个；单个标签最多 64 字符 |
| `sortOrder` | 否 | 为空默认 `0` |
| `status` | 否 | 为空默认 `DRAFT` |

处理规则：

- `slug` 会经过 slug 规范化。
- `slug` 在未软删除项目中唯一；冲突返回 `CONFLICT`。
- `tags` 会 trim、去空值、去重后保存。
- `contentMarkdown` 原样保存，后端不渲染 HTML，前端详情页按 Markdown 渲染。

成功响应中的 `data` 为 `ProjectView`。

### 5.4 更新项目

```http
PUT /api/admin/projects/{id}
```

权限：`ADMIN`

请求体同创建项目。

说明：

- 更新不存在或已删除项目返回 `NOT_FOUND`。
- 更新后的 `slug` 与其他未删除项目冲突时返回 `CONFLICT`。
- `status` 为空时会更新为默认 `DRAFT`，前端编辑时建议显式传当前状态。

成功响应中的 `data` 为 `ProjectView`。

### 5.5 删除项目

```http
DELETE /api/admin/projects/{id}
```

权限：`ADMIN`

说明：软删除项目。

成功响应：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": true
}
```

## 6. 前端接入建议

### 6.1 替换 localStorage 的映射

| 前端页面/动作 | 后端接口 |
| --- | --- |
| `/projects` 项目页 | `GET /api/projects` |
| `/projects/{slug}` 项目详情页 | `GET /api/projects/{slug}` |
| 后台项目列表 | `GET /api/admin/projects` |
| 后台项目详情/编辑回显 | `GET /api/admin/projects/{id}` |
| 新建项目 | `POST /api/admin/projects` |
| 编辑项目 | `PUT /api/admin/projects/{id}` |
| 删除项目 | `DELETE /api/admin/projects/{id}` |
| 上传项目图片 | `POST /api/admin/media/images`，`usageType=PROJECT` |

### 6.2 展示状态

- 前台列表和详情只展示 `PUBLISHED` 项目。
- 后台列表展示未删除的所有状态项目。
- `DRAFT` 和 `HIDDEN` 项目前台应视为不可访问。
- `description` 可用于项目卡片摘要，`contentMarkdown` 可用于项目详情页长说明。
- 项目卡片整体点击建议跳转 `detailUrl`，即前端路由 `/projects/{slug}`；`projectUrl` 是外部项目地址，建议放在卡片或详情页中的“访问项目 / GitHub / Demo”按钮上。

### 6.3 测试建议

- 创建 `DRAFT` 项目：后台可见，前台列表不可见，前台详情 `NOT_FOUND`。
- 创建 `PUBLISHED` 项目：后台可见，前台列表和详情可见。
- 创建 `HIDDEN` 项目：后台可见，前台列表不可见，前台详情 `NOT_FOUND`。
- 重复 slug：创建或更新返回 `CONFLICT`。
- 删除项目：后台列表不再展示，前台详情返回 `NOT_FOUND`。
- 上传项目图片：返回 URL 后可作为 `imageUrl` 保存。
