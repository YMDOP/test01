import { useState } from 'react'

import {
  finishPractice,
  rateQuestion,
  startPractice,
  type PracticeSummary,
  type PracticeView,
} from '@/api/practice'
import { MeterBar } from '@/components/MeterBar'
import { PageHead } from '@/components/PageHead'
import { TerminalCard } from '@/components/TerminalCard'

type Phase = 'setup' | 'running' | 'summary'
type Rating = 'KNOWN' | 'UNKNOWN' | 'VAGUE'

const TYPE_OPTIONS = [
  { label: '全部题型', value: '' },
  { label: '简答', value: 'SA' },
  { label: '选择', value: 'MC' },
  { label: '判断', value: 'TF' },
]

const FILTER_OPTIONS = [
  { label: '随机', mastery: undefined as string[] | undefined },
  { label: '只抽答错', mastery: ['WRONG'] },
  { label: '只抽模糊', mastery: ['VAGUE'] },
  { label: '只抽未做', mastery: ['UNTRIED'] },
]

const TYPE_LABEL: Record<string, string> = { SA: '简答', MC: '选择', TF: '判断' }

export function PracticePage() {
  const [phase, setPhase] = useState<Phase>('setup')
  const [type, setType] = useState('')
  const [filterIndex, setFilterIndex] = useState(0)
  const [count, setCount] = useState('10')
  const [includeAnswer, setIncludeAnswer] = useState(false)
  const [excludeRecentDays, setExcludeRecentDays] = useState('0')

  const [session, setSession] = useState<PracticeView | null>(null)
  const [index, setIndex] = useState(0)
  const [showAnswerNow, setShowAnswerNow] = useState(false)
  const [summary, setSummary] = useState<PracticeSummary | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const current = session?.items[index] ?? null
  const progress = session && session.total > 0 ? ((index) / session.total) * 100 : 0

  async function handleStart() {
    setBusy(true)
    setError('')
    try {
      const result = await startPractice({
        types: type ? [type] : undefined,
        mastery: FILTER_OPTIONS[filterIndex]?.mastery,
        count: Number(count) || 10,
        order: 'RANDOM',
        excludeRecentDays: Number(excludeRecentDays) || 0,
        includeAnswer: true,
      })
      setSession(result)
      setIndex(0)
      setShowAnswerNow(false)
      setSummary(null)
      setPhase('running')
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  async function handleRate(rating: Rating) {
    if (!session || !current) return
    setBusy(true)
    setError('')
    try {
      await rateQuestion(session.sessionId, current.id, rating)
      const next = index + 1
      if (next >= session.total) {
        const result = await finishPractice(session.sessionId)
        setSummary(result)
        setPhase('summary')
      } else {
        setIndex(next)
        setShowAnswerNow(false)
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  function reset() {
    setPhase('setup')
    setSession(null)
    setSummary(null)
    setIndex(0)
    setShowAnswerNow(false)
  }

  return (
    <div className="stack">
      <PageHead
        tag="practice"
        title="练习模式"
        desc="从本地题库抽题，自评掌握度；全程离线，不消耗 token。"
      />

      {error ? <p className="notice">{error}</p> : null}

      {phase === 'setup' ? (
        <div className="grid grid-main-side">
          <TerminalCard title="抽题参数" subtitle="PARAMETERS" corners>
            <div className="param-grid">
              <div className="param-block">
                <span className="param-label">题型</span>
                <div className="chip-row">
                  {TYPE_OPTIONS.map((option) => (
                    <button
                      key={option.value}
                      type="button"
                      className={`chip${type === option.value ? ' active' : ''}`}
                      onClick={() => setType(option.value)}
                    >
                      {option.label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="param-block">
                <span className="param-label">过滤</span>
                <div className="chip-row">
                  {FILTER_OPTIONS.map((option, optionIndex) => (
                    <button
                      key={option.label}
                      type="button"
                      className={`chip${filterIndex === optionIndex ? ' active' : ''}`}
                      onClick={() => setFilterIndex(optionIndex)}
                    >
                      {option.label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="param-block">
                <span className="param-label">题量</span>
                <input
                  className="input"
                  value={count}
                  inputMode="numeric"
                  onChange={(event) => setCount(event.target.value)}
                  aria-label="题量"
                />
              </div>

              <div className="param-block">
                <span className="param-label">排除最近做过的（天）</span>
                <input
                  className="input"
                  value={excludeRecentDays}
                  inputMode="numeric"
                  onChange={(event) => setExcludeRecentDays(event.target.value)}
                  aria-label="排除最近天数"
                />
              </div>
            </div>

            <div className="divider" />

            <div className="settings-row">
              <div>
                <p className="settings-row-label">显示答案与解析</p>
                <p className="settings-row-hint">
                  开启即开卷背题模式；关闭则先自评再翻看答案。
                </p>
              </div>
              <button
                type="button"
                className={`switch${includeAnswer ? ' on' : ''}`}
                role="switch"
                aria-checked={includeAnswer}
                aria-label="显示答案与解析"
                onClick={() => setIncludeAnswer((value) => !value)}
              />
            </div>

            <div className="row" style={{ marginTop: 18 }}>
              <button type="button" className="btn primary" disabled={busy} onClick={() => void handleStart()}>
                {busy ? '抽题中…' : '开始练习'}
              </button>
              <span className="settings-row-hint">全程本地，不调用 AI</span>
            </div>
          </TerminalCard>

          <TerminalCard title="说明" subtitle="NOTE">
            <div className="stack" style={{ gap: 12 }}>
              <div className="notice">
                <strong>自评即写回掌握度。</strong>
                <br />
                「不会」会自动进入错题本；「会」会把该题从错题本移出（保留历史）。
              </div>
              <div className="blueprint">
                <div className="blueprint-item">
                  <p className="blueprint-k">AI 调用</p>
                  <p className="blueprint-v">0</p>
                </div>
                <div className="blueprint-item">
                  <p className="blueprint-k">题量</p>
                  <p className="blueprint-v">{count || '—'}</p>
                </div>
              </div>
            </div>
          </TerminalCard>
        </div>
      ) : null}

      {phase === 'running' && session && current ? (
        <div className="grid grid-main-side">
          <TerminalCard
            title={`第 ${index + 1} / ${session.total} 题`}
            subtitle={TYPE_LABEL[current.type] ?? current.type}
            corners
            actions={
              <button type="button" className="btn ghost" onClick={reset}>
                退出
              </button>
            }
          >
            <div className="practice-card">
              <div className="row" style={{ justifyContent: 'space-between' }}>
                <span className="tag">{current.parentName} / {current.categoryName}</span>
                <span className="tag">{current.masteryStatus}</span>
              </div>

              <p className="practice-stem">{current.stem}</p>

              {current.optionsJson
                ? (JSON.parse(current.optionsJson) as string[]).map((option, optionIndex) => (
                    <div key={optionIndex} className="option">
                      {option}
                    </div>
                  ))
                : null}

              {includeAnswer && !showAnswerNow ? (
                <button
                  type="button"
                  className="btn"
                  onClick={() => setShowAnswerNow(true)}
                  style={{ alignSelf: 'flex-start' }}
                >
                  显示答案
                </button>
              ) : null}

              {includeAnswer && showAnswerNow ? (
                <div className="answer-box">
                  <strong>标准答案：</strong>
                  {current.standardAnswer}
                  {'\n\n'}
                  <strong>解析：</strong>
                  {current.explanation}
                </div>
              ) : null}

              <div className="divider" />

              <div className="row" style={{ gap: 8 }}>
                <button
                  type="button"
                  className="btn primary"
                  disabled={busy}
                  onClick={() => void handleRate('KNOWN')}
                >
                  记为会
                </button>
                <button
                  type="button"
                  className="btn danger"
                  disabled={busy}
                  onClick={() => void handleRate('UNKNOWN')}
                >
                  记为不会
                </button>
                <button
                  type="button"
                  className="btn"
                  disabled={busy}
                  onClick={() => void handleRate('VAGUE')}
                >
                  模糊
                </button>
              </div>
            </div>
          </TerminalCard>

          <TerminalCard title="进度" subtitle="PROGRESS">
            <div className="stack" style={{ gap: 14 }}>
              <MeterBar label="已完成" value={progress} />
              <p className="settings-row-hint">
                已答 {index} / {session.total} 题。退出后本次会话不会自动恢复，但掌握度已实时写回。
              </p>
            </div>
          </TerminalCard>
        </div>
      ) : null}

      {phase === 'summary' && summary ? (
        <TerminalCard title="本轮小结" subtitle="SUMMARY" corners>
          <div className="blueprint">
            <div className="blueprint-item">
              <p className="blueprint-k">总题数</p>
              <p className="blueprint-v">{summary.total}</p>
            </div>
            <div className="blueprint-item">
              <p className="blueprint-k">会</p>
              <p className="blueprint-v">{summary.known}</p>
            </div>
            <div className="blueprint-item">
              <p className="blueprint-k">不会</p>
              <p className="blueprint-v">{summary.unknown}</p>
            </div>
            <div className="blueprint-item">
              <p className="blueprint-k">模糊</p>
              <p className="blueprint-v">{summary.vague}</p>
            </div>
          </div>

          <p className="settings-row-hint" style={{ marginTop: 16 }}>
            用时 {Math.floor(summary.elapsedSeconds / 60)} 分 {summary.elapsedSeconds % 60} 秒 ·
            「不会」的题已进入错题本
          </p>

          <div className="row" style={{ marginTop: 18 }}>
            <button type="button" className="btn primary" onClick={reset}>
              再来一轮
            </button>
          </div>
        </TerminalCard>
      ) : null}
    </div>
  )
}
