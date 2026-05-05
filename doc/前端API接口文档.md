# 个人博客后端 API 接口文档

本文档面向前端对接，基于当前代码实现编写。基础路径统一为 `/api`，上传文件静态访问路径为 `/uploads/**`。

## 1. 通用约定

### 1.1 请求地址

本地默认服务地址：

```text
http://localhost:8080
```

如果使用测试端口或部署域名，请替换 host，接口路径不变。

### 1.2 Content-Type

普通 JSON 接口：

```http
Content-Type: application/json
```

图片上传接口：

```http
Content-Type: multipart/form-data
```

### 1.3 认证方式

登录成功后，后端返回不透明 token。需要登录的接口使用：

```http
Authorization: Bearer <token>
```

token 默认有效期为 `604800` 秒。管理员修改自己的密码、管理员重置游客密码、管理员禁用游客后，相关用户已有 token 会失效。

### 1.4 权限标记

| 权限 | 说明 |
| --- | --- |
| `PUBLIC` | 无需登录 |
| `GUEST` | 游客或管理员登录后可访问 |
| `ADMIN` | 仅管理员可访问 |

### 1.5 统一响应

所有接口返回统一结构：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {}
}
```

失败响应：

```json
{
  "code": "UNAUTHORIZED",
  "message": "unauthorized",
  "data": null
}
```

### 1.6 分页响应

分页接口的 `data` 为：

```json
{
  "items": [],
  "page": 1,
  "size": 10,
  "total": 0,
  "pages": 0
}
```

分页参数当前实现均从 `1` 开始。带校验的分页接口要求：

| 参数 | 说明 |
| --- | --- |
| `page` | 最小值 `1` |
| `size` | 最小值 `1`，最大值 `100` |

### 1.7 常见错误码

| code | HTTP | 说明 |
| --- | ---: | --- |
| `SUCCESS` | 200 | 请求成功 |
| `VALIDATION_ERROR` | 400 | 参数校验失败 |
| `UPLOAD_ERROR` | 400 | 上传失败 |
| `UNAUTHORIZED` | 401 | 未登录、token 无效、账号密码错误 |
| `FORBIDDEN` | 403 | 登录但权限不足 |
| `NOT_FOUND` | 404 | 资源不存在 |
| `CONFLICT` | 409 | 资源冲突，如用户名、slug、标签名重复 |
| `RATE_LIMITED` | 429 | 登录失败次数过多 |
| `INTERNAL_ERROR` | 500 | 服务端内部错误 |

注意：枚举参数请使用本文档给出的英文大写值。非法枚举值属于前端参数错误，当前实现未为所有类型转换异常单独定制错误文案。

### 1.8 时间格式

时间字段为 ISO-8601 风格字符串，无时区，例如：

```text
2026-05-04T21:14:10
```

部分刚写入后直接返回的时间可能包含更高精度小数秒，前端建议按标准 ISO `LocalDateTime` 字符串解析。

### 1.9 枚举

```text
UserRole: ADMIN, GUEST
UserStatus: ACTIVE, DISABLED
ArticleStatus: DRAFT, PUBLISHED, HIDDEN
CommentStatus: VISIBLE, HIDDEN, PENDING
MediaUsageType: ARTICLE, COVER, PROFILE, OTHER
ValueType: STRING, NUMBER, BOOLEAN, JSON
```

### 1.10 重要安全约定

- 响应永远不会返回明文密码或 `passwordHash`。
- 游客评论只展示用户 `id`、`username`、`nickname`。
- 评论内容保存前会做基础 HTML escape，前端展示时不要二次当作可信 HTML 注入。
- `contentHtml` 当前第一版不做后端 Markdown 渲染，通常返回 `null`。

## 2. 通用数据结构

### UserView

```json
{
  "id": 1,
  "username": "admin",
  "nickname": "Admin",
  "role": "ADMIN"
}
```

### CategoryView

```json
{
  "id": 1,
  "name": "Tech",
  "slug": "tech",
  "description": "Technical notes",
  "sortOrder": 1
}
```

### TagView

```json
{
  "id": 1,
  "name": "Spring",
  "slug": "spring"
}
```

### ArticleSummaryView

公开文章列表和后台文章列表共用该结构。

```json
{
  "id": 1,
  "title": "First Article",
  "slug": "first-article",
  "summary": "Article summary",
  "coverImageUrl": "/uploads/article/2026/05/04/a.png",
  "category": {
    "id": 1,
    "name": "Tech",
    "slug": "tech",
    "description": "Technical notes",
    "sortOrder": 1
  },
  "tags": [
    {
      "id": 1,
      "name": "Spring",
      "slug": "spring"
    }
  ],
  "status": "PUBLISHED",
  "viewCount": 12,
  "publishedAt": "2026-05-04T21:14:03",
  "createdAt": "2026-05-04T21:14:03",
  "updatedAt": "2026-05-04T21:14:03"
}
```

### ArticleDetailView

```json
{
  "id": 1,
  "title": "First Article",
  "slug": "first-article",
  "summary": "Article summary",
  "coverImageUrl": null,
  "contentMarkdown": "# First Article",
  "contentHtml": null,
  "category": null,
  "tags": [],
  "status": "PUBLISHED",
  "viewCount": 12,
  "publishedAt": "2026-05-04T21:14:03",
  "createdAt": "2026-05-04T21:14:03",
  "updatedAt": "2026-05-04T21:14:03"
}
```

### LinkItem

用于封面链接和信息页社交链接。

```json
{
  "label": "Articles",
  "url": "/articles",
  "type": "internal",
  "sortOrder": 1
}
```

## 3. 认证接口

### 3.1 游客注册

```http
POST /api/auth/guest/register
```

权限：`PUBLIC`

请求体：

```json
{
  "username": "visitor01",
  "password": "password123",
  "nickname": "Visitor"
}
```

字段规则：

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `username` | 是 | 3-64 位，只允许字母、数字、下划线、短横线 |
| `password` | 是 | 6-64 位 |
| `nickname` | 否 | 1-64 位；为空时默认等于 `username` |

成功响应 `data`：

```json
{
  "id": 2,
  "username": "visitor01",
  "nickname": "Visitor",
  "role": "GUEST"
}
```

可能错误：

- `CONFLICT`: 用户名已存在。
- `VALIDATION_ERROR`: 参数不合法。

### 3.2 登录

```http
POST /api/auth/login
```

权限：`PUBLIC`

请求体：

```json
{
  "username": "admin",
  "password": "password123"
}
```

成功响应 `data`：

```json
{
  "token": "b26c06994a2b436cb122c8726378ebe90c42ec70668446a4be80c3d66b72c66a",
  "expiresIn": 604800,
  "user": {
    "id": 1,
    "username": "admin",
    "nickname": "Admin",
    "role": "ADMIN"
  }
}
```

规则：

- 管理员和游客共用该登录接口。
- 用户不存在、密码错误、用户禁用均返回 `UNAUTHORIZED`。
- 同一用户名和 IP 在 15 分钟内失败 5 次后返回 `RATE_LIMITED`。

### 3.3 退出登录

```http
POST /api/auth/logout
```

权限：`GUEST`

Header：

```http
Authorization: Bearer <token>
```

成功响应 `data`：

```json
true
```

### 3.4 当前登录用户

```http
GET /api/auth/me
```

权限：`GUEST`

成功响应 `data`：

```json
{
  "id": 1,
  "username": "admin",
  "nickname": "Admin",
  "role": "ADMIN"
}
```

## 4. 前台公开接口

### 4.1 获取封面配置

```http
GET /api/cover
```

权限：`PUBLIC`

成功响应 `data`：

```json
{
  "title": "Sam's Blog",
  "subtitle": "Writing, coding and life",
  "backgroundImageUrl": null,
  "avatarImageUrl": null,
  "links": [
    {
      "label": "Articles",
      "url": "/articles",
      "type": "internal",
      "sortOrder": 1
    }
  ]
}
```

### 4.2 获取信息页配置

```http
GET /api/profile
```

权限：`PUBLIC`

成功响应 `data`：

```json
{
  "displayName": "Sam Lee",
  "bio": "Personal blog owner",
  "avatarImageUrl": null,
  "email": null,
  "location": "Shanghai",
  "socialLinks": [],
  "contentMarkdown": "## About me"
}
```

### 4.3 获取文章列表

```http
GET /api/articles?page=1&size=10&categorySlug=tech&tagSlug=spring&keyword=boot
```

权限：`PUBLIC`

查询参数：

| 参数 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `page` | 否 | `1` | 页码，最小 `1` |
| `size` | 否 | `10` | 每页数量，1-100 |
| `categorySlug` | 否 | - | 分类 slug 筛选 |
| `tagSlug` | 否 | - | 标签 slug 筛选 |
| `keyword` | 否 | - | 标题或摘要模糊搜索 |

成功响应 `data`：分页结构，`items[]` 为 `ArticleSummaryView`。

规则：

- 仅返回 `status = PUBLISHED` 且未软删除文章。
- 默认按 `publishedAt`、`id` 倒序。
- 公开列表当前实现也会返回 `status`、`createdAt`、`updatedAt` 字段。

### 4.4 获取文章详情

```http
GET /api/articles/{slug}
```

权限：`PUBLIC`

路径参数：

| 参数 | 说明 |
| --- | --- |
| `slug` | 文章 slug |

成功响应 `data`：`ArticleDetailView`。

规则：

- 仅允许访问已发布且未软删除文章。
- 访问详情会异步递增浏览量。
- 当前实现详情可能先返回递增前的 `viewCount`，刷新后可见更新。

可能错误：

- `NOT_FOUND`: 文章不存在、未发布、隐藏、草稿或已删除。

### 4.5 获取分类列表

```http
GET /api/categories
```

权限：`PUBLIC`

成功响应 `data`：

```json
[
  {
    "id": 1,
    "name": "Tech",
    "slug": "tech",
    "description": "Technical notes",
    "sortOrder": 1
  }
]
```

规则：只返回未软删除分类，按 `sortOrder`、`id` 升序。

### 4.6 获取标签列表

```http
GET /api/tags
```

权限：`PUBLIC`

成功响应 `data`：

```json
[
  {
    "id": 1,
    "name": "Spring",
    "slug": "spring"
  }
]
```

规则：只返回未软删除标签，按 `name` 升序。

## 5. 评论接口

### 5.1 获取文章评论

```http
GET /api/articles/{slug}/comments?page=1&size=20
```

权限：`PUBLIC`

查询参数：

| 参数 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `page` | 否 | `1` | 页码，最小 `1` |
| `size` | 否 | `20` | 每页数量，1-100 |

成功响应 `data`：

```json
{
  "items": [
    {
      "id": 1,
      "content": "&lt;b&gt;Nice article!&lt;/b&gt;",
      "status": "VISIBLE",
      "author": {
        "id": 2,
        "username": "visitor01",
        "nickname": "Visitor"
      },
      "createdAt": "2026-05-04T21:14:10"
    }
  ],
  "page": 1,
  "size": 20,
  "total": 1,
  "pages": 1
}
```

规则：

- 文章必须存在且已发布。
- 只返回 `VISIBLE` 且未软删除评论。
- 评论内容已做 HTML escape。

### 5.2 发表评论

```http
POST /api/articles/{slug}/comments
```

权限：`GUEST`

Header：

```http
Authorization: Bearer <token>
```

请求体：

```json
{
  "content": "Nice article!"
}
```

字段规则：

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `content` | 是 | 1-2000 字符 |

成功响应 `data`：

```json
{
  "id": 1,
  "content": "Nice article!",
  "status": "VISIBLE",
  "author": {
    "id": 2,
    "username": "visitor01",
    "nickname": "Visitor"
  },
  "createdAt": "2026-05-04T21:14:10"
}
```

规则：

- 游客和管理员都可调用。
- 文章必须为已发布状态。
- 新评论状态读取系统配置 `comment.defaultStatus`，默认 `VISIBLE`。
- 后端记录 IP 和 User-Agent，但前台评论响应不返回这两个字段。

## 6. 管理后台：文章

### 6.1 后台文章列表

```http
GET /api/admin/articles?page=1&size=10&status=PUBLISHED&keyword=boot&categoryId=1
```

权限：`ADMIN`

查询参数：

| 参数 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `page` | 否 | `1` | 页码，最小 `1` |
| `size` | 否 | `10` | 每页数量，1-100 |
| `status` | 否 | - | `DRAFT` / `PUBLISHED` / `HIDDEN` |
| `keyword` | 否 | - | 标题或摘要模糊搜索 |
| `categoryId` | 否 | - | 分类 ID |

成功响应 `data`：分页结构，`items[]` 为 `ArticleSummaryView`。

规则：返回未软删除文章，按 `updatedAt` 倒序。

### 6.2 创建文章

```http
POST /api/admin/articles
```

权限：`ADMIN`

请求体：

```json
{
  "title": "First Article",
  "slug": "first-article",
  "summary": "Article summary",
  "coverImageUrl": "/uploads/article/2026/05/04/a.png",
  "contentMarkdown": "# First Article",
  "categoryId": 1,
  "tagIds": [1, 2],
  "status": "DRAFT"
}
```

字段规则：

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `title` | 是 | 1-200 字符 |
| `slug` | 否 | 最多 200 字符；为空时由标题生成 |
| `summary` | 否 | 最多 500 字符 |
| `coverImageUrl` | 否 | 最多 500 字符 |
| `contentMarkdown` | 是 | 非空 |
| `categoryId` | 否 | 如果传入，分类必须存在且未删除 |
| `tagIds` | 否 | 如果传入，所有标签必须存在且未删除 |
| `status` | 否 | `DRAFT` / `PUBLISHED` / `HIDDEN`；为空默认 `DRAFT` |

成功响应 `data`：

```json
{
  "id": 1,
  "title": "First Article",
  "slug": "first-article",
  "status": "DRAFT",
  "createdAt": "2026-05-04T21:14:03",
  "updatedAt": "2026-05-04T21:14:03"
}
```

规则：

- slug 冲突返回 `CONFLICT`。
- 状态为 `PUBLISHED` 时设置 `publishedAt`。
- 标签关系与文章主表在同一事务内保存。

### 6.3 获取后台文章详情

```http
GET /api/admin/articles/{id}
```

权限：`ADMIN`

成功响应 `data`：`ArticleDetailView`。

规则：可查看草稿、已发布和隐藏文章；已软删除文章返回 `NOT_FOUND`。

### 6.4 更新文章

```http
PUT /api/admin/articles/{id}
```

权限：`ADMIN`

请求体：同创建文章。

成功响应 `data`：

```json
{
  "id": 1,
  "title": "Updated Article",
  "slug": "updated-article",
  "status": "PUBLISHED",
  "createdAt": "2026-05-04T21:14:03",
  "updatedAt": "2026-05-04T21:20:00"
}
```

规则：

- 更新会整体替换文章标签关系。
- 文章从非发布状态首次变为 `PUBLISHED` 时设置 `publishedAt`。
- 已发布文章再次编辑不会覆盖原 `publishedAt`。
- 更新后清理文章详情和列表缓存。

### 6.5 删除文章

```http
DELETE /api/admin/articles/{id}
```

权限：`ADMIN`

成功响应 `data`：

```json
true
```

规则：软删除文章；前台不再可见；同时清理文章缓存。

## 7. 管理后台：分类和标签

当前实现没有单独的后台分类/标签列表接口。后台如需列表，可复用公开接口 `GET /api/categories`、`GET /api/tags`。

### 7.1 创建分类

```http
POST /api/admin/categories
```

权限：`ADMIN`

请求体：

```json
{
  "name": "Tech",
  "slug": "tech",
  "description": "Technical notes",
  "sortOrder": 1
}
```

字段规则：

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `name` | 是 | 1-64 字符 |
| `slug` | 否 | 最多 100 字符；为空时由名称生成 |
| `description` | 否 | 最多 255 字符 |
| `sortOrder` | 否 | 为空默认 `0` |

成功响应 `data`：`CategoryView`。

可能错误：

- `CONFLICT`: 分类 slug 已存在。

### 7.2 更新分类

```http
PUT /api/admin/categories/{id}
```

权限：`ADMIN`

请求体：同创建分类。

成功响应 `data`：

```json
true
```

### 7.3 删除分类

```http
DELETE /api/admin/categories/{id}
```

权限：`ADMIN`

成功响应 `data`：

```json
true
```

规则：

- 分类下存在未删除文章时返回 `CONFLICT`。
- 删除为软删除。

### 7.4 创建标签

```http
POST /api/admin/tags
```

权限：`ADMIN`

请求体：

```json
{
  "name": "Spring",
  "slug": "spring"
}
```

字段规则：

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `name` | 是 | 1-64 字符 |
| `slug` | 否 | 最多 100 字符；为空时由名称生成 |

成功响应 `data`：`TagView`。

可能错误：

- `CONFLICT`: 标签名称或 slug 已存在。

### 7.5 更新标签

```http
PUT /api/admin/tags/{id}
```

权限：`ADMIN`

请求体：同创建标签。

成功响应 `data`：

```json
true
```

### 7.6 删除标签

```http
DELETE /api/admin/tags/{id}
```

权限：`ADMIN`

成功响应 `data`：

```json
true
```

规则：软删除标签，并同步删除 `article_tag` 关系。

## 8. 管理后台：评论

### 8.1 评论列表

```http
GET /api/admin/comments?page=1&size=20&status=VISIBLE&articleId=1&username=visitor
```

权限：`ADMIN`

查询参数：

| 参数 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `page` | 否 | `1` | 页码，最小 `1` |
| `size` | 否 | `20` | 每页数量，1-100 |
| `status` | 否 | - | `VISIBLE` / `HIDDEN` / `PENDING` |
| `articleId` | 否 | - | 文章 ID |
| `username` | 否 | - | 用户名模糊搜索 |

成功响应 `data`：

```json
{
  "items": [
    {
      "id": 1,
      "articleId": 1,
      "articleTitle": "First Article",
      "content": "Nice article!",
      "status": "VISIBLE",
      "author": {
        "id": 2,
        "username": "visitor01",
        "nickname": "Visitor"
      },
      "ipAddress": "127.0.0.1",
      "userAgent": "Mozilla/5.0",
      "createdAt": "2026-05-04T21:14:10"
    }
  ],
  "page": 1,
  "size": 20,
  "total": 1,
  "pages": 1
}
```

规则：只返回未软删除评论，按 `createdAt` 倒序。

### 8.2 更新评论状态

```http
PATCH /api/admin/comments/{id}/status
```

权限：`ADMIN`

请求体：

```json
{
  "status": "HIDDEN"
}
```

成功响应 `data`：

```json
true
```

### 8.3 删除评论

```http
DELETE /api/admin/comments/{id}
```

权限：`ADMIN`

成功响应 `data`：

```json
true
```

规则：软删除评论。

## 9. 管理后台：游客用户和管理员密码

### 9.1 游客列表

```http
GET /api/admin/guests?page=1&size=20&username=visitor
```

权限：`ADMIN`

查询参数：

| 参数 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `page` | 否 | `1` | 页码，最小 `1` |
| `size` | 否 | `20` | 每页数量，1-100 |
| `username` | 否 | - | 用户名模糊搜索 |

成功响应 `data`：

```json
{
  "items": [
    {
      "id": 2,
      "username": "visitor01",
      "nickname": "Visitor",
      "status": "ACTIVE",
      "lastLoginAt": "2026-05-04T21:14:08",
      "createdAt": "2026-05-04T21:14:07"
    }
  ],
  "page": 1,
  "size": 20,
  "total": 1,
  "pages": 1
}
```

规则：只返回 `role = GUEST` 且未软删除用户。

### 9.2 重置游客密码

```http
PATCH /api/admin/guests/{id}/password
```

权限：`ADMIN`

请求体：

```json
{
  "newPassword": "newPassword123"
}
```

字段规则：

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `newPassword` | 是 | 6-64 位 |

成功响应 `data`：

```json
true
```

规则：

- 仅允许重置游客密码。
- 重置后该游客所有已有 token 失效。
- 不返回新密码哈希或旧密码。

### 9.3 更新游客状态

```http
PATCH /api/admin/guests/{id}/status
```

权限：`ADMIN`

请求体：

```json
{
  "status": "DISABLED"
}
```

成功响应 `data`：

```json
true
```

规则：

- `status` 可为 `ACTIVE` 或 `DISABLED`。
- 禁用游客后，该游客所有已有 token 失效，且无法再次登录。

### 9.4 修改当前管理员密码

```http
PATCH /api/admin/me/password
```

权限：`ADMIN`

请求体：

```json
{
  "oldPassword": "password123",
  "newPassword": "newPassword123"
}
```

字段规则：

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `oldPassword` | 是 | 当前密码 |
| `newPassword` | 是 | 6-64 位 |

成功响应 `data`：

```json
true
```

规则：

- 旧密码不匹配返回 `UNAUTHORIZED`。
- 修改成功后当前管理员所有已有 token 失效，前端应跳转登录页。

## 10. 管理后台：站点配置

### 10.1 获取封面配置

```http
GET /api/admin/cover
```

权限：`ADMIN`

成功响应 `data`：同 `GET /api/cover`。

### 10.2 更新封面配置

```http
PUT /api/admin/cover
```

权限：`ADMIN`

请求体：

```json
{
  "title": "Sam's Blog",
  "subtitle": "Writing, coding and life",
  "backgroundImageUrl": "/uploads/cover/2026/05/04/cover.png",
  "avatarImageUrl": "/uploads/profile/2026/05/04/avatar.png",
  "links": [
    {
      "label": "Articles",
      "url": "/articles",
      "type": "internal",
      "sortOrder": 1
    }
  ]
}
```

字段规则：

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `title` | 是 | 非空 |
| `subtitle` | 否 | 字符串或 `null` |
| `backgroundImageUrl` | 否 | 字符串或 `null` |
| `avatarImageUrl` | 否 | 字符串或 `null` |
| `links` | 否 | 数组；为空或 `null` 时保存为 `[]` |
| `links[].label` | 是 | 非空 |
| `links[].url` | 是 | 非空 |
| `links[].type` | 否 | 当前实现不限制，建议 `internal` / `external` |
| `links[].sortOrder` | 否 | 数字 |

成功响应 `data`：更新后的 `CoverView`。

### 10.3 获取信息页配置

```http
GET /api/admin/profile
```

权限：`ADMIN`

成功响应 `data`：同 `GET /api/profile`。

### 10.4 更新信息页配置

```http
PUT /api/admin/profile
```

权限：`ADMIN`

请求体：

```json
{
  "displayName": "Sam Lee",
  "bio": "Personal blog owner",
  "avatarImageUrl": "/uploads/profile/2026/05/04/avatar.png",
  "email": "me@example.com",
  "location": "Shanghai",
  "socialLinks": [
    {
      "label": "GitHub",
      "url": "https://github.com/example",
      "type": "external",
      "sortOrder": 1
    }
  ],
  "contentMarkdown": "## About me"
}
```

字段规则：

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `displayName` | 是 | 非空 |
| `bio` | 否 | 字符串或 `null` |
| `avatarImageUrl` | 否 | 字符串或 `null` |
| `email` | 否 | 当前实现不校验邮箱格式 |
| `location` | 否 | 字符串或 `null` |
| `socialLinks` | 否 | 数组；结构同 `LinkItem` |
| `contentMarkdown` | 否 | Markdown 字符串或 `null` |

成功响应 `data`：更新后的 `ProfileView`。

### 10.5 获取系统配置列表

```http
GET /api/admin/system-configs
```

权限：`ADMIN`

成功响应 `data`：

```json
[
  {
    "configKey": "upload.maxFileSizeMb",
    "configValue": "10",
    "valueType": "NUMBER",
    "description": "Maximum upload file size in MB",
    "updatedAt": "2026-05-04T21:14:03"
  }
]
```

规则：按 `configKey` 升序。

默认初始化配置：

| configKey | 默认值 | 说明 |
| --- | --- | --- |
| `site.title` | `Sam's Blog` | 站点标题 |
| `site.description` | `Personal blog` | 站点描述 |
| `comment.defaultStatus` | `VISIBLE` | 新评论默认状态 |
| `upload.maxFileSizeMb` | `10` | 上传大小限制 |
| `upload.allowedImageTypes` | `image/jpeg,image/png,image/webp,image/gif` | 允许图片 MIME |

