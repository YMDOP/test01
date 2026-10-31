# InterviewForge 全阶段实施方案与开发记录

> 本地优先的 Java 后端面试题库系统：文档导入 → AI 出题 → 题库沉淀 → 练习 / 模拟面试 → 错题与统计。

| 项 | 内容 |
|---|---|
| 文档版本 | v1.6 |
| 创建日期 | 2026-10-30 |
| 最后更新 | 2026-10-30 |
| 文档状态 | 待评审（方案冻结后方可进入 P0） |
| 项目根目录 | `C:\Users\33041\Desktop\study_project` |
| 后端工程 | `InterviewForge/` |
| 前端工程 | `frontend/` |
| 运行时数据 | `data/`（唯一可变目录） |
| 适用阶段 | 全阶段（P0 ～ P10） |

---

## 修订记录

> 每次方案调整在此追加一行，**不改历史行**，便于回溯决策。

| 版本 | 日期 | 修改人 | 修改内容 | 影响阶段 |
|---|---|---|---|---|
| v1.0 | 2026-10-30 | Lead | 初版：确定技术选型、数据模型、API、AI 记账口径、视觉系统、P0～P10 分阶段计划 | 全部 |
| v1.1 | 2026-10-30 | Lead | 新增第 14 章「仓库、分支与密钥管理」；确认远端仓库与分支策略；明确密钥零入库三层策略 | 全部 |
| v1.2 | 2026-10-30 | Lead | P0 完成；样式方案由 Tailwind 改为纯 CSS（记录原因）；新增 14.8 本机构建环境约定 | P0、P1 |
| v1.3 | 2026-10-30 | Lead | P1 完成；背景图默认参数与三档预设按实机效果校准；补记 SPA 回退方案 | P1 |
| v1.4 | 2026-10-30 | Lead | P2 完成；表结构改由 schema.sql 维护；记录 Boot 4 使用 Jackson 3 的适配；AI Key 走环境变量 | P2 |
| v1.5 | 2026-10-30 | Lead | P3+P4 完成；新增 generate_job / generate_job_step；分类数上限与采样上限写入决策日志 | P3、P4 |
| v1.6 | 2026-10-31 | Lead | 分类改为两级树（领域 / 主题），AI 只能选不能造；补兜底回捞与重试，保证每个分类都有题 | P4、P5 |

---

## 目录

