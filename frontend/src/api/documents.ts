export interface ScanItem {
  path: string
  filename: string
  ext: string
  size: number
}

export interface ScanSummary {
  root: string
  total: number
  items: ScanItem[]
  truncated: boolean
}

export interface ImportSummary {
  scanned: number
  imported: number
  skipped: number
  failed: number
  chunks: number
  elapsedMs: number
  messages: string[]
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

export async function scanDocuments(dir: string): Promise<ScanSummary> {
  return parse<ScanSummary>(
    await fetch('/api/documents/scan', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ dir }),
    }),
  )
}

export async function importDocuments(dir: string): Promise<ImportSummary> {
  return parse<ImportSummary>(
    await fetch('/api/documents/import', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ dir }),
    }),
  )
}
