export interface AiConfigView {
  baseUrl: string
  model: string
  keyMasked: string
  keySource: 'ENV' | 'DB' | 'NONE'
  temperature: number
  maxTokens: number
  enabled: boolean
  updatedAt: string
}

export interface AiTestUsage {
  inputTokens: number
  outputTokens: number
  cacheReadTokens: number
  cacheWriteTokens: number
  totalTokens: number
}

export interface AiTestResult {
  ok: boolean
  model: string
  keySource: string
  latencyMs: number
  usage: AiTestUsage
  estCost: number
  currency: string
}

export interface AiConfigPatch {
  baseUrl?: string
  model?: string
  apiKey?: string
  temperature?: number
  maxTokens?: number
  enabled?: boolean
  inputMissPrice?: number
  inputHitPrice?: number
  cacheWritePrice?: number
  outputPrice?: number
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

export async function fetchAiConfig(): Promise<AiConfigView> {
  return parse<AiConfigView>(await fetch('/api/settings/ai'))
}

export async function saveAiConfig(patch: AiConfigPatch): Promise<AiConfigView> {
  return parse<AiConfigView>(
    await fetch('/api/settings/ai', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(patch),
    }),
  )
}

export async function testAiConfig(): Promise<AiTestResult> {
  return parse<AiTestResult>(await fetch('/api/settings/ai/test', { method: 'POST' }))
}
