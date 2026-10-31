import { useCallback, useEffect, useMemo, useState } from 'react'

import {
  fetchDailyUsage,
  fetchQbankSummary,
  fetchRecentUsage,
  fetchUsageSummary,
  type DailyPoint,
  type QbankSummary,
  type RecentUsage,
  type UsageRange,
  type UsageSummary,
} from '@/api/usage'
import { DataTable } from '@/components/DataTable'
import { MeterBar } from '@/components/MeterBar'
import { PageHead } from '@/components/PageHead'
import { Sparkline } from '@/components/Sparkline'
import { StatusDot } from '@/components/StatusDot'
import { TerminalCard } from '@/components/TerminalCard'

const RANGES: UsageRange[] = ['TODAY', '7D', '30D', 'ALL']
const RANGE_LABEL: Record<UsageRange, string> = {
  TODAY: '今日',
  '7D': '近 7 天',
  '30D': '近 30 天',
  ALL: '累计',
}
const PURPOSE_LABEL: Record<string, string> = {
  CLASSIFY: '分类归类',
  GENERATE: '出题',
  GRADE: '阅卷',
  CONNECTIVITY_TEST: '连通性测试',
}

export function DashboardPage() {
  const [range, setRange] = useState<UsageRange>('TODAY')
  const [summary, setSummary] = useState<UsageSummary | null>(null)
  const [today, setToday] = useState<UsageSummary | null>(null)
  const [allTime, setAllTime] = useState<UsageSummary | null>(null)
  const [recent, setRecent] = useState<RecentUsage[]>([])
  const [daily, setDaily] = useState<DailyPoint[]>([])
  const [qbank, setQbank] = useState<QbankSummary | null>(null)
  const [error, setError] = useState('')

  const load = useCallback(async (nextRange: UsageRange) => {
    try {
      const [selected, todayData, allData, recentData, dailyData, qbankData] = await Promise.all([
        fetchUsageSummary(nextRange),
        fetchUsageSummary('TODAY'),
        fetchUsageSummary('ALL'),
        fetchRecentUsage(10),
        fetchDailyUsage(14),
        fetchQbankSummary(),
      ])
      setSummary(selected)
      setToday(todayData)
      setAllTime(allData)
      setRecent(recentData)
      setDaily(dailyData)
      setQbank(qbankData)
      setError('')
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    void load(range)
  }, [range, load])

  const sparkPoints = useMemo(() => daily.map((point) => point.cost), [daily])
  const purposeEntries = useMemo(
    () => Object.entries(summary?.byPurpose ?? {}).sort((a, b) => b[1].cost - a[1].cost),
    [summary],
  )
  const maxPurposeCost = purposeEntries.reduce((max, entry) => Math.max(max, entry[1].cost), 0)

  return (
    <div className="stack">
      <PageHead
        tag="dashboard"
        title="仪表盘"
        desc="题库概况与 AI 用量，数字全部来自本地 ai_usage 表。"
        actions={<StatusDot tone="ok" label="LOCAL ONLY" />}
      />

      {error ? <p className="notice">{error}</p> : null}

      <div className="grid grid-4">
        <section className="card stat">
          <span className="stat-label">今日花费</span>
          <span className="stat-value">
            ¥{(today?.totalCost ?? 0).toFixed(4)}
          </span>
          <span className="stat-foot">今日 {today?.totalCalls ?? 0} 次调用</span>
        </section>

        <section className="card stat">
          <span className="stat-label">累计 token</span>
          <span className="stat-value">
            {(allTime?.totalTokens ?? 0).toLocaleString()}
          </span>
          <span className="stat-foot">累计 {(allTime?.totalCalls ?? 0).toLocaleString()} 次调用</span>
        </section>

        <section className="card stat">
          <span className="stat-label">缓存命中率</span>
          <span className="stat-value">{Math.round((summary?.cacheHitRate ?? 0) * 100)}%</span>
          <span className="stat-foot">{RANGE_LABEL[range]}口径</span>
        </section>

        <section className="card stat">
          <span className="stat-label">题库总量</span>
          <span className="stat-value">
            {qbank?.questions ?? 0}
            <span className="unit">题</span>
          </span>
          <span className="stat-foot">
            {qbank?.categories ?? 0} 个分类 · {qbank?.documents ?? 0} 份文档
          </span>
        </section>
      </div>

      <div className="grid grid-main-side">
        <TerminalCard
          title="AI 用量与花费"
          subtitle="TOKEN ACCOUNTING"
          corners
          actions={
            <div className="segmented">
              {RANGES.map((item) => (
                <button
                  key={item}
                  type="button"
                  className={range === item ? 'active' : ''}
                  onClick={() => setRange(item)}
                >
                  {RANGE_LABEL[item]}
                </button>
              ))}
            </div>
          }
        >
          <div className="row-between" style={{ alignItems: 'flex-end' }}>
            <div>
              <p className="mono-label">{RANGE_LABEL[range]}花费</p>
              <p className="stat-value" style={{ marginTop: 4 }}>
                ¥{(summary?.totalCost ?? 0).toFixed(4)}
              </p>
            </div>
            <div style={{ textAlign: 'right' }}>
              <p className="mono-label">tokens</p>
              <p className="stat-value" style={{ fontSize: 24, marginTop: 4 }}>
                {(summary?.totalTokens ?? 0).toLocaleString()}
              </p>
            </div>
          </div>

          <div className="divider" />

          <div className="grid grid-2">
            <MeterBar
              label="缓存命中率"
              value={(summary?.cacheHitRate ?? 0) * 100}
              tone="info"
            />
            <MeterBar
              label="输入 token 占比"
              value={
                summary && summary.inputTokens + summary.cacheReadTokens > 0
                  ? (summary.inputTokens / (summary.inputTokens + summary.cacheReadTokens)) * 100
                  : 0
              }
              tone="accent"
            />
          </div>

          <div className="divider" />

          <p className="mono-label" style={{ marginBottom: 10 }}>
            按用途拆分
          </p>
          {purposeEntries.length === 0 ? (
            <p className="settings-row-hint">该区间没有 AI 调用。</p>
          ) : (
            <div className="stack" style={{ gap: 12 }}>
              {purposeEntries.map(([purpose, stat]) => (
                <MeterBar
                  key={purpose}
                  label={`${PURPOSE_LABEL[purpose] ?? purpose} · ${stat.calls} 次 · ¥${stat.cost.toFixed(4)}`}
                  value={maxPurposeCost > 0 ? (stat.cost / maxPurposeCost) * 100 : 0}
                  tone="accent"
                />
              ))}
            </div>
          )}

          <div className="divider" />

          <p className="mono-label" style={{ marginBottom: 10 }}>
            近 14 天花费
          </p>
          <Sparkline points={sparkPoints} placeholder="近 14 天还没有调用记录" />
        </TerminalCard>

        <TerminalCard title="最近调用" subtitle="RECENT" tight>
          <DataTable
            columns={[
              { key: 'purpose', header: '用途', render: (row) => PURPOSE_LABEL[row.purpose] ?? row.purpose },
              { key: 'tokens', header: 'tokens', align: 'right', render: (row) => row.totalTokens.toLocaleString() },
              { key: 'cost', header: '花费', align: 'right', render: (row) => `¥${row.cost.toFixed(6)}` },
            ]}
            rows={recent}
            getKey={(row) => row.id}
            emptyText="还没有调用记录"
          />
        </TerminalCard>
      </div>
    </div>
  )
}
