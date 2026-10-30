import { useState } from 'react'

import { DataTable } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { PageHead } from '@/components/PageHead'
import { TerminalCard } from '@/components/TerminalCard'

const FILTERS = ['全部', '练习答错', '模拟答错', '已掌握']

export function WrongBookPage() {
  const [filter, setFilter] = useState(FILTERS[0])

  return (
    <div className="stack">
      <PageHead
        tag="wrongbook"
        title="错题本"
        desc="练习与模拟中答错的题自动归集，按题去重，可一键重刷。"
        actions={
          <button type="button" className="btn primary" disabled>
            一键重刷
          </button>
        }
      />

      <TerminalCard
        title="错题列表"
        subtitle="WRONG BOOK"
        tight
        corners
        actions={
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
        }
      >
        <DataTable
          columns={[
            { key: 'type', header: '题型' },
            { key: 'stem', header: '题干' },
            { key: 'category', header: '分类' },
            { key: 'source', header: '来源' },
            { key: 'at', header: '入本时间', align: 'right' },
          ]}
          rows={[]}
          getKey={(_, index) => index}
        />
      </TerminalCard>

      <TerminalCard title="复习说明" subtitle="NOTE">
        <div className="stack" style={{ gap: 10 }}>
          <EmptyState
            mark="[ i ]"
            title="错题本当前为空"
            description="答错的题会自动进入这里；「一键重刷」会跳转练习模式并预置「只抽答错」条件。同一道题答错多次仍只有一条记录。"
          />
        </div>
      </TerminalCard>
    </div>
  )
}
