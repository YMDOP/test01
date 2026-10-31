import { useCallback, useEffect, useMemo, useState } from 'react'

import {
  createQuestion,
  deleteQuestion,
  fetchCategoryTree,
  fetchQuestions,
  searchChunks,
  updateQuestion,
  type CategoryNode,
  type ChunkItem,
  type QuestionInput,
  type QuestionPage,
  type QuestionView,
} from '@/api/questions'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { DataTable } from '@/components/DataTable'
import { EmptyState } from '@/components/EmptyState'
import { PageHead } from '@/components/PageHead'
import { TerminalCard } from '@/components/TerminalCard'
import { exportDownloadUrl, exportLibrary } from '@/api/export'

const TYPE_LABEL: Record<string, string> = { SA: '简答', MC: '选择', TF: '判断' }
const MASTERY_LABEL: Record<string, string> = {
  UNTRIED: '未做',
  CORRECT: '答对',
  WRONG: '答错',
  VAGUE: '模糊',
}

const EMPTY_FORM: QuestionInput & { options: string[] } = {
  categoryId: 0,
  type: 'SA',
  difficulty: 3,
  stem: '',
  options: ['', '', '', ''],
  standardAnswer: '',
  explanation: '',
  sourceChunkId: 0,
}

export function LibraryPage() {
  const [tree, setTree] = useState<CategoryNode[]>([])
  const [selectedCategory, setSelectedCategory] = useState<number | null>(null)
  const [type, setType] = useState('')
  const [mastery, setMastery] = useState('')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const [data, setData] = useState<QuestionPage>({ items: [], total: 0 })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [exporting, setExporting] = useState('')
  const [exportMessage, setExportMessage] = useState('')

  const [drawerOpen, setDrawerOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState({ ...EMPTY_FORM })
  const [chunkKeyword, setChunkKeyword] = useState('')
  const [chunks, setChunks] = useState<ChunkItem[]>([])
  const [saving, setSaving] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState<QuestionView | null>(null)

  const leaves = useMemo(() => tree.flatMap((parent) => parent.children ?? []), [tree])

  const loadQuestions = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const result = await fetchQuestions({
        categoryIds: selectedCategory ? [selectedCategory] : undefined,
        type: type || undefined,
        mastery: mastery || undefined,
        keyword: keyword || undefined,
        page,
        size: 20,
      })
      setData(result)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [selectedCategory, type, mastery, keyword, page])

  useEffect(() => {
    fetchCategoryTree()
      .then(setTree)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)))
  }, [])

  useEffect(() => {
    void loadQuestions()
  }, [loadQuestions])

  function openCreate() {
    setEditingId(null)
    setForm({ ...EMPTY_FORM, categoryId: leaves[0]?.id ?? 0 })
    setChunks([])
    setChunkKeyword('')
    setDrawerOpen(true)
  }

  function openEdit(question: QuestionView) {
    setEditingId(question.id)
    const options = question.optionsJson ? (JSON.parse(question.optionsJson) as string[]) : ['', '', '', '']
    setForm({
      categoryId: question.categoryId,
      type: question.type,
      difficulty: question.difficulty,
      stem: question.stem,
      options: [...options, '', '', '', ''].slice(0, Math.max(4, options.length)),
      standardAnswer: question.standardAnswer ?? '',
      explanation: question.explanation ?? '',
      sourceChunkId: question.sourceChunkId,
    })
    setChunks([])
    setChunkKeyword('')
    setDrawerOpen(true)
  }

  async function handleSearchChunks() {
    try {
      setChunks(await searchChunks(chunkKeyword))
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }

  async function handleSave() {
    setSaving(true)
    setError('')
    try {
      const payload: QuestionInput = {
        categoryId: form.categoryId,
        type: form.type,
        difficulty: form.difficulty,
        stem: form.stem,
        standardAnswer: form.standardAnswer,
        explanation: form.explanation,
        sourceChunkId: form.sourceChunkId,
        options: form.type === 'MC' ? form.options.filter((option) => option.trim() !== '') : undefined,
      }
      if (editingId === null) {
        await createQuestion(payload)
      } else {
        await updateQuestion(editingId, payload)
      }
      setDrawerOpen(false)
      await loadQuestions()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete() {
    if (!confirmDelete) return
    try {
      await deleteQuestion(confirmDelete.id)
      setConfirmDelete(null)
      await loadQuestions()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }

  async function handleExport(format: 'MD' | 'XLSX') {
    setExporting(format)
    setExportMessage('')
    setError('')
    try {
      const result = await exportLibrary({
        format,
        categoryId: selectedCategory ?? undefined,
        includeAnswer: true,
        includeExplanation: true,
      })
      const anchor = document.createElement('a')
      anchor.href = exportDownloadUrl(result.id)
      anchor.click()
      setExportMessage(
        `已导出 ${result.questionCount} 题（${format}，${Math.max(1, Math.round(result.sizeBytes / 1024))} KB）`,
      )
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setExporting('')
    }
  }

  const totalPages = Math.max(1, Math.ceil(data.total / 20))

  return (
    <div className="stack">
      <PageHead
        tag="library"
        title="题库中心"
        desc="按分类浏览、编辑、删除题目；手工新增的题也必须绑定来源切片。"
        actions={
          <div className="row" style={{ gap: 8 }}>
            <button
              type="button"
              className="btn"
              disabled={exporting !== ''}
              onClick={() => void handleExport('MD')}
            >
              {exporting === 'MD' ? '导出中…' : '导出 MD'}
            </button>
            <button
              type="button"
              className="btn"
              disabled={exporting !== ''}
              onClick={() => void handleExport('XLSX')}
            >
              {exporting === 'XLSX' ? '导出中…' : '导出 XLSX'}
            </button>
            <button type="button" className="btn primary" onClick={openCreate}>
              新增题目
            </button>
          </div>
        }
      />

      {error ? <p className="notice">{error}</p> : null}

      {exportMessage ? (
        <p className="settings-row-hint" style={{ color: 'var(--ok)' }}>
          {exportMessage}
        </p>
      ) : null}

      <div className="split">
        <TerminalCard title="知识分类" subtitle="CATEGORIES">
          <div className="cat-list">
            <button
              type="button"
              className={`cat-item${selectedCategory === null ? ' active' : ''}`}
              onClick={() => {
                setSelectedCategory(null)
                setPage(1)
              }}
            >
              <span>全部题目</span>
              <span className="cat-count">{data.total}</span>
            </button>
            {tree.map((parent) => (
              <div key={parent.id}>
                <div className="cat-item tree-parent" style={{ cursor: 'default' }}>
                  <span>{parent.name}</span>
                  <span className="cat-count">{parent.questionCount}</span>
                </div>
                <div className="tree-children">
                  {(parent.children ?? []).map((child) => (
                    <button
                      key={child.id}
                      type="button"
                      className={`cat-item tree-child${selectedCategory === child.id ? ' active' : ''}`}
                      onClick={() => {
                        setSelectedCategory(child.id)
                        setPage(1)
                      }}
                    >
                      <span>{child.name}</span>
                      <span className="cat-count">{child.questionCount}</span>
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </TerminalCard>

        <TerminalCard
          title="题目列表"
          subtitle={`${data.total} 题`}
          tight
          actions={
            <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
              <input
                className="input"
                style={{ width: 180 }}
                placeholder="搜索题干"
                value={keyword}
                onChange={(event) => {
                  setKeyword(event.target.value)
                  setPage(1)
                }}
              />
              <select
                className="select"
                value={type}
                onChange={(event) => {
                  setType(event.target.value)
                  setPage(1)
                }}
              >
                <option value="">全部题型</option>
                <option value="SA">简答</option>
                <option value="MC">选择</option>
                <option value="TF">判断</option>
              </select>
              <select
                className="select"
                value={mastery}
                onChange={(event) => {
                  setMastery(event.target.value)
                  setPage(1)
                }}
              >
                <option value="">全部掌握度</option>
                <option value="UNTRIED">未做</option>
                <option value="CORRECT">答对</option>
                <option value="WRONG">答错</option>
                <option value="VAGUE">模糊</option>
              </select>
            </div>
          }
        >
          {data.items.length === 0 && !loading ? (
            <EmptyState
              mark="[ -- ]"
              title="没有匹配的题目"
              description="换个分类或筛选条件；也可以点右上角「新增题目」手工补一道。"
            />
          ) : (
            <DataTable
              columns={[
                { key: 'type', header: '题型', render: (row) => TYPE_LABEL[row.type] ?? row.type },
                { key: 'stem', header: '题干', render: (row) => row.stem.slice(0, 48) },
                {
                  key: 'category',
                  header: '分类',
                  render: (row) => `${row.parentName} / ${row.categoryName}`,
                },
                {
                  key: 'mastery',
                  header: '掌握度',
                  render: (row) => MASTERY_LABEL[row.masteryStatus] ?? row.masteryStatus,
                },
                {
                  key: 'ops',
                  header: '操作',
                  align: 'right',
                  render: (row) => (
                    <span className="row" style={{ justifyContent: 'flex-end', gap: 6 }}>
                      <button type="button" className="btn ghost" onClick={() => openEdit(row)}>
                        编辑
                      </button>
                      <button type="button" className="btn danger" onClick={() => setConfirmDelete(row)}>
                        删除
                      </button>
                    </span>
                  ),
                },
              ]}
              rows={data.items}
              getKey={(row) => row.id}
            />
          )}
          <div className="pager">
            <button
              type="button"
              className="btn ghost"
              disabled={page <= 1}
              onClick={() => setPage((current) => current - 1)}
            >
              上一页
            </button>
            <span>
              {page} / {totalPages}
            </span>
            <button
              type="button"
              className="btn ghost"
              disabled={page >= totalPages}
              onClick={() => setPage((current) => current + 1)}
            >
              下一页
            </button>
          </div>
        </TerminalCard>
      </div>

      {drawerOpen ? (
        <div
          className="drawer-backdrop"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) setDrawerOpen(false)
          }}
        >
          <section className="drawer" role="dialog" aria-modal="true" aria-label="编辑题目">
            <header className="drawer-head">
              <h2 className="card-title">{editingId === null ? '新增题目' : '编辑题目'}</h2>
              <button
                type="button"
                className="btn ghost"
                style={{ marginLeft: 'auto' }}
                onClick={() => setDrawerOpen(false)}
              >
                关闭
              </button>
            </header>

            <div className="drawer-body">
              <div className="grid grid-2">
                <div className="field">
                  <label className="field-label" htmlFor="q-category">
                    分类
                  </label>
                  <select
                    id="q-category"
                    className="select"
                    value={form.categoryId}
                    onChange={(event) => setForm({ ...form, categoryId: Number(event.target.value) })}
                  >
                    {leaves.map((leaf) => (
                      <option key={leaf.id} value={leaf.id}>
                        {leaf.name}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="field">
                  <label className="field-label" htmlFor="q-type">
                    题型
                  </label>
                  <select
                    id="q-type"
                    className="select"
                    value={form.type}
                    onChange={(event) => setForm({ ...form, type: event.target.value })}
                  >
                    <option value="SA">简答</option>
                    <option value="MC">选择</option>
                    <option value="TF">判断</option>
                  </select>
                </div>
              </div>

              <div className="field">
                <label className="field-label" htmlFor="q-stem">
                  题干
                </label>
                <textarea
                  id="q-stem"
                  className="input"
                  rows={3}
                  value={form.stem}
                  onChange={(event) => setForm({ ...form, stem: event.target.value })}
                />
              </div>

              {form.type === 'MC' ? (
                <div className="field">
                  <span className="field-label">选项</span>
                  {form.options.map((option, index) => (
                    <input
                      key={index}
                      className="input"
                      placeholder={`选项 ${String.fromCharCode(65 + index)}`}
                      value={option}
                      onChange={(event) => {
                        const next = [...form.options]
                        next[index] = event.target.value
                        setForm({ ...form, options: next })
                      }}
                    />
                  ))}
                </div>
              ) : null}

              <div className="field">
                <label className="field-label" htmlFor="q-answer">
                  标准答案
                  {form.type === 'TF' ? <span className="value">TRUE / FALSE</span> : null}
                </label>
                <input
                  id="q-answer"
                  className="input"
                  value={form.standardAnswer}
                  onChange={(event) => setForm({ ...form, standardAnswer: event.target.value })}
                />
              </div>

              <div className="field">
                <label className="field-label" htmlFor="q-exp">
                  解析
                </label>
                <textarea
                  id="q-exp"
                  className="input"
                  rows={3}
                  value={form.explanation}
                  onChange={(event) => setForm({ ...form, explanation: event.target.value })}
                />
              </div>

              <div className="field">
                <span className="field-label">
                  来源切片
                  <span className="value">{form.sourceChunkId ? `#${form.sourceChunkId}` : '未选择'}</span>
                </span>
                <div className="row" style={{ gap: 6 }}>
                  <input
                    className="input"
                    placeholder="搜索原文关键词"
                    value={chunkKeyword}
                    onChange={(event) => setChunkKeyword(event.target.value)}
                  />
                  <button type="button" className="btn" onClick={() => void handleSearchChunks()}>
                    搜索
                  </button>
                </div>
                {chunks.map((chunk) => (
                  <button
                    key={chunk.id}
                    type="button"
                    className={`chunk-hit${form.sourceChunkId === chunk.id ? ' selected' : ''}`}
                    onClick={() => setForm({ ...form, sourceChunkId: chunk.id })}
                  >
                    <strong>
                      #{chunk.id} {chunk.heading_path ?? ''}
                    </strong>
                    <span>{chunk.snippet}</span>
                  </button>
                ))}
              </div>
            </div>

            <footer className="drawer-foot">
              <button type="button" className="btn ghost" onClick={() => setDrawerOpen(false)}>
                取消
              </button>
              <button
                type="button"
                className="btn primary"
                disabled={saving}
                onClick={() => void handleSave()}
              >
                {saving ? '保存中…' : '保存'}
              </button>
            </footer>
          </section>
        </div>
      ) : null}

      <ConfirmDialog
        open={confirmDelete !== null}
        title="删除这道题？"
        description={`「${confirmDelete?.stem.slice(0, 40) ?? ''}」删除后，关联的掌握度、错题、练习记录会一并清理，不可恢复。`}
        confirmText="删除"
        danger
        onCancel={() => setConfirmDelete(null)}
        onConfirm={() => void handleDelete()}
      />
    </div>
  )
}
