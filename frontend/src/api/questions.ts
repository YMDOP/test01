export interface CategoryNode {
  id: number
  name: string
  questionCount: number
  children?: CategoryNode[]
}

export interface QuestionView {
  id: number
  categoryId: number
  categoryName: string
  parentName: string
  type: 'SA' | 'MC' | 'TF'
  difficulty: number
  stem: string
  optionsJson: string | null
  standardAnswer: string | null
  explanation: string | null
  sourceChunkId: number
  qualityFlag: string
  masteryStatus: 'UNTRIED' | 'CORRECT' | 'WRONG' | 'VAGUE'
}

export interface QuestionPage {
  items: QuestionView[]
  total: number
}

export interface QuestionInput {
  categoryId: number
  type: string
  difficulty: number
  stem: string
  options?: string[]
  standardAnswer: string
  explanation: string
  sourceChunkId: number
}

export interface ChunkItem {
  id: number
  heading_path: string | null
  snippet: string
}

interface ApiEnvelope<T> {
  code: number
  message: string
  data: T | null
}

async function parse<T>(res: Response): Promise<T> {
  const body = (await res.json()) as ApiEnvelope<T>
  if (body.code !== 0 || body.data === null) {
    throw new Error(body.message || `HTTP ${res.status}`)
  }
  return body.data
}

export async function fetchCategoryTree(): Promise<CategoryNode[]> {
  return parse<CategoryNode[]>(await fetch('/api/categories/tree'))
}

export async function fetchQuestions(params: {
  categoryIds?: number[]
  type?: string
  mastery?: string
  keyword?: string
  page?: number
  size?: number
}): Promise<QuestionPage> {
  const search = new URLSearchParams()
  if (params.categoryIds?.length) params.categoryIds.forEach((id) => search.append('categoryIds', String(id)))
  if (params.type) search.set('type', params.type)
  if (params.mastery) search.set('mastery', params.mastery)
  if (params.keyword) search.set('keyword', params.keyword)
  search.set('page', String(params.page ?? 1))
  search.set('size', String(params.size ?? 20))
  return parse<QuestionPage>(await fetch(`/api/questions?${search.toString()}`))
}

export async function createQuestion(input: QuestionInput): Promise<QuestionView> {
  return parse<QuestionView>(
    await fetch('/api/questions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    }),
  )
}

export async function updateQuestion(id: number, input: QuestionInput): Promise<QuestionView> {
  return parse<QuestionView>(
    await fetch(`/api/questions/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    }),
  )
}

export async function deleteQuestion(id: number): Promise<void> {
  await fetch(`/api/questions/${id}`, { method: 'DELETE' })
}

export async function searchChunks(keyword: string): Promise<ChunkItem[]> {
  const search = new URLSearchParams()
  if (keyword) search.set('keyword', keyword)
  search.set('limit', '10')
  return parse<ChunkItem[]>(await fetch(`/api/chunks?${search.toString()}`))
}