### 10.6 更新系统配置

```http
PUT /api/admin/system-configs/{configKey}
```

权限：`ADMIN`

请求体：

```json
{
  "configValue": "10",
  "valueType": "NUMBER",
  "description": "Maximum upload file size in MB"
}
```

字段规则：

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `configValue` | 否 | 字符串或 `null` |
| `valueType` | 是 | `STRING` / `NUMBER` / `BOOLEAN` / `JSON` |
| `description` | 否 | 字符串或 `null` |

成功响应 `data`：更新后的 `SystemConfigView`。

规则：

- 只能更新已存在的 `configKey`。
- 不支持新增或修改配置 key。

## 11. 管理后台：图片上传

### 11.1 上传图片

```http
POST /api/admin/media/images
Content-Type: multipart/form-data
```

权限：`ADMIN`

表单字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `file` | 是 | 图片文件 |
| `usageType` | 是 | `ARTICLE` / `COVER` / `PROFILE` / `OTHER` |

成功响应 `data`：

```json
{
  "id": 1,
  "originalFilename": "cover.png",
  "contentType": "image/png",
  "fileSize": 1677841,
  "usageType": "COVER",
  "url": "/uploads/cover/2026/05/04/93ba6705-64b2-417f-8968-84bf5da117a1.png",
  "createdAt": "2026-05-04T21:14:17"
}
```

