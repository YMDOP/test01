-- ============================================================
-- InterviewForge 数据库结构（SQLite）
-- 约定：
--   1. 全部使用 IF NOT EXISTS，启动时幂等执行
--   2. 时间统一存 ISO-8601 字符串（Text），便于按 substr 做按日聚合
--   3. 外键开启级联删除由 PRAGMA foreign_keys=ON 保证（Hikari init-sql 已配）
-- ============================================================

-- 1. 全局配置
CREATE TABLE IF NOT EXISTS app_setting (
    setting_key   TEXT PRIMARY KEY,
    setting_value TEXT,
    updated_at    TEXT NOT NULL
);

-- 2. AI 接入配置（单行，id 固定为 1）
CREATE TABLE IF NOT EXISTS ai_config (
    id          INTEGER PRIMARY KEY CHECK (id = 1),
    base_url    TEXT    NOT NULL,
    model       TEXT    NOT NULL,
    api_key_enc TEXT,
    temperature REAL    NOT NULL DEFAULT 0.4,
    max_tokens  INTEGER NOT NULL DEFAULT 4096,
    enabled     INTEGER NOT NULL DEFAULT 1,
    updated_at  TEXT    NOT NULL
);

-- 3. 模型定价（元 / 百万 token）
CREATE TABLE IF NOT EXISTS ai_pricing (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    model             TEXT    NOT NULL,
    input_miss_price  REAL    NOT NULL,
    input_hit_price   REAL    NOT NULL,
    cache_write_price REAL    NOT NULL,
    output_price      REAL    NOT NULL,
    currency          TEXT    NOT NULL DEFAULT 'CNY',
    effective_from    TEXT    NOT NULL
);

-- 4. 每次 AI 调用一行（计费与审计，永不清理）
CREATE TABLE IF NOT EXISTS ai_usage (
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    ts                 TEXT    NOT NULL,
    purpose            TEXT    NOT NULL,
    model              TEXT,
    input_tokens       INTEGER NOT NULL DEFAULT 0,
    output_tokens      INTEGER NOT NULL DEFAULT 0,
    cache_read_tokens  INTEGER NOT NULL DEFAULT 0,
    cache_write_tokens INTEGER NOT NULL DEFAULT 0,
    total_tokens       INTEGER NOT NULL DEFAULT 0,
    est_cost           REAL    NOT NULL DEFAULT 0,
    latency_ms         INTEGER NOT NULL DEFAULT 0,
    ref_type           TEXT,
    ref_id             INTEGER,
    status             TEXT    NOT NULL,
    error              TEXT
);
CREATE INDEX IF NOT EXISTS idx_ai_usage_ts      ON ai_usage (ts);
CREATE INDEX IF NOT EXISTS idx_ai_usage_purpose ON ai_usage (purpose);

-- 5. 导入文档（只记路径，不复制原文件）
CREATE TABLE IF NOT EXISTS document (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    path         TEXT    NOT NULL,
    filename     TEXT    NOT NULL,
    ext          TEXT    NOT NULL,
    size         INTEGER NOT NULL DEFAULT 0,
    sha256       TEXT,
    imported_at  TEXT    NOT NULL,
    parse_status TEXT    NOT NULL DEFAULT 'PENDING',
    parse_error  TEXT,
    chunk_count  INTEGER NOT NULL DEFAULT 0
);