- [0. 文档说明](#0-文档说明)
- [1. 项目概述与设计原则](#1-项目概述与设计原则)
- [2. 现状盘点](#2-现状盘点)
- [3. 技术选型与总体架构](#3-技术选型与总体架构)
- [4. 目录与存储布局](#4-目录与存储布局)
- [5. 数据模型](#5-数据模型)
- [6. API 契约](#6-api-契约)
- [7. AI 调用与 Token 计费](#7-ai-调用与-token-计费)
- [8. 前端视觉系统与交互](#8-前端视觉系统与交互)
- [9. 分阶段实施计划 P0～P10](#9-分阶段实施计划-p0p10)
- [10. 里程碑与节奏](#10-里程碑与节奏)
- [11. 风险登记与对策](#11-风险登记与对策)
- [12. 验收清单（DoD）](#12-验收清单dod)
- [13. 开发进度记录表](#13-开发进度记录表)
- [14. 仓库、分支与密钥管理](#14-仓库分支与密钥管理)
- [附录 A. AI 提示词模板](#附录-a-ai-提示词模板)
- [附录 B. 默认定价表（示例）](#附录-b-默认定价表示例)
- [附录 C. 术语表](#附录-c-术语表)

---

## 0. 文档说明

本文档同时承担两个职责：

1. **实施方案**：定义技术架构、数据模型、接口契约、视觉规范与分阶段交付物，作为开发期的唯一依据；
2. **开发记录**：通过「修订记录」与「开发进度记录表」（第 13 节）持续追加，形成可回溯的项目履历。

约定：

- 所有路径以项目根目录为基准的相对路径书写；
- 「工时」按单人 + AI 辅助编码估算，单位为人日；
- 标注 ★ 的阶段为高风险阶段，需预留缓冲；
- 方案冻结后，任何接口或表结构变更必须先更新本文档再改代码。

---

## 1. 项目概述与设计原则

### 1.1 一句话定位

用自己整理的面试笔记自动生成题库，平时本地刷题不花 token，考前做一场 AI 阅卷的模拟面试。

### 1.2 核心数据流

```
本地文档 ──(一次性/可重置)──> AI 出题 ──> 本地题库（唯一数据源）
                                          ├──> 练习模式   （本地抽题，0 AI 调用）
                                          ├──> 模拟面试   （抽题 + AI 只做阅卷）
                                          │        └──> 错题本 ──回流──> 练习
                                          ├──> 进度统计
                                          └──> 导出（md / xlsx）
```

### 1.3 四条不可妥协的设计原则

| 编号 | 原则 | 落地方式 |
|---|---|---|
| P-1 | AI 只在三个时机出现 | 分类、出题、简答阅卷；其余全部本地 |
| P-2 | 练习模式零 AI 调用 | 抽题与自评纯 DB 读写，用测试固化 |
| P-3 | AI 判分不脱离题库 | 只把「用户答案 + 标准答案 + 解析」交给模型，禁止其自行补充知识点 |
| P-4 | 数据全部本地 | 运行数据只写 `data/`，删除即恢复出厂；无登录、无云同步 |

### 1.4 明确不做的事

- 不做登录 / 注册 / 个人中心 / 权限体系（单用户本地应用）
- 不做主题切换（视觉风格固定为黑底黑客风）
- 不做云端同步、多端漫游
- 不做移动端适配优先（以 ≥1280px 桌面为主，1024px 可用即可）
- 不做扫描件 OCR（识别不出文本层时明确提示，不静默失败）

---

## 2. 现状盘点

盘点时间：2026-10-30

| 项 | 现状 | 结论 |
|---|---|---|
| `InterviewForge/pom.xml` | Spring Boot **4.1.2**，Java **17**，仅有 `spring-boot-starter` + test | 需补 web / data-jpa / sqlite / 解析器 |
| 启动类 | `com.love.interviewforge.InterviewForgeApplication` | 保留 |
| `application.yaml` | 仅 48 字节，含 `spring.application.name` | 需重写 |
| `frontend/` | 空目录 | 从零初始化 Vite |
| `docs/` | 本次创建 | 存放方案与记录 |
| 版本风险 | Boot 4 使用 Spring Framework 7 / Jakarta 11，部分三方库适配滞后 | **P0 必须先验证依赖可加载，不通过则回退 Boot 3.5.x** |

---

## 3. 技术选型与总体架构

### 3.1 选型表

| 层 | 选型 | 版本建议 | 理由 |
|---|---|---|---|
| 运行时 | Java | 17（现有） | 与 Boot 4 基线一致 |
| 框架 | Spring Boot | 4.1.2（现有），风险回退 3.5.x | 不折腾骨架 |
| Web | `spring-boot-starter-web` | 随 Boot | REST + SSE |
| 持久化 | Spring Data JPA + `hibernate-community-dialects`(SQLiteDialect) + `sqlite-jdbc` | 最新稳定 | 单文件库，零运维，天然本地 |
| 建表 | 开发期 `ddl-auto=update`，交付期改 `validate` + 手写迁移脚本 | — | 避免线上误删列 |
| Markdown | 自写轻量解析 | — | 只取标题层级与段落，无需完整 AST |
| Word | `poi-ooxml` | 5.x | 抽段落与标题样式 |
| PDF | `pdfbox` | 3.x | 抽文本层，保留页码 |
| Excel 导出 | `poi-ooxml` | 5.x | 与 Word 共用依赖 |
| AI 客户端 | `RestClient` + Jackson | 随 Boot | 不引 Spring AI，便于精确记账 / 重试 / 超时 |
| 前端构建 | Vite | 最新 | 快 |
| 前端框架 | React + TypeScript | 18/19 | 生态与可维护性 |
| 样式 | 纯 CSS + CSS 变量（不引入 UI 框架 / 不用 Tailwind） | — | 视觉完全自定义；规避原生二进制依赖在受限环境下的构建风险 |
| 图表 | 手写 SVG | — | 只需折线与进度条，避免引入图表库 |
| 打包 | Maven 单 jar + 内嵌静态资源 | — | 双击即用 |

### 3.2 部署形态

```
[浏览器] ──HTTP──> [Spring Boot :8081]
                     ├─ /api/**    REST / SSE
                     └─ /**         静态资源（frontend 构建产物）
                   [data/interviewforge.db]
```

- 开发期：Vite `:5173` 代理 `/api` 到 `:8081`
- 交付期：`:8081` 单进程对外，无前端 dev server
- 单用户，无鉴权；仅监听 `127.0.0.1`，不对外暴露

### 3.3 分层结构

```
api/        Controller：参数校验、DTO 转换、SSE 输出
service/    业务编排：Document / Generate / Question / Practice / Exam / Stats / Export
domain/     Entity + Repository（JPA）
ai/         AiClient、PromptBuilder、UsageRecorder、PricingService
ingest/     Scanner、Parser(md/docx/pdf)、Chunker
common/     统一返回、异常、分页、SSE 工具
config/     数据源、CORS、Jackson、加密
```

约束：Controller 不写业务；Service 不直接拼 SQL；Entity 不出 Controller 层。

---

## 4. 目录与存储布局

### 4.1 工程结构

```
study_project/
├── docs/
│   └── implementation-plan.md          # 本文档
├── InterviewForge/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/love/interviewforge/
│       │   ├── InterviewForgeApplication.java
│       │   ├── api/
│       │   ├── service/
│       │   ├── domain/
│       │   ├── ai/
│       │   ├── ingest/
│       │   ├── common/
│       │   └── config/
│       └── resources/
│           ├── application.yaml
│           └── static/                 # 前端构建产物
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── index.html
│   └── src/
│       ├── main.tsx
│       ├── App.tsx
│       ├── styles/                     # 设计 Token、氛围层
│       ├── components/                 # 终端风基础组件
│       ├── pages/                      # 7 个页面
│       ├── api/                        # 请求封装
│       └── hooks/
└── data/                               # ★ 唯一运行时数据目录
    ├── interviewforge.db
    ├── interviewforge.db-wal
    ├── assets/background.<ext>
    ├── exports/<timestamp>-<scope>.{md,xlsx}
    └── logs/app.log
```

### 4.2 存储规则

| 内容 | 位置 | 说明 |
|---|---|---|
| 数据库 | `data/interviewforge.db` | WAL 模式 |
| 背景图 | `data/assets/` | 上传即压缩，单文件，覆盖式 |
| 导出文件 | `data/exports/` | 保留最近 50 个，超限清理 |
| 日志 | `data/logs/app.log` | 滚动，单文件 10MB × 5 |
| 原始文档 | **不复制** | 只记录用户所选目录路径，读取时按需访问 |
| 配置 | 数据库 `app_setting` 表 | 不额外落 json，避免双写不一致 |

### 4.3 路径解析规则

- `app.data-dir` 默认 `./data`，启动时以 `user.dir` 为基准解析为绝对路径
- 启动日志必须打印解析后的绝对路径，便于排查「数据到底写哪了」
- `data/` 若不存在则自动创建，创建失败直接启动失败并给出明确错误

---

## 5. 数据模型

### 5.1 表清单（16 张）

| # | 表名 | 说明 |
|---|---|---|
| 1 | `app_setting` | 全局配置键值对 |
| 2 | `ai_config` | AI 接入配置（单行） |
| 3 | `ai_pricing` | 模型定价 |
| 4 | `ai_usage` | 每次 AI 调用一行（计费与审计） |
| 5 | `document` | 导入的文档 |
| 6 | `chunk` | 文本切片 |
| 7 | `category` | 知识分类 |
| 8 | `question` | 题目 |
| 9 | `mastery` | 掌握度 |
| 10 | `practice_session` | 练习会话 |
| 11 | `practice_item` | 练习明细 |
| 12 | `exam` | 模拟卷 |
| 13 | `exam_item` | 题目作答与判分 |
| 14 | `wrong_book` | 错题本 |
| 15 | `export_job` | 导出记录 |
| 16 | `schema_migration` | 迁移版本记录（交付期启用） |

### 5.2 字段定义

#### app_setting
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| key | TEXT | PK | 配置键，如 `ui.background.veil` |
| value | TEXT | NOT NULL | 值（字符串，复杂结构存 JSON） |
| updated_at | TEXT | NOT NULL | ISO-8601 |

#### ai_config
| 字段 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | INTEGER | PK，固定 1 | 单行配置 |
| base_url | TEXT | NOT NULL | 如 `https://api.deepseek.com` |
| model | TEXT | NOT NULL | 模型 id |
| api_key_enc | TEXT | NOT NULL | AES 加密后的 Key |
| temperature | REAL | DEFAULT 0.4 | 出题温度 |
| max_tokens | INTEGER | DEFAULT 4096 | 单次上限 |
| enabled | INTEGER | DEFAULT 1 | 0/1 |
| updated_at | TEXT | NOT NULL | — |

> Key 加密密钥由机器标识 + 固定盐派生，**不入库、不入日志**；设置页只回显掩码 `sk-****abcd`。

#### ai_pricing
| 字段 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK | — |
| model | TEXT | 模型 id |
| input_miss_price | REAL | 缓存未命中输入价，元/百万 token |
| input_hit_price | REAL | 缓存命中输入价，元/百万 token |
| cache_write_price | REAL | 缓存写入价，元/百万 token |
| output_price | REAL | 输出价，元/百万 token |
| currency | TEXT | 默认 `CNY` |
| effective_from | TEXT | 生效日期 |

#### ai_usage
| 字段 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK | — |
| ts | TEXT | 调用时间 |
| purpose | TEXT | `CLASSIFY` / `GENERATE` / `GRADE` / `CONNECTIVITY_TEST` |
| model | TEXT | 实际模型 |
| input_tokens | INTEGER | 缓存未命中输入 |
| output_tokens | INTEGER | 输出 |
| cache_read_tokens | INTEGER | 缓存命中读取 |
| cache_write_tokens | INTEGER | 缓存写入 |
| total_tokens | INTEGER | 上游返回的总数 |
| est_cost | REAL | 估算花费（元） |
| latency_ms | INTEGER | 耗时 |
| ref_type | TEXT | `CATEGORY` / `QUESTION` / `EXAM` / `DOCUMENT` |
| ref_id | INTEGER | 关联 id |
| status | TEXT | `OK` / `FAILED` |
| error | TEXT | 失败原因摘要 |

#### document
| 字段 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK | — |
| path | TEXT | 绝对路径 |
| filename | TEXT | 文件名 |
| ext | TEXT | `md`/`docx`/`pdf` |
| size | INTEGER | 字节 |
| sha256 | TEXT | 内容哈希，用于增量判断 |
| imported_at | TEXT | 导入时间 |
| parse_status | TEXT | `PENDING`/`OK`/`FAILED`/`SKIPPED` |
| parse_error | TEXT | 失败原因 |
| chunk_count | INTEGER | 切片数 |

#### chunk
| 字段 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK | — |
| document_id | INTEGER FK | 级联删除 |
| ordinal | INTEGER | 文档内序号 |
| heading_path | TEXT | 如 `第3章 > 3.2 消息队列 > 3.2.1 削峰` |
| text | TEXT | 切片全文 |
| char_count | INTEGER | 字数 |
| fingerprint | TEXT | 文本哈希，用于去重 |
| category_id | INTEGER FK | 可空，AI 分类后回填 |

#### category
| 字段 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK | — |
| name | TEXT UNIQUE | 分类名 |
| description | TEXT | AI 生成的一句话说明 |
| sort_order | INTEGER | 展示顺序 |
| origin | TEXT | `AI` / `MANUAL` |
| created_at | TEXT | — |

#### question
| 字段 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK | — |
| category_id | INTEGER FK | 非空 |
| type | TEXT | `SA` 简答 / `MC` 选择 / `TF` 判断 |
| difficulty | INTEGER | 1～5，校招定位 2～3 |
| stem | TEXT | 题干 |
| options_json | TEXT | 选择题 4 项数组；简答/判断为 NULL |
| standard_answer | TEXT | **必填** 标准答案 |
| explanation | TEXT | **必填** 参考解析 |
| source_chunk_id | INTEGER FK | **必填** 对应原文片段 |
| origin | TEXT | `AI` / `MANUAL` |
| quality_flag | TEXT | `OK` / `DUPLICATE` / `MISSING_SOURCE` |
| created_at / updated_at | TEXT | — |

> 三必填（标准答案、解析、原文片段）在入库前由 Service 强校验，缺一即拒绝入库并计数。

#### mastery
| 字段 | 类型 | 说明 |
|---|---|---|
| question_id | INTEGER PK, FK | — |
| status | TEXT | `UNTRIED` / `CORRECT` / `WRONG` / `VAGUE` |
| wrong_count | INTEGER | 累计答错次数 |
| correct_count | INTEGER | 累计答对次数 |
| last_at | TEXT | 最近变更时间 |

#### practice_session / practice_item
| 表 | 字段 |
|---|---|
| practice_session | id, params_json, started_at, ended_at, total, known, unknown, vague |
| practice_item | id, session_id, question_id, self_rating(`KNOWN/UNKNOWN/VAGUE`), answered_at |

#### exam / exam_item
| 表 | 字段 |
|---|---|
| exam | id, status(`RUNNING/SUBMITTED/ABANDONED`), blueprint_json, started_at, submitted_at, duration_sec, score, max_score |
| exam_item | id, exam_id, question_id, order_no, user_answer, score, max_score, judged(`PENDING/OK/PENDING_MANUAL`), ai_feedback_json, judged_at |

#### wrong_book / export_job
| 表 | 字段 |
|---|---|
| wrong_book | id, question_id(UNIQUE), source_type(`PRACTICE/EXAM`), source_id, created_at, resolved_at |
| export_job | id, format, scope, path, created_at |

### 5.3 关系与级联

- `document 1—N chunk`：删文档级联删切片
- `category 1—N question`：删分类时若仍有题，先要求迁移或确认级联删题
- `question 1—1 mastery`：建题即插 `UNTRIED`
- `question 1—N exam_item / practice_item`，`1—1 wrong_book`
- 删除题目：级联清理 `mastery`、`practice_item`、`exam_item`、`wrong_book`；`exam` 总分不重算（保留历史快照）
- 重置题库：清空 `question` 及其级联；`category` 可选保留；`ai_usage` **永不清理**（花费是审计数据）

### 5.4 索引

| 表 | 索引 |
|---|---|
| question | `(category_id, type)`、`(type)`、`(quality_flag)` |
| mastery | `(status)` |
| practice_item / exam_item | `(session_id)` / `(exam_id)` |
| ai_usage | `(ts)`、`(purpose)` |
| chunk | `(document_id)`、`(category_id)` |
| wrong_book | `(question_id)` 唯一 |

---

## 6. API 契约

### 6.1 通用约定

- 前缀统一 `/api`
- 返回体：`{ "code": 0, "message": "ok", "data": ... }`
- 分页：`?page=1&size=20`，返回 `{ items, total, page, size }`
- 错误码分层：`0` 成功；`1xxx` 参数；`2xxx` 业务；`3xxx` AI 上游；`5xxx` 系统
- 长任务（导入、生成、判卷）统一走 SSE，事件 `progress` / `done` / `error`
- 时间一律 ISO-8601 字符串

### 6.2 设置与外观

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/settings` | 全部配置（Key 脱敏） |
| PUT | `/api/settings` | 批量更新 |
| POST | `/api/settings/background` | multipart 上传背景图；>2560px 自动压缩 |
| DELETE | `/api/settings/background` | 移除背景图，回退纯黑 |
| GET | `/api/settings/background` | 返回图片字节，带 ETag |
| GET | `/api/health` | 健康检查 |

### 6.3 文档导入

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/documents/scan` | `{dir}` 预览清单，**不写库** |
| POST | `/api/documents/import` | `{dir, mode}`→`jobId`，SSE 推进度 |
| GET | `/api/documents` | 文档列表与解析状态 |
| DELETE | `/api/documents/{id}` | 删文档（级联切片） |
| GET | `/api/chunks` | `?documentId=&page=` 切片预览 |

### 6.4 分类与生成

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/generate/jobs` | `{categories, quotas, reset}`→`jobId` |
| GET | `/api/generate/jobs/{id}/events` | SSE：分类进度、出题进度、token 实时累计 |
| GET | `/api/generate/summary` | 总数 / 分题型 / 分类覆盖 |
| POST | `/api/generate/reset` | `{keepHistory}` 清空题库 |
| GET | `/api/categories` | 分类列表（含题量） |
| POST/PUT/DELETE | `/api/categories[/{id}]` | 增改删 |
| POST | `/api/categories/{id}/merge` | 合并到目标分类 |
| POST | `/api/categories/{id}/split` | 拆分（指定题目集合迁出） |

### 6.5 题库

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/questions` | `?categoryId&type&mastery&keyword&qualityFlag&page` |
| GET | `/api/questions/{id}` | 详情 |
| PUT | `/api/questions/{id}` | 编辑（校验三必填） |
| DELETE | `/api/questions/{id}` | 删除 |
| POST | `/api/pick` | 统一抽题，练习与模拟共用 |

抽题请求体：

```json
{
  "categoryIds": [1, 2],
  "types": ["SA"],
  "mastery": ["WRONG", "VAGUE"],
  "order": "RANDOM",
  "count": 10,
  "excludeRecentDays": 3
}
```

不足时返回 `data.insufficient = true` 并给出按条件命中的真实数量，由前端决定是否放宽。

### 6.6 练习

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/practice/sessions` | 创建并返回题目列表（含答案与否由参数决定） |
| POST | `/api/practice/sessions/{id}/rate` | `{questionId, rating}` 更新掌握度 |
| POST | `/api/practice/sessions/{id}/finish` | 结束并返回小结 |
| GET | `/api/practice/sessions/{id}` | 会话详情（用于续练） |

### 6.7 模拟面试

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/exams` | `{blueprint}` 组卷，**返回题面不含答案** |
| GET | `/api/exams/{id}` | 当前卷状态（不含答案） |
| POST | `/api/exams/{id}/answer` | `{questionId, answer}` 自动保存 |
| POST | `/api/exams/{id}/submit` | 交卷；客观题同步判，简答走 SSE |
| GET | `/api/exams/{id}/result` | 报告（含答案、解析、批注） |
| GET | `/api/exams` | 历史列表 |

blueprint 默认值：

```json
{ "total": 15, "quota": { "SA": 10, "MC": 3, "TF": 2 }, "durationMin": 45, "balancedByCategory": true }
```

### 6.8 辅助

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/wrongbook` | 错题列表（可按分类/题型筛） |
| POST | `/api/wrongbook/{id}/resolve` | 标记已掌握，移出 |
| DELETE | `/api/wrongbook/{id}` | 删除 |
| GET | `/api/stats/overview` | 总题量、掌握率、练习/模拟次数 |
| GET | `/api/stats/categories` | 分类掌握率 |
| GET | `/api/stats/trend` | `?days=30` 练习量与得分趋势 |
| GET | `/api/usage/summary` | `?range=TODAY/7D/30D/ALL` 花费聚合 |
| GET | `/api/usage/recent` | 最近 N 次调用明细 |
| POST | `/api/export` | `{format, scope, includeAnswer, includeExplanation}` |
| GET | `/api/export/{id}/download` | 下载产物 |

---

## 7. AI 调用与 Token 计费

### 7.1 三类调用

| purpose | 触发时机 | 输入 | 输出 | 典型 token |
|---|---|---|---|---|
| `CLASSIFY` | 首次生成 / 重置后 | 一批切片文本（约 8～12 个） | 分类名 + 归属 + 说明 | 输入 3k～6k，输出 0.5k |
| `GENERATE` | 每个分类一次 | 分类名 + 该分类切片 + 题型模板 | 题目 JSON 数组 | 输入 4k～8k，输出 2k～4k |
| `GRADE` | 模拟交卷后逐题 | 题干 + 标准答案 + 解析 + 用户答案 | 得分 + 命中 + 遗漏 + 建议 | 输入 1k～2k，输出 0.3k |
| `CONNECTIVITY_TEST` | 设置页点「测试」 | 一句 `ping` | 一句 `pong` | 极小 |

练习模式无调用；题库浏览、统计、导出无调用。

### 7.2 记账口径（沿用 DSH 的用量字段）

每次调用结束后，`UsageRecorder` 写入一行 `ai_usage`，字段映射：

| 本项目字段 | 上游 usage 字段 | 含义 |
|---|---|---|
| input_tokens | `prompt_tokens` 中未命中部分 | 缓存未命中输入 |
| cache_read_tokens | `prompt_cache_hit_tokens` | 缓存命中读取 |
| cache_write_tokens | `prompt_cache_miss_tokens` 中写入部分 | 缓存写入 |
| output_tokens | `completion_tokens` | 输出 |
| total_tokens | `total_tokens` | 上游总数 |

> 若上游不返回缓存细分（部分兼容接口），把全部 prompt tokens 计入 `input_tokens`，`cache_*` 记 0，并在 `err` 字段记 `NO_CACHE_DETAIL`，保证「总额恒等」不被破坏。

### 7.3 成本计算

```
cost = ( input_tokens    × input_miss_price
       + cache_read_tokens × input_hit_price
       + cache_write_tokens × cache_write_price
       + output_tokens   × output_price ) / 1_000_000
```

- 价格单位：元 / 百万 token
- 计算精度：保留 6 位小数入库，展示保留 4 位
- 上游未提供 usage 时：按字符数估算（中文约 1 字 ≈ 1.5 token），并标记 `ESTIMATED`

### 7.4 仪表盘要展示的花费指标

| 指标 | 计算 | 展示形态 |
|---|---|---|
| 今日花费 | Σ est_cost where date(ts)=today | 大数字 + 元 |
| 近 7 天 / 累计花费 | 同上按区间 | 小字并排 |
| 今日 token | Σ total_tokens | 大数字 |
| 缓存命中率 | cache_read / (input + cache_read + cache_write) | 百分比 + 进度条 |
| 用途拆分 | 按 purpose 分组金额 | 三段横向占比条 |
| 14 天花费趋势 | 按日聚合 | 自绘 SVG 折线 |
| 最近 10 次调用 | `ai_usage` 倒序 | 表格：时间/用途/模型/tokens/耗时/金额 |
| 月度预算 | 设置项 `ai.budget.monthly` | 进度条，≥80% 琥珀，≥100% 红色 + 一次性提示 |

### 7.5 省钱策略（实现要求）

1. **缓存前缀复用**：同一轮出题的 system prompt 固定不变，把分类/题型模板放在前缀，切片放在尾部，提升 `cache_read` 命中。
2. **分批粒度**：出题按分类分批（每批 1 个分类），避免单次请求过大导致重试成本翻倍。
3. **温度控制**：出题 0.4，判卷 0.1，分类 0.2。
4. **JSON 强约束**：要求纯 JSON 输出；失败重试 1 次；二次失败降级半结构化解析并打 `quality_flag`。
5. **预览成本**：生成前按「分类数 × 每类题量」估算 token 并提示预估花费，用户确认后再跑。
6. **判卷批量化（可选开关）**：同一张卷的简答题可合并为一次请求，默认关闭，稳定优先。

### 7.6 审计与对账

- 仪表盘金额必须等于 `SELECT SUM(est_cost) FROM ai_usage`
- 提供 `/api/usage/summary` 的内部一致性断言（total_tokens ≥ input + output）
- 每次生成任务的 token 消耗在 SSE `done` 事件里回传，前端直接展示「本次花了几分钱」

---

## 8. 前端视觉系统与交互

### 8.1 参考基准

调研了以下方向作为风格锚点：

- [cyberpunk-ui 设计规范](https://raw.githubusercontent.com/sickn33/agentic-awesome-skills/HEAD/plugins/agentic-awesome-skills-claude/skills/design-it/cyberpunk-ui/SKILL.md)
- [Dark Terminal 风格拆解](https://github.com/tisfeng/selectedtextkit/blob/93dc960a5ef8d52de98af41d4abd7a89576d091c/.agents/skills/fireworks-tech-graph/references/style-2-dark-terminal.md)
- [暗色作品集实现参考](https://github.com/Gagan665-h4ck/portfolio)

共同结论：纯黑底 + 单一荧光强调色 + 等宽字体 + 1px 细边框 + 低强度扫描线/噪点，**禁用大圆角与彩色渐变**。

### 8.2 设计 Token（固定，不提供切换）

| Token | 值 | 用途 |
|---|---|---|
| `--bg` | `#050607` | 页面底色 |
| `--panel` | `rgba(14,17,19,0.72)` | 面板（配 backdrop-blur 14px） |
| `--panel-solid` | `#0E1113` | 不支持毛玻璃时的兜底 |
| `--line` | `#1B2126` | 分割线 / 边框 |
| `--line-hover` | `#2A3339` | hover 边框 |
| `--text` | `#E6EDF3` | 主文字 |
| `--text-dim` | `#7C8A96` | 次要文字 |
| `--accent` | `#B6F24A` | 主强调（荧光酸绿） |
| `--accent-2` | `#22D3EE` | 数据/链接辅助色 |
| `--ok` | `#3ADE9B` | 正确 |
| `--warn` | `#FFB020` | 警告 |
| `--danger` | `#FF5C5C` | 错误 / 危险操作 |
| 字体 | `JetBrains Mono`，fallback `Consolas, monospace` | 全站等宽 |
| 圆角 | `2px` | 几乎直角 |
| 字号 | 12 / 13 / 14 / 18 / 28 / 40 | 六档 |
| 间距 | 4 的倍数（4/8/12/16/24/32/48） | — |
| 动效 | 120–180ms `ease-out` | 不做弹跳/长动画 |
| 阴影 | 不用投影，用边框与底色分层 | — |

### 8.3 氛围层

| 层 | 实现 | 强度 |
|---|---|---|
| 扫描线 | `repeating-linear-gradient` 2px 间隔 | 3% 透明度 |
| 噪点 | 内联 SVG `feTurbulence` 平铺 | 2% 透明度 |
| 角落刻线 | 面板 `::before/::after` 画 L 形 1px | 100%，仅面板四角 |
| 发光 | 焦点元素 `box-shadow: 0 0 0 1px accent, 0 0 12px -2px accent` | 仅聚焦态 |

以上氛围层均可由 `prefers-reduced-motion` 与「低特效」设置关闭。

### 8.4 背景图能力（重点需求）

**上传链路**：设置页选图 → `POST /api/settings/background` → 后端校验类型与尺寸 → 超过 2560px 等比压缩 → 转 JPG 85% → 存 `data/assets/background.jpg` → 写入设置 → 前端刷新图片层。

**渲染三层**：

```
第 1 层  图片：position:fixed; inset:0; background-size:cover;
          filter: brightness(var(--bg-bright, .30)) saturate(var(--bg-sat, .55)) contrast(.95);
          filter 之后可叠加 blur(var(--bg-blur, 0px))
第 2 层  压暗：rgba(3,4,5,var(--bg-veil, .78))
第 3 层  暗角：radial-gradient(ellipse at center, transparent 30%, rgba(0,0,0,.65) 100%)
```

**可调项**（设置页实时预览）：

| 项 | 范围 | 默认 |
|---|---|---|
| 亮度 | 0.1 – 0.7 | 0.48 |
| 模糊 | 0 – 12px | 0 |
| 压暗 | 0.4 – 0.92 | 0.62 |
| 暗角 | 开 / 关 | 开 |

三档预设：极暗（0.26 / 0.84）、标准（0.48 / 0.62）、微亮（0.60 / 0.48）。

**三档预设**：极暗 / 标准 / 微亮，一键切换后仍可微调。

**硬性要求**：

- 无背景图时纯黑 + 噪点，不得出现白色闪屏（`<html>` 背景色先给 `#050607`）
- 背景图只做氛围，**正文永远在面板上**，正文字号 ≥13px，对比度 ≥ 7:1
- 图片文件改动后靠 ETag + 时间戳刷新，避免缓存导致「换了图没变」
- 移动端/窄屏时自动隐藏背景图，降级为纯黑

### 8.5 页面清单（7 个路由）

| 路由 | 页面 | 核心区块 |
|---|---|---|
| `/` | 仪表盘 | KPI 四宫格、AI 花费卡、分类掌握率、最近错题、模拟趋势 |
| `/library` | 题库中心 | 左分类树 / 右题目表 / 抽屉编辑 |
| `/practice` | 练习 | 参数面板 → 单题卡片 → 自评三键 → 小结 |
| `/interview` | 模拟面试 | 组卷 → 计时作答 → 交卷进度 → 报告 |
| `/wrongbook` | 错题本 | 列表 + 筛选 + 一键重刷 |
| `/stats` | 统计 | 分类掌握率、题型正确率、趋势图 |
| `/settings` | 设置 | 导入、生成、重置、AI Key/定价、外观/背景图 |

**全局不出现**：登录、注册、个人中心、头像、主题切换、语言切换。

### 8.6 关键组件

| 组件 | 职责 |
|---|---|
| `TerminalCard` | 面板容器，带边框与四角刻线 |
| `Kbd` | 快捷键标识 |
| `StatusDot` | 状态点（运行中脉冲 / 成功 / 失败） |
| `MeterBar` | 掌握率、预算、进度条 |
| `DataTable` | 等宽数字、斑马纹、可排序 |
| `CommandPalette` | `Ctrl/⌘+K` 全站指令 |
| `ConfirmDialog` | 破坏性操作二次确认 |
| `EmptyState` | 空库引导（不报错） |
| `Sparkline` | 自绘 SVG 折线 |

### 8.7 键盘与交互

| 快捷键 | 行为 |
|---|---|
| `Ctrl/⌘ + K` | 命令面板 |
| `1`–`7` | 切换主页面 |
| `Space` | 练习页：标记「会」并下一题 |
| `Esc` | 退出当前模式（模拟考试中禁用，需二次确认） |
| `?` | 快捷键帮助浮层 |

其他：Toast 右下角 3 秒；破坏性操作统一二次确认；所有异步操作有 loading 骨架，不用 spinner 铺满。

### 8.8 仪表盘布局

```
┌──────────────────────────────────────────────────────────────┐
│ KPI: 题库总量 │ 知识分类 │ 总体掌握率 │ 今日练习               │
├───────────────────────────────┬──────────────────────────────┤
│ AI 花费卡                      │ 分类掌握率                   │
│ 今日 ¥x.xxx / 累计 ¥xx.xx      │ 消息队列    ████████░░ 82%   │
│ token: 12.4k  命中率 86%       │ MySQL 优化  ██████░░░░ 61%   │
│ 14 天折线（SVG）               │ Spring Sec  ████░░░░░░ 43%   │
├───────────────────────────────┼──────────────────────────────┤
│ 最近错题（5 条）               │ 最近模拟得分（列表 + 趋势）  │
└───────────────────────────────┴──────────────────────────────┘
```

---

## 9. 分阶段实施计划 P0～P10

> 单人节奏，含 AI 辅助编码，合计约 **14.5 人日**。每个阶段独立可验收，验收不通过不进入下一阶段。

### P0 · 工程基线（0.5d）

**目标**：一条命令起后端、一条命令起前端，请求能打通。

**任务**
1. `pom.xml` 增加：`spring-boot-starter-web`、`spring-boot-starter-data-jpa`、`sqlite-jdbc`、`hibernate-community-dialects`、`poi-ooxml`、`pdfbox`
2. 重写 `application.yaml`：端口 8081、`app.data-dir=./data`、SQLite 连接（WAL）、JPA 配置、上传大小限制
3. 启动时创建 `data/` 及子目录，打印绝对路径
4. Vite 初始化：React + TS + Tailwind v4、路径别名 `@`、`/api` 代理到 8081
5. 统一返回体、全局异常处理、CORS（仅 dev profile）
6. `GET /api/health` 打通

**交付物**：可启动的后端 + 可启动的前端 + 健康检查接口

**验收**
- `mvn spring-boot:run` 正常，`data/interviewforge.db` 自动生成
- 前端 `npm run dev` 后访问 `/api/health` 返回 `{"status":"ok"}`
- **风险验证**：JPA + SQLiteDialect + PDFBox + POI 同时加载不报冲突；失败则记录并回退 Boot 3.5.x（此决策需写入修订记录）

---

### P1 · 视觉系统与外壳（1.0d）★ 提前做

**目标**：黑客风外壳与背景图能力先落地，后续每页直接套，避免最后全局返工。

**任务**
1. 设计 Token 落成 CSS 变量 + Tailwind 主题扩展
2. 布局：`AppShell`（左侧栏 + 顶栏 + 内容区），侧栏图标 + 等宽文字
3. 基础组件 9 个（见 8.6）
4. 氛围层：扫描线 / 噪点 / 暗角 / 角落刻线
5. 背景图：上传接口联调 + 三层渲染 + 三档预设 + 三个滑杆 + 本地即时预览
6. 路由骨架（7 路由可空跑）+ 404 页
7. `CommandPalette` 骨架

**交付物**：可切换 7 个空页面的完整外壳 + 可用的背景图设置

**验收**
- 7 个路由可跳转，无控制台报错
- 上传背景图后，正文面板可读性不受影响（抽查对比度 ≥ 7:1）
- 刷新不闪白；删除背景图后回退纯黑
- `prefers-reduced-motion` 下动效自动关闭

---

### P2 · 数据层与设置（1.0d）

**目标**：16 张表建好，设置可读写并持久化。

**任务**
1. JPA 实体 + Repository + 启动建表（`ddl-auto=update`）
2. `SettingService`：键值读写，复杂结构存 JSON
3. `ai_config` 加密存取（AES + 机器标识派生密钥），脱敏回显
4. `ai_pricing` CRUD，预置示例定价
5. 「连通性测试」：发最小请求 → 落 `ai_usage` → 返回耗时与花费
6. 设置页表单（AI 配置、定价、预算、外观参数）

**交付物**：完整 schema + 设置页可用

**验收**
- 改设置后重启仍在
- 数据库中搜不到明文 Key，日志中也不出现
- 连通性测试成功后在仪表盘可见一行 `ai_usage`

---

### P3 · 文档导入与切片（1.5d）

**目标**：一个目录进去，切片在库里。

**任务**
1. 目录递归扫描（排除 `node_modules`/`.git`/隐藏目录），返回清单
2. 解析器三件套：md（标题层级）、docx（标题样式 + 段落 + 表格转文本）、pdf（文本层 + 页码）
3. 切片器：标题优先 → 段落 → 句号；300–800 字；相邻块重叠 50 字
4. `document` / `chunk` 落库，`sha256` + `fingerprint` 去重
5. 增量模式（对比哈希，只处理新增/变更），文件消失时清理孤儿切片
6. SSE 导入进度 + 失败清单
7. 切片预览页

**交付物**：导入功能 + 预览

**验收**
- 混合导入 20 个文档不崩，失败文件有明确原因
- 所有 chunk 长度落在 300–800
- 重复导入不产生重复 chunk
- PDF 无文本层时明确提示，而非静默 0 切片

---

### P4 · AI 分类、出题与用量记账（2.5d）★ 最高风险

**目标**：点一次按钮，题库从 0 到 100+，且每分钱可追溯。

**任务**
1. `AiClient`：RestClient、超时、重试（1 次）、SSE 流式、usage 解析
2. `UsageRecorder`：落 `ai_usage`，实时算 `est_cost`
3. 分类：切片分批 → 聚类 → 写 `category` → 回填 `chunk.category_id`
4. 分类管理：改名 / 合并 / 拆分
5. 出题：分类 × 配额 → 三题型模板 → 三必填校验 → 入库
6. 质量闸门：字段缺失拒绝入库并计数；题干相似度查重，标 `DUPLICATE`
7. 重置：二次确认 → 清题（可选保留历史）→ 重跑
8. 生成统计接口 + 前端统计卡
9. 生成前成本预估提示

**交付物**：一键生成题库全链路 + 用量台账

**验收**
- 一次生成 ≥100 题，三必填完整率 100%
- 三题型数量比例符合配额
- `ai_usage` 行数 = 实际调用次数（含失败重试）
- 中断（关页面）后任务可续跑，已生成题目保留
- 重置后旧题在库中彻底不可查，`ai_usage` 保留

---

### P5 · 题库中心与抽题服务（1.5d）

**目标**：题目可浏览、可编辑、可被精确抽取。

**任务**
1. 分类树（拖拽排序）+ 题量角标
2. 题目列表：多条件筛选、搜索、分页
3. 详情抽屉：查看 / 编辑 / 删除（编辑校验三必填）
4. `/api/pick` 统一抽题（过滤 + 排序 + 数量 + 不足降级）
5. 质量巡检面板：缺字段、无原文引用、重复题干

**交付物**：题库中心完整可用

**验收**
- 「MySQL 分类下答错的简答题」能精确命中
- 不足时返回真实命中数 + `insufficient=true`，而非空数组
- 删除题目后关联表无残留（SQL 抽查）

---

### P6 · 练习模式（1.0d）

**目标**：闭卷/开卷两种刷题体验，全程离线。

**任务**
1. 参数面板（范围 / 过滤 / 题量 / 是否显示答案）
2. 单题卡片：题干、选项、答案与解析区（受开关控制）
3. 自评三键：会 / 不会 / 模糊 → 写 `mastery`
4. 进度条 + 上一题 / 下一题 + 退出确认
5. 会话小结
6. 断点续练（可选）

**交付物**：练习全流程

**验收**
- 全流程 DevTools Network 无外部请求，`ai_usage` 无新增行
- 自评后掌握度立即体现在题库列表
- 开卷/闭卷行为符合预期

---

### P7 · 模拟面试（2.5d）

**目标**：15 题整卷 → 计时作答 → AI 阅卷 → 报告。

**任务**
1. 组卷：按配额 + 分类均衡，题面不下发答案
2. 考试页：倒计时、题号导航、自动保存、禁止查看答案
3. 交卷：客观题本地判；简答逐题 SSE 判分 + 进度
4. 判卷 prompt 与 JSON schema 校验；失败重试 1 次，仍失败标 `PENDING_MANUAL`
5. 报告页：总分、分类得分、逐题回顾、薄弱知识点
6. 错题入 `wrong_book`、掌握度更新
7. 历史记录列表

**交付物**：模拟面试全链路

**验收**
- 15 题整卷（10 简答）判分 < 60s
- 抽查 20 道简答，AI 未引入标准答案之外的知识点
- 中途断网，报告页仍可展示已判部分
- 时间到自动交卷

---

### P8 · 错题本、统计与导出（1.5d）

**任务**
1. 错题本：列表、筛选、移出、一键重刷（跳练习并预置条件）
2. 统计：分类掌握率、题型正确率、7/30 天趋势
3. 导出 md：分章节、可选答案与解析
4. 导出 xlsx：多 sheet（分类 / 题目 / 统计）
5. 导出记录与下载

**验收**
- 导出 md 能被 Typora 正常打开，标题层级正确
- xlsx 在 Excel / WPS 无损坏提示
- 同题答错 3 次，错题本仍 1 条

---

### P9 · 仪表盘与全链路联调（1.5d）

**任务**
1. 仪表盘四块内容
2. `/api/usage/summary` 聚合 + 触发时机（页面加载 + 生成结束后）
3. 花费折线自绘
4. 预算条与一次性告警
5. 全链路走查：导入 → 生成 → 练习 → 模拟 → 报告 → 错题 → 导出 → 重置

**验收**
- 仪表盘金额与 `ai_usage` 求和完全一致
- 首页加载 < 800ms（本地）
- 空库状态每页都有引导，不报错
- 重置后仪表盘历史花费仍在（审计数据不丢）

---

### P10 · 打包交付（1.0d）

**任务**
1. Vite 构建产物拷贝进 `static/`
2. Maven 打包单 jar
3. `start.bat`：检查 Java、检查端口、启动、打开浏览器
4. README：首次配置、导入、生成、重置说明
5. 冒烟清单执行

**验收**
- 干净机器双击 `start.bat` 可用（需 Java 17）
- 复制 `data/` 到另一台机器即为完整迁移
- 关闭窗口后台进程正常退出

---

## 10. 里程碑与节奏

| 里程碑 | 阶段 | 关键产出 | 累计人日 |
|---|---|---|---|
| **M0 方案冻结** | — | 本文档 v1.0 评审通过 | 0 |
| **M1 能看** | P0–P1 | 黑客风外壳 + 背景图 | 1.5 |
| **M2 能存** | P2–P3 | 文档进库、切片可见 | 4.0 |
| **M3 有题** | P4–P5 | 一键生成、题库可管理 | 8.0 |
| **M4 能用** | P6–P7 | 练习 + 模拟全链路 | 12.0 |
| **M5 能交** | P8–P10 | 统计/导出/打包 | 14.5 |

节奏建议：

- 每完成一个阶段，在本文档第 13 节追加一行进度记录；
- 每个里程碑结束做一次自测，不通过则当阶段返工，不带病推进；
- P4 与 P7 各预留 0.5 天缓冲。

---

## 11. 风险登记与对策

| # | 风险 | 概率 | 影响 | 对策 | 触发信号 |
|---|---|---|---|---|---|
| R1 | Boot 4 与 SQLite / JPA / PDFBox 兼容问题 | 中 | 高 | P0 先做依赖加载验证；不通过回退 Boot 3.5.x | 启动期 `NoClassDefFoundError` / Dialect 缺失 |
| R2 | PDF 无文本层（扫描件） | 高 | 中 | 明确提示「需 OCR」，不静默失败 | 单文件切片数 = 0 |
| R3 | AI 返回非法 JSON | 高 | 中 | schema 校验 + 重试 1 次 + 半结构化兜底 + 失败条目可单独重生成 | 解析异常率 > 5% |
| R4 | 出题质量差（重复/偏题/脱离原文） | 中 | 高 | 三必填强校验 + 相似度查重 + prompt 固定原文片段 | 查重命中率 > 10% |
| R5 | 简答判分「跑偏」 | 中 | 高 | prompt 只对照标准答案 + 输出 schema + 抽查 20 题 | 人工复核分差 > 20% |
| R6 | token 花费失控 | 中 | 中 | 分批出题 + 缓存前缀 + 生成前预估 + 预算告警 | 单次生成 > 预算 30% |
| R7 | 大文件导入 OOM | 低 | 高 | 流式读取、单文件上限 50MB、批量落库 | 堆内存告警 |
| R8 | 背景图 4K 卡顿 | 中 | 低 | 上传即压到 2560、固定层不触发重排、窄屏隐藏 | 滚动掉帧 |
| R9 | SQLite 写冲突 | 低 | 低 | 单用户 + WAL + 写操作串行 | `SQLITE_BUSY` |
| R10 | 重置误操作丢数据 | 中 | 高 | 二次确认 + 明示影响范围 + 自动备份 db 到 `data/backup/` | 用户反馈 |

---

## 12. 验收清单（DoD）

### 12.1 功能

- [ ] 目录扫描 + 三类文档解析 + 300–800 字切片
- [ ] AI 分类可改名 / 合并 / 拆分
- [ ] 一键出题，三题型，三必填 100% 完整
- [ ] 生成统计正确（总量 / 分题型 / 分类数）
- [ ] 重置题库彻底清空，可选保留历史
- [ ] 题库筛选、编辑、删除、质量巡检
- [ ] 抽题接口支持分类 / 题型 / 掌握度 / 随机
- [ ] 练习：开卷 / 闭卷、三键自评、掌握度即时更新
- [ ] 模拟：15 题组卷、计时、自动保存、交卷判分
- [ ] 报告：总分 / 分类得分 / 逐题回顾 / 薄弱点
- [ ] 错题本自动归集、去重、一键重刷
- [ ] 统计：分类掌握率、题型正确率、趋势
- [ ] 导出 md / xlsx 可正常打开

### 12.2 非功能

- [ ] 练习模式零 AI 调用（网络 + `ai_usage` 双验证）
- [ ] 仪表盘金额 = `SUM(ai_usage.est_cost)`，误差 0
- [ ] 所有运行数据在 `data/`，删除即恢复出厂
- [ ] 无登录 / 注册 / 个人中心 / 主题切换（源码中无相关文件）
- [ ] 背景图三档 + 三滑杆可用，正文对比度 ≥ 7:1
- [ ] 首屏不闪白，加载 < 800ms
- [ ] 单 jar 启动，`start.bat` 可用
- [ ] 关键错误都有明确中文提示，不出现裸堆栈

### 12.3 交付物

- [ ] 可运行的单 jar
- [ ] 前端源码与构建产物
- [ ] 本文档（含进度记录）
- [ ] README（启动与首次配置）
- [ ] 冒烟测试记录

---

## 13. 开发进度记录表

> 每完成一个阶段追加一行，**只追加不改写**。状态：`未开始` / `进行中` / `已完成` / `阻塞`。

| 阶段 | 状态 | 开始日期 | 完成日期 | 实际人日 | 交付摘要 | 遗留问题 |
|---|---|---|---|---|---|---|
| P0 工程基线 | 已完成 | 2026-10-30 | 2026-10-30 | 0.5 | 后端可启动、`/api/health` 通、SQLite 自动建库、前端可构建并嵌入 jar、浏览器端联通 | Maven/pnpm 缓存须指向工作区内（见 14.8）；Tailwind 已移除 |
| P1 视觉系统与外壳 | 已完成 | 2026-10-30 | 2026-10-30 | 1.0 | 侧栏/顶栏/7 路由、9 个终端风组件、氛围层、背景图上传与三层渲染、命令面板 | 页面内容为骨架，数据待 P3 起接入 |
| P2 数据层与设置 | 已完成 | 2026-10-30 | 2026-10-30 | 1.0 | 16 张表由 schema.sql 建立；AI 配置加密存取；连通性测试真实调用成功并落 ai_usage；Key 环境变量优先 | 定价为示例值，需按实际价目调整 |
| P3 文档导入与切片 | 已完成 | 2026-10-30 | 2026-10-30 | 1.0 | 57 份文档导入、1127 个切片（300–800 字）、按 sha256 与指纹去重 | PDF 若无文本层会记 FAILED，不做 OCR |
| P4 AI 出题与记账 | 已完成 | 2026-10-31 | 2026-10-31 | 2.0 | 两级分类树（5 领域 / 15 主题）；347 题；三必填 100%；每个二级分类都有题 | 分类由 AI 归纳，重跑可能略有差异 |
| P5 题库中心与抽题 | 未开始 | — | — | — | — | — |
| P6 练习模式 | 未开始 | — | — | — | — | — |
| P7 模拟面试 | 未开始 | — | — | — | — | — |
| P8 错题/统计/导出 | 未开始 | — | — | — | — | — |
| P9 仪表盘与联调 | 未开始 | — | — | — | — | — |
| P10 打包交付 | 未开始 | — | — | — | — | — |

### 决策日志

> 记录方案执行中出现的、需要留痕的关键决定（技术回退、接口变更、范围调整）。

| 日期 | 阶段 | 决策 | 原因 | 影响 |
|---|---|---|---|---|
| 2026-10-30 | M0 | 采用 SQLite 单文件库 | 满足「全本地、可整目录迁移」 | 所有阶段 |
| 2026-10-30 | M0 | 不引入 Spring AI，自写 AiClient | 需精确记账与重试控制 | P4 |
| 2026-10-30 | M0 | 前端不引入组件库 | 黑客风需完全自定义 | P1 |
| 2026-10-30 | P0 | 移除 Tailwind CSS，改用纯 CSS + 变量 | Tailwind v4 的 oxide 原生模块在受限构建环境中无法加载 | P1 |
| 2026-10-30 | P0 | pnpm 改为 `nodeLinker: hoisted` | 默认符号链接布局下 vite 的传递依赖解析失败 | 全部前端 |
| 2026-10-30 | P0 | 增加 `frontend/scripts/sandbox-shim.cjs` | 构建环境禁止 piped stdio 子进程，vite 的 `net use` 探测会 EPERM | 全部前端 |
| 2026-10-30 | P0 | Maven 本地仓库指向 `.cache/m2`（`-s .cache/maven-settings.xml`） | 全局仓库在用户目录外，构建进程无写权限 | 全部后端 |
| 2026-10-30 | P1 | SPA 回退用 `PathResourceResolver` 实现，而非 `/**` 转发 | 转发会吞掉 `/api/**` 的 404，资源解析器可按扩展名精确放行 | 全部前端 |
| 2026-10-30 | P1 | 背景图默认亮度 0.48 / 压暗 0.62 | 0.30/0.78 时图片几乎不可见，达不到「氛围但可读」 | P1 |
| 2026-10-30 | P2 | 表结构改用 schema.sql 维护，`ddl-auto=none` | 避免为未实现功能预建 15 个空实体；后续阶段按需加实体映射 | P3–P6 |
| 2026-10-30 | P2 | 适配 Spring Boot 4 的 Jackson 3（`tools.jackson.*`） | Boot 4 默认使用 Jackson 3，`com.fasterxml` 包不可用 | P2–P7 |
| 2026-10-30 | P2 | AI Key 只从环境变量或加密列读取，不写明文 | 满足密钥零入库；ai_config 仅在用户手填时才存密文 | 全部 |
| 2026-10-30 | P3 | 新增 `generate_job` / `generate_job_step` 两张表 | 断点续跑需要按分类粒度记录完成状态 | P3、P4 |
| 2026-10-30 | P3 | 单轮默认最多处理 300 个切片 | 全量 1127 切片需约 60 次调用，首轮验证成本与时长过高 | P3、P4 |
| 2026-10-30 | P4 | 分类总数硬上限 15 个 | 首轮 AI 自建了 127 个分类，出题调用量爆炸（实测后修正） | P4 |
| 2026-10-30 | P4 | 出题阶段只处理切片数最多的 15 个分类 | 同上；其余分类保留，可在后续轮次继续生成 | P4 |
| 2026-10-31 | P4 | 分类改为两级树：一级领域（Java 八股 / 项目实战 / 实习经历…），二级主题 | 扁平分类会被切得过细，且无法体现「项目 / 公司」维度 | P4、P5 |
| 2026-10-31 | P4 | 先由文档清单归纳分类树，归类阶段 AI 只能选不能造 | 避免自由命名导致分类爆炸（首轮曾产生 127 个） | P4 |
| 2026-10-31 | P4 | 空分类先关键词回捞，出题失败重试一次，仍为空则删除 | 保证「有分类必有题」，不留空目录 | P4 |

---

## 14. 仓库、分支与密钥管理

> 本章约束开发期的一切 Git 行为。**密钥泄露的代价远高于任何功能进度**，冲突时以本章为准。

### 14.1 仓库信息

| 项 | 值 |
|---|---|
| 远端 | `https://github.com/YMDOP/test01.git` |
| 默认分支 | `main` |
| 本地分支 | `main`（与远端同名，避免 push 时被要求指定 refspec） |
| 代码托管范围 | 源码、文档、构建配置；**不含任何密钥与运行时数据** |

### 14.2 分支与提交策略

- 单人开发，直接在 `main` 上线性提交，不额外开 feature 分支（多 agent 并行阶段除外）
- **一次提交 = 一个可编译、可验收的增量**，禁止「攒一大坨再提交」
- 里程碑打 tag：`m1-shell` / `m2-ingest` / `m3-questions` / `m4-usable` / `m5-delivery`
- 提交信息格式：`<type>(<scope>): <subject>`

| type | 用途 | scope 示例 |
|---|---|---|
| `feat` | 新功能 | `p0` `ingest` `ai` `exam` `ui` |
| `fix` | 缺陷修复 | 模块名 |
| `docs` | 文档 | `plan` `contract` |
| `refactor` | 不改变行为的重构 | 模块名 |
| `test` | 测试 | 模块名 |
| `chore` | 构建 / 依赖 / 配置 | `deps` `build` |

示例：`feat(ai): 实现分类与出题流水线，接入用量记账`

### 14.3 密钥零入库：三层优先级

| 层级 | 方式 | 是否入库 | 用途 |
|---|---|---|---|
| 1（最高） | 环境变量 `INTERVIEWFORGE_AI_API_KEY` / `INTERVIEWFORGE_AI_BASE_URL` / `INTERVIEWFORGE_AI_MODEL` | ❌ | 日常开发与 CI 首选 |
| 2 | `application-local.yaml`（已被 `.gitignore` 排除） | ❌ | 本地调试时的非密钥覆盖 |
| 3 | 设置页写入，加密后存 SQLite（密钥由机器标识 + 盐派生） | ❌（`data/` 整体忽略） | 普通用户图形化配置 |

规则：

1. 启动时按 1 → 2 → 3 顺序取配置，**环境变量永远优先**；设置页检测到环境变量时必须置灰并提示。
2. 任何配置来源的 Key **不得出现在**：日志、异常堆栈、接口响应、前端 bundle、SQLite 明文列。
3. 展示一律脱敏：`sk-****abcd`（只留后 4 位）。
4. `.env.example` 允许提交，且只放占位符，不放真实值。

### 14.4 禁止入库清单（`.gitignore` 已覆盖）

| 类别 | 举例 |
|---|---|
| 密钥与凭据 | `.env*`、`application-local.*`、`application-secret.*`、`*.pem`、`*.key`、`*.jks`、`*.p12` |
| 运行时数据 | `data/`、`*.db`、`*.db-wal`、`*.sqlite` |
| 日志与导出 | `logs/`、`*.log`、`exports/` |
| 构建产物 | `target/`、`node_modules/`、`frontend/dist/`、`dist/`、`*.jar`（wrapper 除外） |
| IDE / 系统 | `.idea/`、`*.iml`、`.vscode/`、`.DS_Store`、`Thumbs.db` |

> 若将来确有需要入库的产物，用 `git add -f` 显式放行，并在提交信息里说明原因。

### 14.5 提交前自检（每一轮必做）

1. `git status --short` —— 确认没有 `data/`、`target/`、`node_modules/` 被带上
2. `git diff --cached` —— 人工扫一遍，确认无 Key、无绝对路径、无个人信息
3. 关键字扫描：`git diff --cached | Select-String 'sk-[A-Za-z0-9]{16,}|password\s*[:=]|api[_-]?key\s*[:=]'`
4. 构建通过：后端 `mvn -q -DskipTests package`，前端 `pnpm build`
5. 提交信息符合 §14.2 格式

### 14.6 回滚流程

| 场景 | 操作 |
|---|---|
| 丢弃未提交改动（单文件） | `git checkout -- <path>` |
| 撤销最近一次提交但保留改动 | `git reset --soft HEAD~1` |
| 已推送的错误提交 | `git revert <sha>`（**不改写公共历史**） |
| 误将密钥提交但未推送 | `git reset --soft HEAD~1` → 修正 → 重新提交 |
| 误将密钥提交且已推送 | ① 立刻到服务商**吊销并轮换** Key ② `git filter-repo` 清理历史 ③ force push ④ 全文通知影响方 |

> 顺序不能反：**先吊销，再清理历史**。历史清理完成前，泄露的 Key 依然有效。

### 14.7 每轮提交映射

| 轮次 | 建议 commit | tag |
|---|---|---|
| R0 | `chore(repo): 初始化 gitignore 与接口/视觉契约` | — |
| R1 | `feat(p0): 工程基线，前后端联通` | — |
| R2 | `feat(ui): 黑客风外壳与背景图能力` | `m1-shell` |
| R3 | `feat(ingest,ai): 文档切片与题库生成` | `m2-ingest` `m3-questions` |
| R4 | `feat(question,practice): 题库中心与练习模式` | — |
| R5 | `feat(exam): 模拟面试组卷与 AI 判分` | `m4-usable` |
| R6 | `feat(stats,export): 统计、仪表盘与导出` | — |
| R7 | `chore(release): 打包与验收` | `m5-delivery` |

### 14.8 本机构建环境约定

本项目开发在受限沙箱中执行，以下约定保证命令可复现：

| 限制 | 现象 | 约定 |
|---|---|---|
| 只允许写工作区 | 全局 Maven 仓库 `C:\Java\...\mav-repo` 写入被拒 | 构建统一加 `-s .cache/maven-settings.xml`，本地仓库指向 `.cache/m2` |
| 只允许写工作区 | 默认 npm 缓存位于用户目录 | pnpm 统一加 `--store-dir .cache/pnpm-store` |
| 禁止 piped stdio 子进程 | `spawn EPERM` | 前端构建前置 `NODE_OPTIONS=--require frontend/scripts/sandbox-shim.cjs`，仅拦截 vite 的 `net use` 探测 |
| 默认符号链接布局解析失败 | `vite` 的传递依赖 `MODULE_NOT_FOUND` | `frontend/pnpm-workspace.yaml` 固定 `nodeLinker: hoisted` |
| HTTPS 出网受限 | 早期 `git push` 报 schannel 凭据错误 | 全权限模式下恢复；**当前推送走 HTTPS + Windows 凭据管理器** |

标准命令：

```powershell
# 后端构建（.cache/maven-settings.xml 为机器本地文件，不入库，内容见下）
mvn -B -s .cache/maven-settings.xml -DskipTests -f InterviewForge/pom.xml package

# 前端依赖与构建
pnpm install --dir frontend --store-dir .cache/pnpm-store
$env:NODE_OPTIONS="--require <abs>/frontend/scripts/sandbox-shim.cjs"
pnpm --dir frontend build

# 前端产物嵌入后端
Copy-Item frontend/dist/* InterviewForge/src/main/resources/static/ -Recurse -Force
```

`.cache/maven-settings.xml` 内容（路径按机器调整）：

```xml
<settings>
  <localRepository>D:/path/to/project/.cache/m2</localRepository>
  <offline>false</offline>
</settings>
```

---

## 附录 A. AI 提示词模板

### A.1 分类（CLASSIFY）

```
你是 Java 后端面试内容整理助手。
下面是从候选人笔记中截取的多段文本，请按「技术知识点」归类。

要求：
1. 分类名不超过 8 个字，优先使用业界通用叫法（如「消息队列」「MySQL 优化」「Spring Security」）。
2. 一个片段可归入多个分类时，选最贴近其主题的一个。
3. 只输出 JSON，不要解释。

输入片段：
[片段1] {text}
[片段2] {text}
...

输出格式：
{"categories":[{"name":"消息队列","description":"一句话说明","chunks":[1,4]}],
 "assignments":[{"chunk":1,"category":"消息队列"}]}
```

### A.2 出题（GENERATE）

```
你是一位有 5 年经验的 Java 后端面试官，面向校招候选人出题。

【知识分类】{category}
【参考资料】{chunks}

要求：
- 严禁脱离参考资料凭空出题，每题必须能对应到给定片段。
- 优先出带项目场景的追问，不考纯背诵。
- 选择题必须 4 个选项，干扰项取自易混概念，不能是明显错误项。
- 判断题针对容易记错的细节，正误大致各半。
- 难度按校招标准，difficulty 取 2 或 3。

输出纯 JSON：
{"questions":[
 {"type":"SA","difficulty":3,"stem":"...","standardAnswer":"...","explanation":"...","sourceChunk":12},
 {"type":"MC","difficulty":2,"stem":"...","options":["A....","B....","C....","D...."],"standardAnswer":"B","explanation":"...","sourceChunk":12},
 {"type":"TF","difficulty":2,"stem":"...","standardAnswer":"TRUE","explanation":"...","sourceChunk":12}
]}
```

> 校验点：`standardAnswer`、`explanation`、`sourceChunk` 三者缺一即整题丢弃并计入失败数。

### A.3 判卷（GRADE）

```
你是阅卷老师。请严格对照【标准答案】给【考生答案】打分，不得引入标准答案以外的知识点。

【题干】{stem}
【标准答案】{standardAnswer}
【参考解析】{explanation}
【考生答案】{userAnswer}
【满分】{maxScore}

评分规则：
- 按标准答案中的关键点逐点给分，漏点扣分，多答不额外加分。
- 考生答案为空或明显跑题，得 0 分。
- 不得因为「说法不同但意思相同」判错，也不得因为「看起来专业」给分。

只输出 JSON：
{"score": 0.0,
 "hitPoints": ["命中的要点"],
 "missedPoints": ["遗漏的要点"],
 "suggestion": "一句可执行的改进建议",
 "reason": "给分依据简述"}
```

### A.4 调用参数默认值

| 场景 | temperature | max_tokens | 超时 | 重试 |
|---|---|---|---|---|
| CLASSIFY | 0.2 | 2048 | 60s | 1 |
| GENERATE | 0.4 | 4096 | 180s | 1 |
| GRADE | 0.1 | 1024 | 60s | 1 |
| CONNECTIVITY_TEST | 0 | 32 | 15s | 0 |

---

## 附录 B. 默认定价表（示例）

> 仅用于首次启动时初始化 `ai_pricing`，**数值为结构示例**，实际请以服务商当期价目为准，可在设置页修改。

| model | input_miss_price | input_hit_price | cache_write_price | output_price | currency |
|---|---|---|---|---|---|
| deepseek-chat | 2.00 | 0.20 | 2.00 | 8.00 | CNY |
| deepseek-reasoner | 4.00 | 0.40 | 4.00 | 16.00 | CNY |

> 单位：元 / 百万 token。若服务商不区分缓存命中，把 `input_hit_price` 设为与 `input_miss_price` 相同即可，命中率仍可正常统计。

---

## 附录 C. 术语表

| 术语 | 含义 |
|---|---|
| SA | 简答题（Short Answer） |
| MC | 选择题（Multiple Choice） |
| TF | 判断题（True / False） |
| chunk | 文档切片，300–800 字，出题的最小原文单位 |
| mastery | 掌握度，四态：未做 / 答对 / 答错 / 模糊 |
| blueprint | 模拟卷组卷蓝图（题量、题型配额、时长） |
| purpose | AI 调用用途：CLASSIFY / GENERATE / GRADE |
| est_cost | 按定价表估算的单次调用花费（元） |
| 缓存命中率 | cache_read / (input + cache_read + cache_write) |
| DSH | DeepSeek Harness，本项目的用量字段参考来源 |

---

*文档结束。后续所有变更请追加到「修订记录」与「决策日志」，不要覆盖历史。*
