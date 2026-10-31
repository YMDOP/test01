# InterviewForge 构建与运行说明

> 适用版本：v1.0（P0–P10 全部完成）

## 1. 环境要求

| 项 | 要求 | 说明 |
|---|---|---|
| JDK | 17 或更高 | 运行与构建都需要，`java -version` 能输出即可 |
| Maven | 3.9+ | 仅构建需要 |
| Node.js | 20+、pnpm 9+ | 仅构建前端需要；运行不需要 |
| 端口 | 8081 空闲 | 被占用时 `start.bat` 会直接报错退出 |

## 2. 一键构建

```powershell
# 在项目根目录
powershell -ExecutionPolicy Bypass -File build.ps1
```

脚本会依次：安装前端依赖 → `mvn clean package`（**Maven 会自动编译前端并打进 jar**）→ 打印产物路径。

产物：

```
InterviewForge/target/InterviewForge-0.0.1-SNAPSHOT.jar
```

## 3. 启动

**Windows**：双击 `start.bat`

脚本会：

1. 检查 JDK 版本（低于 17 给出中文提示并退出）
2. 检查 8081 是否被占用
3. 在独立窗口启动服务
4. 轮询 `/api/health`，就绪后自动打开浏览器
5. 关闭服务窗口即停止服务

**macOS / Linux**：

```bash
chmod +x start.sh && ./start.sh
```

手动启动（前台）：

```bash
java -Dfile.encoding=UTF-8 -jar InterviewForge/target/InterviewForge-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

## 4. 数据目录

所有运行时数据都在 `data/`：

```
data/
├── interviewforge.db          SQLite 主库
├── interviewforge.db-wal      WAL 日志
├── assets/                    背景图
├── exports/                   导出的 md / xlsx
├── backup/                    重置前的数据库快照
└── logs/app.log               运行日志（prod 配置）
```

- 数据目录优先级：`INTERVIEWFORGE_DATA_DIR` 环境变量 > `-Dapp.data-dir` 系统属性 > `./data`
- **备份**：直接复制整个 `data/`；重置题库时也会自动在 `data/backup/` 留一份
- **恢复**：停止服务 → 用备份覆盖 `interviewforge.db` → 删除同目录的 `-wal` / `-shm` → 重启

## 5. AI 配置

优先级：

1. 环境变量 `INTERVIEWFORGE_AI_API_KEY`（或 `DEEPSEEK_API_KEY`）
2. 设置页填写（AES 加密后存本地库，接口只回显后 4 位）

Base URL 默认 `https://api.deepseek.com`，模型默认 `deepseek-chat`，可在设置页修改。

## 6. 首次使用

1. 打开 `设置` → `文档导入`，填写笔记目录 → `扫描目录` → `导入并切片`
2. 回到 `设置` → `生成题库`，确认预估花费后开始（会调用 AI 分类与出题）
3. `题库中心` 浏览编辑，`练习模式` 自评刷题，`模拟面试` 做整卷并看 AI 阅卷报告

## 7. 常见问题

| 现象 | 原因 / 处理 |
|---|---|
| `[错误] 未检测到 Java` | 没有 JDK 或未加入 PATH |
| `[错误] Java 版本过低` | 需要 17+，检查 `java -version` |
| `[错误] 端口 8081 已被占用` | 关掉占用端口的进程，或改用 `--server.port=8082` |
| 页面空白 | 打开 `/api/health` 看后端是否正常；查看 `data/logs/app.log` |
| 构建时报 pnpm 不存在 | `npm i -g pnpm` 后重试 |
| 导入后没有题 | 需要先在设置页执行「生成题库」 |

## 8. 目录结构

```
study_project/
├── build.ps1          一键构建（前端 + 后端）
├── start.bat          Windows 启动
├── start.sh           macOS/Linux 启动
├── data/              运行时数据（不入库）
├── docs/              方案、运行说明、截图
├── frontend/          React + Vite + TypeScript（纯 CSS）
└── InterviewForge/    Spring Boot 4 + SQLite
```