校验规则：

- 文件不能为空。
- MIME 必须为 `image/jpeg`、`image/png`、`image/webp`、`image/gif` 之一。
- MIME 必须在系统配置 `upload.allowedImageTypes` 中。
- 文件大小不能超过 `upload.maxFileSizeMb`。
- 文件扩展名必须与 MIME 匹配：`jpg/jpeg`、`png`、`webp`、`gif`。

存储路径：

| usageType | URL 前缀 |
| --- | --- |
| `ARTICLE` | `/uploads/article/yyyy/MM/dd/{uuid}.{ext}` |
| `COVER` | `/uploads/cover/yyyy/MM/dd/{uuid}.{ext}` |
| `PROFILE` | `/uploads/profile/yyyy/MM/dd/{uuid}.{ext}` |
| `OTHER` | `/uploads/other/yyyy/MM/dd/{uuid}.{ext}` |

### 11.2 图片列表

```http
GET /api/admin/media/images?page=1&size=20&usageType=COVER
```

权限：`ADMIN`

查询参数：

| 参数 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `page` | 否 | `1` | 页码，最小 `1` |
| `size` | 否 | `20` | 每页数量，1-100 |
| `usageType` | 否 | - | 图片用途枚举 |

成功响应 `data`：分页结构，`items[]` 为 `MediaAssetView`。

