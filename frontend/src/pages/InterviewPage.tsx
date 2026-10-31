import { useCallback, useEffect, useRef, useState } from 'react'

import {
  createExam,
  saveAnswer,
  submitExam,
  type ExamQuestion,
  type ExamView,
  type Report,
} from '@/api/exams'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { MeterBar } from '@/components/MeterBar'
import { PageHead } from '@/components/PageHead'
import { TerminalCard } from '@/components/TerminalCard'

type Phase = 'setup' | 'exam' | 'report'

const TYPE_LABEL: Record<string, string> = { SA: '简答', MC: '选择', TF: '判断' }
const BLUEPRINT = [
  { key: '总题量', value: '15' },
  { key: '简答', value: '10 × 8 分' },
  { key: '选择', value: '3 × 4 分' },
  { key: '判断', value: '2 × 4 分' },
]

export function InterviewPage() {
  const [phase, setPhase] = useState<Phase>('setup')
  const [exam, setExam] = useState<ExamView | null>(null)
  const [index, setIndex] = useState(0)
  const [answers, setAnswers] = useState<Record<number, string>>({})
  const [remaining, setRemaining] = useState(0)
  const [report, setReport] = useState<Report | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [confirmSubmit, setConfirmSubmit] = useState(false)
  const submittingRef = useRef(false)

  const current: ExamQuestion | null = exam?.items[index] ?? null

  const doSubmit = useCallback(async () => {
    if (!exam || submittingRef.current) return
    submittingRef.current = true
    setBusy(true)
    setError('')
    try {
      const result = await submitExam(exam.examId)
      setReport(result)
      setPhase('report')
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
      submittingRef.current = false
    }
  }, [exam])

  // 倒计时
  useEffect(() => {
    if (phase !== 'exam' || !exam) return
    const deadline = new Date(exam.startedAt).getTime() + exam.durationMinutes * 60_000
    const tick = () => {
      const left = Math.max(0, Math.floor((deadline - Date.now()) / 1000))
      setRemaining(left)
      if (left === 0) {
        void doSubmit()
      }
    }
    tick()
    const timer = window.setInterval(tick, 1000)
    return () => window.clearInterval(timer)
  }, [phase, exam, doSubmit])

  async function handleStart() {
    setBusy(true)
    setError('')
    try {
      const created = await createExam()
      setExam(created)
      setAnswers({})
      setIndex(0)
      setReport(null)
      setPhase('exam')
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  function updateAnswer(questionId: number, value: string) {
    setAnswers((prev) => ({ ...prev, [questionId]: value }))
    if (exam) {
      void saveAnswer(exam.examId, questionId, value).catch(() => {
        // 自动保存失败不打断作答，交卷时以最终值重传
      })
    }
  }

  function reset() {
    setPhase('setup')
    setExam(null)
    setReport(null)
    setAnswers({})
    setIndex(0)
  }

  const answeredCount = exam ? exam.items.filter((item) => (answers[item.questionId] ?? '').trim() !== '').length : 0
  const minutes = Math.floor(remaining / 60)
  const seconds = remaining % 60

  return (
    <div className="stack">
      <PageHead
        tag="interview"
        title="模拟面试"
        desc="从题库自动组卷；客观题本地判分，简答题由 AI 对照标准答案阅卷。"
      />

      {error ? <p className="notice">{error}</p> : null}

      {phase === 'setup' ? (
        <div className="grid grid-main-side">
          <TerminalCard title="组卷蓝图" subtitle="BLUEPRINT" corners>
            <div className="blueprint">
              {BLUEPRINT.map((item) => (
                <div key={item.key} className="blueprint-item">
                  <p className="blueprint-k">{item.key}</p>
                  <p className="blueprint-v" style={{ fontSize: 18 }}>{item.value}</p>
                </div>
              ))}
            </div>

            <div className="divider" />

            <div className="grid grid-2">
              <div className="param-block">
                <span className="param-label">考试时长</span>
                <p style={{ fontSize: 24, fontWeight: 700 }}>45 分钟</p>
              </div>
              <div className="param-block">
                <span className="param-label">判题方式</span>
                <p className="settings-row-hint" style={{ marginTop: 4 }}>
                  客观题本地比对；简答题把「你的答案 + 标准答案」交给 AI 判分。
                </p>
              </div>
            </div>

            <div className="notice" style={{ marginTop: 20 }}>
              <strong>交卷前不会下发答案。</strong>
              <br />
              考试中可随时修改答案并自动保存；计时结束会自动交卷。
            </div>

            <div className="row" style={{ marginTop: 18 }}>
              <button type="button" className="btn primary" disabled={busy} onClick={() => void handleStart()}>
                {busy ? '组卷中…' : '开始考试'}
              </button>
            </div>
          </TerminalCard>

          <TerminalCard title="说明" subtitle="NOTE">
            <div className="stack" style={{ gap: 12 }}>
              <div className="notice">
                <strong>判分只对照题库标准答案。</strong>
                <br />
                同一份答卷重复交卷会命中缓存，不会重复计费。
              </div>
              <div className="blueprint">
                <div className="blueprint-item">
                  <p className="blueprint-k">满分</p>
                  <p className="blueprint-v">100</p>
                </div>
                <div className="blueprint-item">
                  <p className="blueprint-k">AI 调用</p>
                  <p className="blueprint-v">≤10</p>
                </div>
              </div>
            </div>
          </TerminalCard>
        </div>
      ) : null}

      {phase === 'exam' && exam && current ? (
        <div className="grid grid-main-side">
          <TerminalCard
            title={`第 ${index + 1} / ${exam.items.length} 题`}
            subtitle={TYPE_LABEL[current.type] ?? current.type}
            corners
            actions={<span className="tag">剩余 {minutes}:{String(seconds).padStart(2, '0')}</span>}
          >
            <div className="practice-card">
              <p className="practice-stem">{current.stem}</p>

              {current.type === 'MC' && current.optionsJson
                ? (JSON.parse(current.optionsJson) as string[]).map((option, optionIndex) => {
                    const letter = String.fromCharCode(65 + optionIndex)
                    const selected = (answers[current.questionId] ?? '').toUpperCase() === letter
                    return (
                      <button
                        key={optionIndex}
                        type="button"
                        className="option"
                        style={selected ? { borderColor: 'var(--accent)', background: 'rgba(182,242,74,.08)' } : undefined}
                        onClick={() => updateAnswer(current.questionId, letter)}
                      >
                        {option}
                      </button>
                    )
                  })
                : null}

              {current.type === 'TF' ? (
                <div className="row" style={{ gap: 8 }}>
                  {[
                    { label: '正确 TRUE', value: 'TRUE' },
                    { label: '错误 FALSE', value: 'FALSE' },
                  ].map((option) => {
                    const selected = (answers[current.questionId] ?? '').toUpperCase() === option.value
                    return (
                      <button
                        key={option.value}
                        type="button"
                        className="option"
                        style={selected ? { borderColor: 'var(--accent)', background: 'rgba(182,242,74,.08)' } : undefined}
                        onClick={() => updateAnswer(current.questionId, option.value)}
                      >
                        {option.label}
                      </button>
                    )
                  })}
                </div>
              ) : null}

              {current.type === 'SA' ? (
                <textarea
                  className="input"
                  rows={6}
                  placeholder="写下你的回答…"
                  value={answers[current.questionId] ?? ''}
                  onChange={(event) => updateAnswer(current.questionId, event.target.value)}
                />
              ) : null}

              <div className="divider" />

              <div className="row" style={{ gap: 8 }}>
                <button
                  type="button"
                  className="btn"
                  disabled={index === 0}
                  onClick={() => setIndex((value) => Math.max(0, value - 1))}
                >
                  上一题
                </button>
                <button
                  type="button"
                  className="btn"
                  disabled={index >= exam.items.length - 1}
                  onClick={() => setIndex((value) => Math.min(exam.items.length - 1, value + 1))}
                >
                  下一题
                </button>
                <button
                  type="button"
                  className="btn primary"
                  style={{ marginLeft: 'auto' }}
                  disabled={busy}
                  onClick={() => setConfirmSubmit(true)}
                >
                  {busy ? '判分中…' : '交卷'}
                </button>
              </div>
            </div>
          </TerminalCard>

          <TerminalCard title="答题卡" subtitle="SHEET">
            <div className="stack" style={{ gap: 12 }}>
              <MeterBar label="已作答" value={(answeredCount / exam.items.length) * 100} />
              <div className="chip-row">
                {exam.items.map((item, itemIndex) => {
                  const answered = (answers[item.questionId] ?? '').trim() !== ''
                  return (
                    <button
                      key={item.questionId}
                      type="button"
                      className={`chip${itemIndex === index ? ' active' : ''}`}
                      style={answered && itemIndex !== index ? { borderColor: 'rgba(58,222,155,.45)' } : undefined}
                      onClick={() => setIndex(itemIndex)}
                    >
                      {item.orderNo}
                    </button>
                  )
                })}
              </div>
              <p className="settings-row-hint">
                已答 {answeredCount} / {exam.items.length} 题。离开页面不会丢失作答。
              </p>
            </div>
          </TerminalCard>
        </div>
      ) : null}

      {phase === 'report' && report ? (
        <div className="stack">
          <TerminalCard title="成绩报告" subtitle="REPORT" corners>
            <div className="blueprint">
              <div className="blueprint-item">
                <p className="blueprint-k">总分</p>
                <p className="blueprint-v">{report.totalScore.toFixed(1)}</p>
              </div>
              <div className="blueprint-item">
                <p className="blueprint-k">满分</p>
                <p className="blueprint-v">{report.maxScore}</p>
              </div>
              <div className="blueprint-item">
                <p className="blueprint-k">得分率</p>
                <p className="blueprint-v">
                  {report.maxScore > 0 ? Math.round((report.totalScore / report.maxScore) * 100) : 0}%
                </p>
              </div>
              <div className="blueprint-item">
                <p className="blueprint-k">用时</p>
                <p className="blueprint-v">{Math.max(1, Math.round(report.durationSec / 60))} 分</p>
              </div>
            </div>

            <div className="row" style={{ marginTop: 18 }}>
              <button type="button" className="btn primary" onClick={reset}>
                再来一场
              </button>
            </div>
          </TerminalCard>

          {report.items.map((item) => (
            <TerminalCard
              key={item.orderNo}
              title={`第 ${item.orderNo} 题 · ${TYPE_LABEL[item.type] ?? item.type}`}
              subtitle={`${item.score.toFixed(1)} / ${item.maxScore}`}
              corners
            >
              <div className="stack" style={{ gap: 12 }}>
                <p className="practice-stem">{item.stem}</p>

                <div className="answer-box">
                  <strong>你的答案：</strong>
                  {item.userAnswer || '（未作答）'}
                  {'\n\n'}
                  <strong>标准答案：</strong>
                  {item.standardAnswer}
                  {'\n\n'}
                  <strong>解析：</strong>
                  {item.explanation}
                </div>

                <div className="grid grid-2">
                  <div>
                    <p className="param-label">命中要点</p>
                    <ul style={{ margin: '6px 0 0 18px', fontSize: 13, lineHeight: 1.8 }}>
                      {item.hitPoints.map((point, pointIndex) => (
                        <li key={pointIndex}>{point}</li>
                      ))}
                    </ul>
                  </div>
                  <div>
                    <p className="param-label">遗漏要点</p>
                    <ul style={{ margin: '6px 0 0 18px', fontSize: 13, lineHeight: 1.8 }}>
                      {item.missedPoints.map((point, pointIndex) => (
                        <li key={pointIndex}>{point}</li>
                      ))}
                    </ul>
                  </div>
                </div>

                <p className="settings-row-hint">建议：{item.suggestion}</p>
              </div>
            </TerminalCard>
          ))}
        </div>
      ) : null}

      <ConfirmDialog
        open={confirmSubmit}
        title="确认交卷？"
        description={`已作答 ${answeredCount} / ${exam?.items.length ?? 0} 题。交卷后简答题会调用 AI 判分，耗时约 10–30 秒。`}
        confirmText="交卷并判分"
        onCancel={() => setConfirmSubmit(false)}
        onConfirm={() => {
          setConfirmSubmit(false)
          void doSubmit()
        }}
      />
    </div>
  )
}
