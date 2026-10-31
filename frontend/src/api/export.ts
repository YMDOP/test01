export interface ExportRequest {
  format: 'MD' | 'XLSX'
  categoryId?: number
  includeAnswer?: boolean
  includeExplanation?: boolean
}

export interface ExportResult {
  id: number
  format: string
  path: string
  sizeBytes: number
  questionCount: number
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

export async function exportLibrary(request: ExportRequest): Promise<ExportResult> {
  return parse<ExportResult>(
    await fetch('/api/export', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    }),
  )
}

export function exportDownloadUrl(id: number): string {
  return `/api/export/${id}/download`
}
