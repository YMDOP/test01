import { useState } from 'react'

import { DataTable } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { PageHead } from '@/components/PageHead'
import { TerminalCard } from '@/components/TerminalCard'

const TYPE_CHIPS = ['全部', '简答', '选择', '判断']

export function LibraryPage() {
  const [activeType, setActiveType] = useState('全部')

  return (
    <div className="stack">
      <PageHead
        tag="library"
        title="题库中心"
        desc="所有题目的唯一数据源，支持按分类、题型、掌握度筛选。"
        actions={
          <button type="button" className="btn" disabled>
            导入文档
          </button>
        }
      />

      <div className="split">
        <TerminalCard title="知识分类" subtitle="CATEGORIES">
          <div className="cat-list">
            <EmptyState
              mark="[ -- ]"
              title="暂无分类"
              description="导入文档并生成题库后，分类会出现在这里。"
            />
          </div>
        </TerminalCard>

        <TerminalCard
          title="题目列表"
          subtitle="QUESTIONS"
          tight
          actions={
            <div className="chip-row">
              {TYPE_CHIPS.map((chip) => (
                <button
                  key={chip}
                  type="button"
                  className={`chip${activeType === chip ? ' active' : ''}`}
                  onClick={() => setActiveType(chip)}
                >
                  {chip}
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
              { key: 'mastery', header: '掌握度', align: 'right' },
            ]}
            rows={[]}
            getKey={(_, index) => index}
            emptyText="题库为空。P3 完成文档导入、P4 完成生成后，题目会在这里列出。"
          />
        </TerminalCard>
      </div>
    </div>
  )
}
