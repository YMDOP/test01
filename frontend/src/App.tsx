import { useEffect, useState } from 'react'

import { fetchHealth, type HealthInfo } from '@/api/health'

type Status = 'loading' | 'ok' | 'error'

export default function App() {
  const [status, setStatus] = useState<Status>('loading')
  const [info, setInfo] = useState<HealthInfo | null>(null)
  const [error, setError] = useState<string>('')

  useEffect(() => {
    let alive = true
    fetchHealth()
      .then((data) => {
        if (!alive) return
        setInfo(data)
        setStatus('ok')
      })
      .catch((e: unknown) => {
        if (!alive) return
        setError(e instanceof Error ? e.message : String(e))
        setStatus('error')
      })
    return () => {
      alive = false
    }
  }, [])

  const statusLabel =
    status === 'loading' ? '检测中…' : status === 'ok' ? 'CONNECTED' : 'DISCONNECTED'

  return (
    <main className="page">
      <div className="container">
        <header className="header">
          <p className="eyebrow">// LOCAL INTERVIEW ENGINE</p>
          <h1 className="title">
            Interview<span>Forge</span>
          </h1>
          <p className="subtitle">本地优先的 Java 后端面试题库系统 · 阶段 P0 工程基线</p>
        </header>

        <section className="card">
          <div className="status-row">
            <span className={`dot ${status === 'ok' ? 'ok' : status === 'error' ? 'error' : ''}`} />
            <span className="status-text">
              backend /api/health · <b>{statusLabel}</b>
            </span>
          </div>

          {status === 'ok' && info && (
            <dl className="kv">
              <dt>应用</dt>
              <dd>{info.application}</dd>
              <dt>版本</dt>
              <dd>{info.version}</dd>
              <dt>Java</dt>
              <dd>{info.javaVersion}</dd>
              <dt>数据目录</dt>
              <dd className="accent">{info.dataDir}</dd>
              <dt>服务时间</dt>
              <dd>{info.time}</dd>
            </dl>
          )}

          {status === 'error' && (
            <p className="error-text">
              连接后端失败：{error}
              <br />
              <span>请确认 Spring Boot 已在 127.0.0.1:8081 启动。</span>
            </p>
          )}
        </section>

        <p className="footnote">P0 验收点：本页面能读到后端健康信息，即前后端联通。</p>
      </div>
    </main>
  )
}