-- 6. 文本切片
CREATE TABLE IF NOT EXISTS chunk (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    document_id  INTEGER NOT NULL REFERENCES document (id) ON DELETE CASCADE,
    ordinal      INTEGER NOT NULL,
    heading_path TEXT,
    text         TEXT    NOT NULL,
    char_count   INTEGER NOT NULL,
    fingerprint  TEXT,
    category_id  INTEGER REFERENCES category (id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_chunk_document ON chunk (document_id);
CREATE INDEX IF NOT EXISTS idx_chunk_category ON chunk (category_id);

-- 7. 知识分类
CREATE TABLE IF NOT EXISTS category (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL UNIQUE,
    description TEXT,
    parent_id   INTEGER REFERENCES category (id) ON DELETE CASCADE,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    origin      TEXT    NOT NULL DEFAULT 'AI',
    created_at  TEXT    NOT NULL
);

-- 8. 题目（三必填：标准答案 / 解析 / 来源切片）
CREATE TABLE IF NOT EXISTS question (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    category_id     INTEGER NOT NULL REFERENCES category (id) ON DELETE CASCADE,
    type            TEXT    NOT NULL CHECK (type IN ('SA', 'MC', 'TF')),
    difficulty      INTEGER NOT NULL DEFAULT 3,
    stem            TEXT    NOT NULL,
    options_json    TEXT,
    standard_answer TEXT    NOT NULL,
    explanation     TEXT    NOT NULL,
    source_chunk_id INTEGER NOT NULL REFERENCES chunk (id) ON DELETE CASCADE,
    origin          TEXT    NOT NULL DEFAULT 'AI',
    quality_flag    TEXT    NOT NULL DEFAULT 'OK',
    created_at      TEXT    NOT NULL,
    updated_at      TEXT    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_question_category ON question (category_id, type);
CREATE INDEX IF NOT EXISTS idx_question_type     ON question (type);
CREATE INDEX IF NOT EXISTS idx_question_quality  ON question (quality_flag);

-- 9. 掌握度
CREATE TABLE IF NOT EXISTS mastery (
    question_id   INTEGER PRIMARY KEY REFERENCES question (id) ON DELETE CASCADE,
    status        TEXT    NOT NULL DEFAULT 'UNTRIED',
    wrong_count   INTEGER NOT NULL DEFAULT 0,
    correct_count INTEGER NOT NULL DEFAULT 0,
    last_at       TEXT
);
CREATE INDEX IF NOT EXISTS idx_mastery_status ON mastery (status);

-- 10/11. 练习会话与明细
CREATE TABLE IF NOT EXISTS practice_session (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    params_json TEXT,
    started_at  TEXT    NOT NULL,
    ended_at    TEXT,
    total       INTEGER NOT NULL DEFAULT 0,
    known       INTEGER NOT NULL DEFAULT 0,
    unknown     INTEGER NOT NULL DEFAULT 0,
    vague       INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS practice_item (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id  INTEGER NOT NULL REFERENCES practice_session (id) ON DELETE CASCADE,
    question_id INTEGER NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    self_rating TEXT    NOT NULL,
    answered_at TEXT    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_practice_item_session ON practice_item (session_id);

-- 12/13. 模拟卷与逐题
CREATE TABLE IF NOT EXISTS exam (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    status        TEXT    NOT NULL DEFAULT 'RUNNING',
    blueprint_json TEXT,
    started_at    TEXT    NOT NULL,
    submitted_at  TEXT,
    duration_sec  INTEGER,
    score         REAL,
    max_score     REAL
);
CREATE TABLE IF NOT EXISTS exam_item (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    exam_id          INTEGER NOT NULL REFERENCES exam (id) ON DELETE CASCADE,
    question_id      INTEGER NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    order_no         INTEGER NOT NULL,
    user_answer      TEXT,
    score            REAL,
    max_score        REAL,
    judged           TEXT    NOT NULL DEFAULT 'PENDING',
    ai_feedback_json TEXT,
    judged_at        TEXT
);
CREATE INDEX IF NOT EXISTS idx_exam_item_exam ON exam_item (exam_id);

-- 14. 错题本（按题去重）
CREATE TABLE IF NOT EXISTS wrong_book (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    question_id INTEGER NOT NULL UNIQUE REFERENCES question (id) ON DELETE CASCADE,
    source_type TEXT    NOT NULL,
    source_id   INTEGER,
    created_at  TEXT    NOT NULL,
    resolved_at TEXT
);

-- 15. 导出记录
CREATE TABLE IF NOT EXISTS export_job (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    format     TEXT NOT NULL,
    scope      TEXT,
    path       TEXT NOT NULL,
    created_at TEXT NOT NULL
);

-- 16. 迁移版本
CREATE TABLE IF NOT EXISTS schema_migration (
    version    TEXT PRIMARY KEY,
    applied_at TEXT NOT NULL
);

-- 17. 生成任务（主记录）
CREATE TABLE IF NOT EXISTS generate_job (
    id            TEXT PRIMARY KEY,
    status        TEXT    NOT NULL,
    stage         TEXT,
    params_json   TEXT,
    progress      INTEGER NOT NULL DEFAULT 0,
    total_chunks  INTEGER NOT NULL DEFAULT 0,
    total_categories INTEGER NOT NULL DEFAULT 0,
    done_categories  INTEGER NOT NULL DEFAULT 0,
    question_count   INTEGER NOT NULL DEFAULT 0,
    estimated_cost   REAL    NOT NULL DEFAULT 0,
    message       TEXT,
    stats_json    TEXT,
    created_at    TEXT    NOT NULL,
    updated_at    TEXT    NOT NULL
);

-- 18. 生成任务的分步进度（按分类粒度，用于断点续跑）
CREATE TABLE IF NOT EXISTS generate_job_step (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id      TEXT    NOT NULL,
    category_id INTEGER,
    category_name TEXT,
    status      TEXT    NOT NULL,
    question_count INTEGER NOT NULL DEFAULT 0,
    error       TEXT,
    updated_at  TEXT    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_generate_job_step_job ON generate_job_step (job_id);
