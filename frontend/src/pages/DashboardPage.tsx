import { DataTable } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { MeterBar } from '@/components/MeterBar'
import { PageHead } from '@/components/PageHead'
import { Sparkline } from '@/components/Sparkline'
import { StatusDot } from '@/components/StatusDot'
import { TerminalCard } from '@/components/TerminalCard'

interface StatCard {
  label: string
  value: string
  unit?: string
  foot: string
}

const STATS: StatCard[] = [
  { label: '题库总量', value: '—', unit: '题', foot: '待 P4 生成' },
  { label: '知识分类', value: '—', unit: '类', foot: '待 P4 生成' },
  { label: '总体掌握率', value: '—', foot: '待 P6 练习数据' },
  { label: '今日练习', value: '0', unit: '题', foot: '本地统计，不耗 token' },
]

export function DashboardPage() {
  return (
    <div className="stack">
      <PageHead
        tag="dashboard"
        title="仪表盘"
        desc="题库概况、AI 用量与复习进度，一屏看全。"
        actions={<StatusDot tone="ok" label="LOCAL ONLY" />}
      />

      <div className="grid grid-4">
        {STATS.map((stat) => (
          <section key={stat.label} className="card stat">
            <span className="stat-label">{stat.label}</span>
            <span className={`stat-value${stat.value === '—' ? ' muted' : ''}`}>
              {stat.value}
              {stat.unit ? <span className="unit">{stat.unit}</span> : null}
            </span>
            <span className="stat-foot">{stat.foot}</span>
          </section>
        ))}
      </div>

      <div className="grid grid-main-side">
        <TerminalCard
          title="AI 用量与花费"
          subtitle="TOKEN ACCOUNTING"
          corners
          actions={<span className="tag accent">P2 接入</span>}
        >
          <div className="row-between" style={{ alignItems: 'flex-end' }}>
            <div>
              <p className="mono-label">今日花费</p>
              <p className="stat-value" style={{ marginTop: 4 }}>
                ¥0.0000
              </p>
            </div>
            <div style={{ textAlign: 'right' }}>
              <p className="mono-label">累计 token</p>
              <p className="stat-value" style={{ fontSize: 24, marginTop: 4 }}>
                0
              </p>
            </div>
          </div>

          <div className="divider" />

          <MeterBar label="缓存命中率" value={0} tone="info" />

          <div style={{ marginTop: 18 }}>
            <Sparkline points={[]} placeholder="生成题库后显示 14 天花费趋势" />
          </div>
        </TerminalCard>

        <TerminalCard title="分类掌握率" subtitle="MASTERY" corners>
          <EmptyState
            mark="[ -- ]"
            title="还没有分类数据"
            description="完成一次题库生成后，这里按分类显示掌握率，例如「消息队列 90% / MySQL 优化 60%」。"
          />
        </TerminalCard>
      </div>

      <div className="grid grid-2">
        <TerminalCard title="最近错题" subtitle="WRONG BOOK" tight>
          <DataTable
            columns={[
              { key: 'stem', header: '题目' },
              { key: 'category', header: '分类' },
              { key: 'at', header: '时间', align: 'right' },
            ]}
            rows={[]}
            getKey={(_, index) => index}
            emptyText="练习和模拟中答错的题会自动出现在这里"
          />
        </TerminalCard>

        <TerminalCard title="最近模拟" subtitle="MOCK EXAM" tight>
          <DataTable
            columns={[
              { key: 'at', header: '交卷时间' },
              { key: 'score', header: '得分', align: 'right' },
              { key: 'duration', header: '用时', align: 'right' },
            ]}
            rows={[]}
            getKey={(_, index) => index}
            emptyText="还没有模拟记录，P7 完成后可开始组卷"
          />
        </TerminalCard>
      </div>
    </div>
  )
}
