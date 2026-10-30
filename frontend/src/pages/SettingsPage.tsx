import { useEffect, useRef, useState } from 'react'

import { fetchAiConfig, saveAiConfig, testAiConfig, type AiConfigView, type AiTestResult } from '@/api/ai'
import {
  importDocuments,
  scanDocuments,
  type ImportSummary,
  type ScanSummary,
} from '@/api/documents'
import {
  createJob,
  fetchEstimate,
  resetLibrary,
  subscribeJob,
  type Estimate,
  type JobView,
} from '@/api/generate'
import { backgroundUrl } from '@/api/settings'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { MeterBar } from '@/components/MeterBar'
import { PageHead } from '@/components/PageHead'
import { StatusDot } from '@/components/StatusDot'
import { TerminalCard } from '@/components/TerminalCard'
import { useSettings } from '@/hooks/useSettings'

interface HealthInfo {
  application: string
  version: string
  dataDir: string
  javaVersion: string
}

const PRESETS = {
  极暗: { brightness: '0.26', veil: '0.84', blur: '0' },
  标准: { brightness: '0.48', veil: '0.62', blur: '0' },
  微亮: { brightness: '0.60', veil: '0.48', blur: '0' },
} as const

type PresetName = keyof typeof PRESETS

export function SettingsPage() {
  const { settings, update, upload, remove, error } = useSettings()
  const [uploading, setUploading] = useState(false)
  const [message, setMessage] = useState('')
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [health, setHealth] = useState<HealthInfo | null>(null)
  const [ai, setAi] = useState<AiConfigView | null>(null)
  const [aiTesting, setAiTesting] = useState(false)
  const [aiSaving, setAiSaving] = useState(false)
  const [aiMessage, setAiMessage] = useState('')
  const [aiResult, setAiResult] = useState<AiTestResult | null>(null)
  const [docDir, setDocDir] = useState('C:\\Java\\obs-note\\Obsidian Vault\\java面试')
  const [scan, setScan] = useState<ScanSummary | null>(null)
  const [scanning, setScanning] = useState(false)
  const [importing, setImporting] = useState(false)
  const [importResult, setImportResult] = useState<ImportSummary | null>(null)
  const [docMessage, setDocMessage] = useState('')
  const [estimate, setEstimate] = useState<Estimate | null>(null)
  const [job, setJob] = useState<JobView | null>(null)
  const [generating, setGenerating] = useState(false)
  const [genMessage, setGenMessage] = useState('')
  const [confirmCost, setConfirmCost] = useState(false)
  const [confirmReset, setConfirmReset] = useState(false)
  const streamRef = useRef<EventSource | null>(null)
  const [aiForm, setAiForm] = useState({
    baseUrl: 'https://api.deepseek.com',
    model: 'deepseek-chat',
    apiKey: '',
    temperature: '0.4',
    maxTokens: '4096',
    inputMissPrice: '2.00',
    inputHitPrice: '0.20',
    cacheWritePrice: '2.00',
    outputPrice: '8.00',
  })
  const fileRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    fetch('/api/health')
      .then((res) => res.json())
      .then((body) => setHealth(body.data as HealthInfo))
      .catch(() => setHealth(null))
  }, [])

  useEffect(() => {
    fetchAiConfig()
      .then((config) => {
        setAi(config)
        setAiForm((prev) => ({
          ...prev,
          baseUrl: config.baseUrl,
          model: config.model,
          temperature: String(config.temperature),
          maxTokens: String(config.maxTokens),
        }))
      })
      .catch((e: unknown) => setAiMessage(e instanceof Error ? e.message : String(e)))
  }, [])

  const keyFromEnv = ai?.keySource === 'ENV'
  const keyConfigured = ai?.keySource === 'ENV' || ai?.keySource === 'DB'

  async function handleSaveAi() {
    setAiSaving(true)
    setAiMessage('')
    try {
      const saved = await saveAiConfig({
        baseUrl: aiForm.baseUrl,
        model: aiForm.model,
        apiKey: aiForm.apiKey || undefined,
        temperature: Number(aiForm.temperature),
        maxTokens: Number(aiForm.maxTokens),
        inputMissPrice: Number(aiForm.inputMissPrice),
        inputHitPrice: Number(aiForm.inputHitPrice),
        cacheWritePrice: Number(aiForm.cacheWritePrice),
        outputPrice: Number(aiForm.outputPrice),
      })
      setAi(saved)
      setAiForm((prev) => ({ ...prev, apiKey: '' }))
      setAiMessage('配置已保存')
    } catch (e) {
      setAiMessage(e instanceof Error ? e.message : String(e))
    } finally {
      setAiSaving(false)
    }
  }

  async function handleTestAi() {
    setAiTesting(true)
    setAiMessage('')
    setAiResult(null)
    try {
      const result = await testAiConfig()
      setAiResult(result)
    } catch (e) {
      setAiMessage(e instanceof Error ? e.message : String(e))
    } finally {
      setAiTesting(false)
    }
  }

  useEffect(() => {
    void refreshEstimate()
    return () => {
      streamRef.current?.close()
    }
    // 只跑一次
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function refreshEstimate() {
    try {
      setEstimate(await fetchEstimate())
    } catch (e) {
      setGenMessage(e instanceof Error ? e.message : String(e))
    }
  }

  async function handleScan() {
    setScanning(true)
    setDocMessage('')
    try {
      const result = await scanDocuments(docDir)
      setScan(result)
      setDocMessage(`扫描到 ${result.total} 个文件`)
    } catch (e) {
      setDocMessage(e instanceof Error ? e.message : String(e))
    } finally {
      setScanning(false)
    }
  }

  async function handleImport() {
    setImporting(true)
    setDocMessage('')
    setImportResult(null)
    try {
      const result = await importDocuments(docDir)
      setImportResult(result)
      setDocMessage(
        `导入完成：导入 ${result.imported}，跳过 ${result.skipped}，失败 ${result.failed}，切片 ${result.chunks}`,
      )
      void refreshEstimate()
    } catch (e) {
      setDocMessage(e instanceof Error ? e.message : String(e))
    } finally {
      setImporting(false)
    }
  }

  function openStream(jobId: string) {
    streamRef.current?.close()
    const source = subscribeJob(jobId)
    streamRef.current = source
    const update = (event: MessageEvent) => {
      try {
        setJob(JSON.parse(event.data) as JobView)
      } catch {
        // 忽略无法解析的帧
      }
    }
    source.addEventListener('progress', update as EventListener)
    source.addEventListener('done', (event) => {
      update(event as MessageEvent)
      setGenerating(false)
      source.close()
      void refreshEstimate()
    })
    source.addEventListener('error', () => {
      setGenerating(false)
      source.close()
      setGenMessage('生成任务中断；重启服务后会自动续跑')
    })
  }

  async function handleGenerate(confirm: boolean) {
    setGenerating(true)
    setGenMessage('')
    try {
      const result = await createJob(300, confirm)
      if (result.needConfirm && !result.jobId) {
        setEstimate(result.estimate)
        setConfirmCost(true)
        setGenerating(false)
        return
      }
      if (result.jobId) {
        openStream(result.jobId)
      }
    } catch (e) {
      setGenMessage(e instanceof Error ? e.message : String(e))
      setGenerating(false)
    }
  }

  async function handleReset() {
    try {
      const result = await resetLibrary(true)
      setGenMessage(
        `已重置：删除 ${result.questionsDeleted} 道题，ai_usage 保留 ${result.aiUsageKept} 行`,
      )
      setJob(null)
      void refreshEstimate()
    } catch (e) {
      setGenMessage(e instanceof Error ? e.message : String(e))
    }
  }

  const enabled = settings['ui.background.enabled'] === 'true'
  const version = settings['ui.background.version'] ?? '0'
  const brightness = Number(settings['ui.background.brightness'] ?? '0.48')
  const blur = Number(settings['ui.background.blur'] ?? '0')
  const veil = Number(settings['ui.background.veil'] ?? '0.62')
  const vignette = settings['ui.background.vignette'] !== 'false'

  async function handleFile(file: File | undefined) {
    if (!file) return
    if (!file.type.startsWith('image/')) {
      setMessage('只支持图片文件（jpg / png / webp）')
      return
    }
    setUploading(true)
    setMessage('')
    try {
      await upload(file)
      setMessage('背景图已更新，刷新页面后依然生效')
    } catch (e) {
      setMessage(e instanceof Error ? e.message : String(e))
    } finally {
      setUploading(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  return (
    <div className="stack">
      <PageHead
        tag="settings"
        title="设置"
        desc="外观与背景图。所有配置写入本地数据库，不联网。"
        actions={<StatusDot tone={error ? 'error' : 'ok'} label={error ? 'SYNC FAILED' : 'SAVED LOCALLY'} />}
      />

      <div className="settings-grid">
        <div className="stack">
          <TerminalCard title="背景图" subtitle="APPEARANCE" corners>
            <input
              ref={fileRef}
              type="file"
              accept="image/*"
              className="sr-only"
              onChange={(event) => void handleFile(event.target.files?.[0])}
            />

            <button
              type="button"
              className="dropzone"
              onClick={() => fileRef.current?.click()}
              disabled={uploading}
            >
              <span className="dropzone-mark">[ + ]</span>
              <span className="dropzone-text">
                {uploading ? '正在压缩并保存…' : '点击选择图片，或把图片拖进来'}
              </span>
              <span className="dropzone-hint">jpg / png / webp · 长边超过 2560px 自动压缩</span>
            </button>

            {message ? (
              <p className="settings-row-hint" style={{ marginTop: 10, color: 'var(--accent-2)' }}>
                {message}
              </p>
            ) : null}

            <div className="divider" />

            <div className="settings-row">
              <div>
                <p className="settings-row-label">启用背景图</p>
                <p className="settings-row-hint">
                  关闭后回退纯黑 + 噪点。背景只作氛围，正文始终在半透明面板上。
                </p>
              </div>
              <button
                type="button"
                className={`switch${enabled ? ' on' : ''}`}
                role="switch"
                aria-checked={enabled}
                aria-label="启用背景图"
                onClick={() => void update({ 'ui.background.enabled': enabled ? 'false' : 'true' })}
              />
            </div>

            <div className="settings-row">
              <div>
                <p className="settings-row-label">暗角</p>
                <p className="settings-row-hint">四周压暗、中心保留，让视线集中在内容上。</p>
              </div>
              <button
                type="button"
                className={`switch${vignette ? ' on' : ''}`}
                role="switch"
                aria-checked={vignette}
                aria-label="暗角"
                onClick={() =>
                  void update({ 'ui.background.vignette': vignette ? 'false' : 'true' })
                }
              />
            </div>
          </TerminalCard>

          <TerminalCard title="背景参数" subtitle="TUNING">
            <div className="settings-controls">
              <div className="field">
                <label className="field-label" htmlFor="preset">
                  预设
                  <span className="segmented" style={{ marginLeft: 'auto' }}>
                    {(Object.keys(PRESETS) as PresetName[]).map((name) => (
                      <button
                        key={name}
                        type="button"
                        onClick={() =>
                          void update({
                            'ui.background.brightness': PRESETS[name].brightness,
                            'ui.background.veil': PRESETS[name].veil,
                            'ui.background.blur': PRESETS[name].blur,
                          })
                        }
                      >
                        {name}
                      </button>
                    ))}
                  </span>
                </label>
              </div>

              <div className="field">
                <label className="field-label" htmlFor="brightness">
                  图片亮度
                  <span className="value">{brightness.toFixed(2)}</span>
                </label>
                <input
                  id="brightness"
                  className="slider"
                  type="range"
                  min="0.1"
                  max="0.7"
                  step="0.01"
                  value={brightness}
                  onChange={(event) =>
                    void update({ 'ui.background.brightness': event.target.value })
                  }
                />
              </div>

              <div className="field">
                <label className="field-label" htmlFor="veil">
                  压暗层
                  <span className="value">{veil.toFixed(2)}</span>
                </label>
                <input
                  id="veil"
                  className="slider"
                  type="range"
                  min="0.4"
                  max="0.92"
                  step="0.01"
                  value={veil}
                  onChange={(event) => void update({ 'ui.background.veil': event.target.value })}
                />
              </div>

              <div className="field">
                <label className="field-label" htmlFor="blur">
                  模糊
                  <span className="value">{blur}px</span>
                </label>
                <input
                  id="blur"
                  className="slider"
                  type="range"
                  min="0"
                  max="12"
                  step="1"
                  value={blur}
                  onChange={(event) => void update({ 'ui.background.blur': event.target.value })}
                />
              </div>

              <div className="row">
                <button
                  type="button"
                  className="btn danger"
                  onClick={() => setConfirmOpen(true)}
                  disabled={!enabled && version === '0'}
                >
                  移除背景图
                </button>
                <span className="settings-row-hint">删除文件并清除版本号</span>
              </div>
            </div>
          </TerminalCard>

          <TerminalCard title="文档导入" subtitle="INGEST" corners>
            <div className="settings-controls">
              <div className="field">
                <label className="field-label" htmlFor="doc-dir">
                  笔记目录
                </label>
                <input
                  id="doc-dir"
                  className="input"
                  value={docDir}
                  onChange={(event) => setDocDir(event.target.value)}
                />
                <p className="settings-row-hint">
                  递归扫描 md / docx / pdf；只读原文件，切片只写入 data/interviewforge.db。
                </p>
              </div>

              <div className="row" style={{ flexWrap: 'wrap' }}>
                <button type="button" className="btn" onClick={() => void handleScan()} disabled={scanning}>
                  {scanning ? '扫描中…' : '扫描目录'}
                </button>
                <button
                  type="button"
                  className="btn primary"
                  onClick={() => void handleImport()}
                  disabled={importing}
                >
                  {importing ? '导入中…' : '导入并切片'}
                </button>
                {scan ? <span className="tag">{scan.total} 个文件</span> : null}
              </div>

              {scan && scan.items.length > 0 ? (
                <div className="table-wrap">
                  <table className="table">
                    <thead>
                      <tr>
                        <th>文件</th>
                        <th>类型</th>
                        <th className="num">大小</th>
                      </tr>
                    </thead>
                    <tbody>
                      {scan.items.slice(0, 5).map((item) => (
                        <tr key={item.path}>
                          <td>{item.filename}</td>
                          <td>{item.ext}</td>
                          <td className="num">{Math.round(item.size / 1024)} KB</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : null}

              {importResult ? (
                <p className="settings-row-hint" style={{ color: 'var(--ok)' }}>
                  扫描 {importResult.scanned} · 导入 {importResult.imported} · 跳过 {importResult.skipped} ·
                  失败 {importResult.failed} · 切片 {importResult.chunks} · 用时 {importResult.elapsedMs}ms
                </p>
              ) : null}

              {docMessage ? <p className="settings-row-hint">{docMessage}</p> : null}
            </div>
          </TerminalCard>

          <TerminalCard
            title="生成题库"
            subtitle="GENERATE"
            corners
            actions={<span className="tag">{job ? job.status : 'IDLE'}</span>}
          >
            <div className="settings-controls">
              {estimate ? (
                <p className="settings-row-hint">
                  本轮可处理切片 {estimate.chunkCount} 个 · 预计调用 {estimate.estimatedCalls} 次 · 预计花费
                  ¥{estimate.estimatedCost.toFixed(4)}（阈值 ¥{estimate.threshold}）
                </p>
              ) : null}

              <div className="row" style={{ flexWrap: 'wrap' }}>
                <button
                  type="button"
                  className="btn primary"
                  onClick={() => void handleGenerate(false)}
                  disabled={generating}
                >
                  {generating ? '生成中…' : '生成题库'}
                </button>
                <button
                  type="button"
                  className="btn danger"
                  onClick={() => setConfirmReset(true)}
                  disabled={generating}
                >
                  重置题库
                </button>
                <button type="button" className="btn ghost" onClick={() => void refreshEstimate()}>
                  刷新预估
                </button>
              </div>

              {job ? (
                <>
                  <MeterBar
                    label={job.stage ?? '任务'}
                    value={job.progress}
                    tone={job.status === 'FAILED' ? 'danger' : 'accent'}
                  />
                  <p className="settings-row-hint">
                    分类 {job.doneCategories}/{job.totalCategories} · 已生成 {job.questionCount} 题 ·{' '}
                    {job.message}
                  </p>
                </>
              ) : null}

              {genMessage ? (
                <p className="settings-row-hint" style={{ color: 'var(--accent-2)' }}>
                  {genMessage}
                </p>
              ) : null}
            </div>
          </TerminalCard>

          <TerminalCard title="AI 配置" subtitle="CONNECTION" corners>
            <div className="settings-controls">
              <div className="field">
                <label className="field-label" htmlFor="ai-base">
                  Base URL
                </label>
                <input
                  id="ai-base"
                  className="input"
                  value={aiForm.baseUrl}
                  onChange={(event) => setAiForm({ ...aiForm, baseUrl: event.target.value })}
                />
              </div>

              <div className="field">
                <label className="field-label" htmlFor="ai-model">
                  模型
                </label>
                <input
                  id="ai-model"
                  className="input"
                  value={aiForm.model}
                  onChange={(event) => setAiForm({ ...aiForm, model: event.target.value })}
                />
              </div>

              <div className="field">
                <label className="field-label" htmlFor="ai-key">
                  API Key
                  <span className="value">
                    {keyFromEnv ? '环境变量' : ai?.keyMasked || '未配置'}
                  </span>
                </label>
                <input
                  id="ai-key"
                  className="input"
                  type="password"
                  autoComplete="off"
                  disabled={keyFromEnv}
                  placeholder={keyFromEnv ? '由环境变量提供' : '留空表示不修改'}
                  value={aiForm.apiKey}
                  onChange={(event) => setAiForm({ ...aiForm, apiKey: event.target.value })}
                />
                <p className="settings-row-hint">
                  {keyFromEnv
                    ? '检测到环境变量 INTERVIEWFORGE_AI_API_KEY，优先使用它，此处不可编辑。'
                    : 'Key 加密后存入本地库，接口只回显后 4 位。'}
                </p>
              </div>

              <div className="grid grid-2">
                <div className="field">
                  <label className="field-label" htmlFor="ai-temp">
                    温度
                  </label>
                  <input
                    id="ai-temp"
                    className="input"
                    inputMode="decimal"
                    value={aiForm.temperature}
                    onChange={(event) => setAiForm({ ...aiForm, temperature: event.target.value })}
                  />
                </div>
                <div className="field">
                  <label className="field-label" htmlFor="ai-maxtok">
                    max_tokens
                  </label>
                  <input
                    id="ai-maxtok"
                    className="input"
                    inputMode="numeric"
                    value={aiForm.maxTokens}
                    onChange={(event) => setAiForm({ ...aiForm, maxTokens: event.target.value })}
                  />
                </div>
              </div>

              <div className="divider" />

              <p className="param-label">定价（元 / 百万 token）</p>
              <div className="grid grid-2">
                <div className="field">
                  <label className="field-label" htmlFor="price-miss">
                    输入·未命中
                  </label>
                  <input
                    id="price-miss"
                    className="input"
                    inputMode="decimal"
                    value={aiForm.inputMissPrice}
                    onChange={(event) => setAiForm({ ...aiForm, inputMissPrice: event.target.value })}
                  />
                </div>
                <div className="field">
                  <label className="field-label" htmlFor="price-hit">
                    输入·命中
                  </label>
                  <input
                    id="price-hit"
                    className="input"
                    inputMode="decimal"
                    value={aiForm.inputHitPrice}
                    onChange={(event) => setAiForm({ ...aiForm, inputHitPrice: event.target.value })}
                  />
                </div>
                <div className="field">
                  <label className="field-label" htmlFor="price-write">
                    缓存写入
                  </label>
                  <input
                    id="price-write"
                    className="input"
                    inputMode="decimal"
                    value={aiForm.cacheWritePrice}
                    onChange={(event) =>
                      setAiForm({ ...aiForm, cacheWritePrice: event.target.value })
                    }
                  />
                </div>
                <div className="field">
                  <label className="field-label" htmlFor="price-out">
                    输出
                  </label>
                  <input
                    id="price-out"
                    className="input"
                    inputMode="decimal"
                    value={aiForm.outputPrice}
                    onChange={(event) => setAiForm({ ...aiForm, outputPrice: event.target.value })}
                  />
                </div>
              </div>

              <div className="row" style={{ flexWrap: 'wrap' }}>
                <button
                  type="button"
                  className="btn primary"
                  onClick={() => void handleSaveAi()}
                  disabled={aiSaving}
                >
                  {aiSaving ? '保存中…' : '保存配置'}
                </button>
                <button
                  type="button"
                  className="btn"
                  onClick={() => void handleTestAi()}
                  disabled={aiTesting || !keyConfigured}
                >
                  {aiTesting ? '测试中…' : '测试连接'}
                </button>
                <StatusDot
                  tone={keyFromEnv ? 'ok' : keyConfigured ? 'ok' : 'warn'}
                  label={keyFromEnv ? 'ENV' : keyConfigured ? 'DB' : 'NOT SET'}
                />
              </div>

              {aiResult ? (
                <p className="settings-row-hint" style={{ color: 'var(--ok)' }}>
                  连接成功 · {aiResult.model} · 延迟 {aiResult.latencyMs}ms · tokens{' '}
                  {aiResult.usage.totalTokens} · 本次花费 ¥{aiResult.estCost.toFixed(6)}
                </p>
              ) : null}

              {aiMessage ? (
                <p className="settings-row-hint" style={{ color: 'var(--accent-2)' }}>
                  {aiMessage}
                </p>
              ) : null}
            </div>
          </TerminalCard>

          <TerminalCard title="系统信息" subtitle="RUNTIME">
            <dl className="kv" style={{ fontSize: 12 }}>
              <dt>应用</dt>
              <dd>{health?.application ?? '—'}</dd>
              <dt>版本</dt>
              <dd>{health?.version ?? '—'}</dd>
              <dt>Java</dt>
              <dd>{health?.javaVersion ?? '—'}</dd>
              <dt>数据目录</dt>
              <dd className="text-accent" style={{ wordBreak: 'break-all' }}>
                {health?.dataDir ?? '—'}
              </dd>
            </dl>
          </TerminalCard>
        </div>

        <div className="stack">
          <TerminalCard title="实时预览" subtitle="PREVIEW" corners>
            <div className="preview">
              {enabled && version !== '0' ? (
                <div
                  className="preview-thumb"
                  style={{ backgroundImage: `url("${backgroundUrl(version)}")` }}
                />
              ) : (
                <div className="preview-empty">未启用背景图</div>
              )}
              <div className="preview-veil" />
              {vignette ? <div className="preview-vignette" /> : null}
              <span className="preview-label">SAME PARAMETERS AS PAGE</span>
            </div>
            <p className="settings-row-hint" style={{ marginTop: 12 }}>
              预览与页面使用同一组 CSS 变量，所见即所得。图片本身按亮度、压暗、暗角三层叠加。
            </p>
          </TerminalCard>

        </div>
      </div>

      <ConfirmDialog
        open={confirmCost}
        title="确认生成题库？"
        description={`预估花费 ¥${estimate?.estimatedCost.toFixed(4) ?? '0'}，已超过 ¥${estimate?.threshold ?? 5} 阈值。确认后开始调用 AI。`}
        confirmText="继续生成"
        onCancel={() => setConfirmCost(false)}
        onConfirm={() => {
          setConfirmCost(false)
          void handleGenerate(true)
        }}
      />

      <ConfirmDialog
        open={confirmReset}
        title="重置题库？"
        description="将清空所有题目，以及掌握度、错题、练习与考试记录；ai_usage 历史保留。分类与切片保留。"
        confirmText="重置"
        danger
        onCancel={() => setConfirmReset(false)}
        onConfirm={() => {
          setConfirmReset(false)
          void handleReset()
        }}
      />

      <ConfirmDialog
        open={confirmOpen}
        title="移除背景图？"
        description="会删除 data/assets/background.jpg 并清除当前背景。此操作不可撤销，但可以重新上传。"
        confirmText="移除"
        danger
        onCancel={() => setConfirmOpen(false)}
        onConfirm={() => {
          setConfirmOpen(false)
          void remove()
            .then(() => setMessage('背景图已移除'))
            .catch((e: unknown) => setMessage(e instanceof Error ? e.message : String(e)))
        }}
      />
    </div>
  )
}
