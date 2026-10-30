import { MeterBar } from '@/components/MeterBar'
import { PageHead } from '@/components/PageHead'
import { TerminalCard } from '@/components/TerminalCard'

export function StatsPage() {
  return (
    <div className="stack">
      <PageHead
        tag="stats"
        title="进度统计"
        desc="按分类看掌握率，一眼找出没复习到的地方。"
      />

      <div className="grid grid-2">
        <TerminalCard title="分类掌握率" subtitle="MASTERY BY CATEGORY" corners>
          <div className="stack" style={{ gap: 16 }}>
            <MeterBar label="消息队列" value={0} />
            <MeterBar label="MySQL 优化" value={0} />
            <MeterBar label="Spring Security" value={0} />
            <p className="settings-row-hint">
              当前为占位示例。题库生成后按真实分类渲染，数据来自本地 mastery 表。
            </p>
          </div>
        </TerminalCard>

        <TerminalCard title="题型正确率" subtitle="ACCURACY BY TYPE" corners>
          <div className="stack" style={{ gap: 16 }}>
            <MeterBar label="简答题" value={0} tone="info" />
            <MeterBar label="选择题" value={0} tone="info" />
            <MeterBar label="判断题" value={0} tone="info" />
            <p className="settings-row-hint">统计口径：答对次数 / 总作答次数，模糊不计入分子。</p>
          </div>
        </TerminalCard>
      </div>

      <TerminalCard title="近 30 天趋势" subtitle="TREND">
        <div className="notice">
          暂无数据。练习与模拟记录产生后，这里展示每日练习量、模拟得分与错题存量。
        </div>
      </TerminalCard>
    </div>
  )
}
