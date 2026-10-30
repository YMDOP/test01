import { PageHead } from '@/components/PageHead'
import { TerminalCard } from '@/components/TerminalCard'

const BLUEPRINT = [
  { key: '总题量', value: '15' },
  { key: '简答', value: '10' },
  { key: '选择', value: '3' },
  { key: '判断', value: '2' },
]

export function InterviewPage() {
  return (
    <div className="stack">
      <PageHead
        tag="interview"
        title="模拟面试"
        desc="从题库自动组卷，全程计时；交卷后 AI 只对照标准答案阅卷。"
      />

      <TerminalCard
        title="组卷蓝图"
        subtitle="BLUEPRINT"
        corners
        actions={<span className="tag">P7 接入</span>}
      >
        <div className="blueprint">
          {BLUEPRINT.map((item) => (
            <div key={item.key} className="blueprint-item">
              <p className="blueprint-k">{item.key}</p>
              <p className="blueprint-v">{item.value}</p>
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
              客观题本地比对；简答题把「你的答案 + 标准答案」一并交给 AI 判分，
              <br />
              AI 不得引入标准答案之外的知识点。
            </p>
          </div>
        </div>

        <div className="notice" style={{ marginTop: 20 }}>
          <strong>组卷与判分尚未接通。</strong>
          <br />
          P7 会实现：不提前透题、交卷后才显示答案、错题自动进入错题本。
        </div>

        <div className="row" style={{ marginTop: 18 }}>
          <button type="button" className="btn primary" disabled>
            开始考试
          </button>
          <span className="text-dim" style={{ fontSize: 12 }}>
            题库为空时无法组卷
          </span>
        </div>
      </TerminalCard>
    </div>
  )
}
