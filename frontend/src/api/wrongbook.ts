export interface WrongItem {
  id: number
  questionId: number
  type: string
  stem: string
  categoryName: string
  parentName: string
  sourceType: string
  createdAt: string
  resolvedAt: string | null
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

export interface WrongBookResult {
  items: WrongItem[]
  count: number
  unresolved: number
}

export async function fetchWrongBook(type?: string): Promise<WrongBookResult> {
  const search = new URLSearchParams()
  if (type) search.set('type', type)
  return parse<WrongBookResult>(await fetch(`/api/wrongbook?${search.toString()}`))
}

export async function resolveWrong(id: number): Promise<void> {
  await fetch(`/api/wrongbook/${id}/resolve`, { method: 'POST' })
}

export async function deleteWrong(id: number): Promise<void> {
  await fetch(`/api/wrongbook/${id}`, { method: 'DELETE' })
}
