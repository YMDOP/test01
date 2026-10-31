import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'

import { deleteWrong, fetchWrongBook, resolveWrong, type WrongItem } from '@/api/wrongbook'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { DataTable } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { PageHead } from '@/components/PageHead'
import { TerminalCard } from '@/components/TerminalCard'

const TYPE_LABEL: Record<string, string> = { SA: '简答', MC: '选择', TF: '判断' }

export function WrongBookPage() {
  const navigate = useNavigate()
  const [items, setItems] = useState<WrongItem[]>([])
  const [unresolved, setUnresolved] = useState(0)
  const [type, setType] = useState('')
  const [error, setError] = useState('')
  const [confirmDelete, setConfirmDelete] = useState<WrongItem | null>(null)

  const load = useCallback(async () => {
    try {
      const result = await fetchWrongBook(type || undefined)
      setItems(result.items)
      setUnresolved(result.unresolved)
      setError('')
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }, [type])

  useEffect(() => {
    void load()
  }, [load])

  async function handleResolve(item: WrongItem) {
    try {
      await resolveWrong(item.id)
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }

  async function handleDelete() {
    if (!confirmDelete) return
    try {
      await deleteWrong(confirmDelete.id)
      setConfirmDelete(null)
      await load()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }

  return (
    <div className="stack">
      <PageHead
        tag="wrongbook"
        title="错题本"
        desc="练习中「记为不会」的题会自动归集，按题去重。"
        actions={
          <button type="button" className="btn primary" onClick={() => navigate('/practice')}>
            去练习
          </button>
        }
      />

      {error ? <p className="notice">{error}</p> : null}

      <TerminalCard
        title="错题列表"
        subtitle={`未掌握 ${unresolved} 题`}
        tight
        corners
        actions={
          <select className="select" value={type} onChange={(event) => setType(event.target.value)}>
            <option value="">全部题型</option>
            <option value="SA">简答</option>
            <option value="MC">选择</option>
            <option value="TF">判断</option>
          </select>
        }
      >
        {items.length === 0 ? (
          <EmptyState
            mark="[ i ]"
            title="错题本当前为空"
            description="去练习模式做几题，评「不会」的题会自动出现在这里。"
          />
        ) : (
          <DataTable
            columns={[
              { key: 'type', header: '题型', render: (row) => TYPE_LABEL[row.type] ?? row.type },
              { key: 'stem', header: '题干', render: (row) => row.stem.slice(0, 52) },
              { key: 'category', header: '分类', render: (row) => `${row.parentName} / ${row.categoryName}` },
              { key: 'source', header: '来源', render: (row) => row.sourceType },
              {
                key: 'ops',
                header: '操作',
                align: 'right',
                render: (row) => (
                  <span className="row" style={{ justifyContent: 'flex-end', gap: 6 }}>
                    <button type="button" className="btn ghost" onClick={() => void handleResolve(row)}>
                      已掌握
                    </button>
                    <button type="button" className="btn danger" onClick={() => setConfirmDelete(row)}>
                      移除
                    </button>
                  </span>
                ),
              },
            ]}
            rows={items}
            getKey={(row) => row.id}
          />
        )}
      </TerminalCard>

      <ConfirmDialog
        open={confirmDelete !== null}
        title="从错题本移除？"
        description="只删除这条错题记录，题目本身仍保留在题库中。"
        confirmText="移除"
        danger
        onCancel={() => setConfirmDelete(null)}
        onConfirm={() => void handleDelete()}
      />
    </div>
  )
}