规则：只返回未软删除图片记录，按 `createdAt` 倒序。

### 11.3 删除图片

```http
DELETE /api/admin/media/images/{id}
```

权限：`ADMIN`

成功响应 `data`：

```json
true
```

规则：

- 当前实现只软删除数据库记录。
- 不会自动修改已经引用该图片 URL 的文章 Markdown 或配置。
- 当前实现不物理删除文件。

## 12. 前端对接建议

### 12.1 登录态处理

- 登录后保存 `token` 和 `user`。
- 请求 `GUEST` / `ADMIN` 接口时附加 `Authorization`。
- 遇到 `401` 时清理本地 token 并跳转登录页。
- 遇到 `403` 时展示无权限提示。
- 管理员修改密码成功后，当前 token 会失效，应立即跳转登录页。

### 12.2 Markdown 展示

- 文章详情返回 `contentMarkdown`。
- 当前 `contentHtml` 通常为 `null`，前端应自行渲染 Markdown。
- 如前端渲染 Markdown 为 HTML，请做 XSS 清洗。

### 12.3 评论展示

- 评论 `content` 已在后端 HTML escape。
- 前端普通文本展示即可，不要用未清洗的 `innerHTML`。

### 12.4 上传后使用图片

- 上传成功后取 `data.url`。
- 写文章时可插入 Markdown：

```markdown
![alt text](/uploads/article/2026/05/04/uuid.png)
```

- 更新封面或信息页时，把 `url` 填入对应图片字段。

### 12.5 接口覆盖现状

当前实现已覆盖主要第一版功能，但以下点前端需要注意：

- 没有单独后台分类列表、标签列表接口，可复用公开列表接口。
- 没有分类详情、标签详情接口。
- 没有管理员创建账号接口，管理员账号由服务启动时环境变量初始化。
- 删除文章、评论、分类、标签、图片均为软删除语义。
