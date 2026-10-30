import { useEffect, useRef, useState } from 'react'

import { fetchAiConfig, saveAiConfig, testAiConfig, type AiConfigView, type AiTestResult } from '@/api/ai'
import { backgroundUrl } from '@/api/settings'
import { ConfirmDialog } from '@/components/ConfirmDialog'
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
