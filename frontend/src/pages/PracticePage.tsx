import { useState } from 'react'

import { PageHead } from '@/components/PageHead'
import { TerminalCard } from '@/components/TerminalCard'

const SCOPES = ['全题库', '指定分类']
const TYPES = ['全部题型', '简答', '选择', '判断']
const FILTERS = ['随机', '只抽答错', '只抽模糊', '只抽未做']

export function PracticePage() {
  const [scope, setScope] = useState(SCOPES[0])
  const [type, setType] = useState(TYPES[0])
  const [filter, setFilter] = useState(FILTERS[0])
  const [showAnswer, setShowAnswer] = useState(false)
  const [count, setCount] = useState('10')

  return (
    <div className="stack">
      <PageHead
        tag="practice"
        title="练习模式"
        desc="从本地题库抽题，全程离线，不消耗 token。"
      />

      <div className="grid grid-main-side">
        <TerminalCard title="抽题参数" subtitle="PARAMETERS" corners>
          <div className="param-grid">
            <div className="param-block">
              <span className="param-label">范围</span>
              <div className="chip-row">
                {SCOPES.map((item) => (
                  <button
                    key={item}
                    type="button"
                    className={`chip${scope === item ? ' active' : ''}`}
                    onClick={() => setScope(item)}
                  >
                    {item}
                  </button>
                ))}
              </div>
            </div>

            <div className="param-block">
              <span className="param-label">题型</span>
              <div className="chip-row">
                {TYPES.map((item) => (
                  <button
                    key={item}
                    type="button"
                    className={`chip${type === item ? ' active' : ''}`}
                    onClick={() => setType(item)}
                  >
                    {item}
                  </button>
                ))}
              </div>
            </div>

            <div className="param-block">
              <span className="param-label">过滤</span>
              <div className="chip-row">
                {FILTERS.map((item) => (
                  <button
                    key={item}
                    type="button"
                    className={`chip${filter === item ? ' active' : ''}`}
                    onClick={() => setFilter(item)}
                  >
                    {item}
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
          </div>

          <div className="divider" />

          <div className="settings-row">
            <div>
              <p className="settings-row-label">显示答案与解析</p>
              <p className="settings-row-hint">
                开启即开卷背题模式；关闭则先自己想，再翻看答案。
              </p>
            </div>
            <button
              type="button"
              className={`switch${showAnswer ? ' on' : ''}`}
              role="switch"
              aria-checked={showAnswer}
              aria-label="显示答案与解析"
              onClick={() => setShowAnswer((value) => !value)}
            />
          </div>

          <div className="row" style={{ marginTop: 18 }}>
            <button type="button" className="btn primary" disabled>
              开始练习
            </button>
            <span className="tag">P6 接入</span>
          </div>
        </TerminalCard>

        <TerminalCard title="本次会话" subtitle="SESSION">
          <div className="stack" style={{ gap: 12 }}>
            <div className="notice">
              <strong>练习流程尚未接通。</strong>
              <br />
              抽题、自评与掌握度写回会在 P6 实现；届时本页完全本地，不发任何 AI 请求。
            </div>
            <div className="blueprint">
              <div className="blueprint-item">
                <p className="blueprint-k">题量</p>
                <p className="blueprint-v">{count || '—'}</p>
              </div>
              <div className="blueprint-item">
                <p className="blueprint-k">AI 调用</p>
                <p className="blueprint-v">0</p>
              </div>
            </div>
          </div>
        </TerminalCard>
      </div>
    </div>
  )
}
