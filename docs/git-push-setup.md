# Git 推送通道说明

> 结论先行：**默认使用 HTTPS + Windows 凭据管理器推送**。
> SSH 密钥本身可用（Win32 OpenSSH 验证通过），但 Git 自带的 OpenSSH 在当前环境无法完成公钥认证。

## 1. 当前可用配置

```powershell
git remote -v
# origin  https://github.com/YMDOP/test01.git (fetch)
# origin  https://github.com/YMDOP/test01.git (push)

git push origin main   # 可用，凭据来自 Windows 凭据管理器（git:https://github.com）
```

验证方式：

```powershell
git ls-remote --heads origin   # 能列出 refs 即通
git status -sb                 # 无 ahead 即已同步
```

## 2. SSH 通道的实测结果

| 项目 | 结果 |
|---|---|
| 私钥 `%USERPROFILE%\.ssh\id_ed25519` | 存在，`ssh -T git@github.com` 通过 Win32 OpenSSH 返回 `Hi YMDOP!` ✅ |
| 公钥是否已加入 GitHub | 是（认证成功即证明） |
| `%USERPROFILE%\.ssh\config` 中的 github 映射 | 指向 `ssh.github.com:443`（22 端口不可用时常见写法）✅ |
| Git 自带的 OpenSSH（`C:\Program Files\Git\usr\bin\ssh.exe`） | ❌ `Permission denied (publickey)` |
| 让 Git 改用 Win32 OpenSSH（`GIT_SSH` / `core.sshCommand`） | ❌ 被安全策略禁止（禁止在 git 网络操作中覆盖 SSH 命令） |

**根因**：Git 调用的是自带的 msys OpenSSH，它在当前环境下读不到可用的身份（与 Win32 OpenSSH 的密钥解析/权限判定不同）；而替换 Git 的 SSH 命令属于被禁止的操作。

## 3. 如果将来一定要走 SSH

由人工在终端执行一次，把 Git 的 ssh 指向 Win32 版本（不在自动化流程内）：

```powershell
git config --global core.sshCommand '"C:/Users/<user>/Desktop/OpenSSH-Win64/ssh.exe"'
git remote set-url origin git@github.com:YMDOP/test01.git
git ls-remote --heads origin   # 能列出 refs 才继续
```

> 注意：该配置会改变所有仓库的 Git over SSH 行为；失败时用
> `git config --global --unset core.sshCommand` 回退，并执行
> `git remote set-url origin https://github.com/YMDOP/test01.git` 切回 HTTPS。

## 4. 提交前自检

```powershell
git status --short
git diff --cached | Select-String 'sk-[A-Za-z0-9]{16,}|api[_-]?key\s*[:=]|password\s*[:=]'
```

无命中再提交；提交后立即推送，避免本地堆积过多未推送提交。
