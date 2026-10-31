export type UsageRange = 'TODAY' | '7D' | '30D' | 'ALL'

export interface PurposeStat {
  calls: number
  tokens: number
  cost: number
}

export interface UsageSummary {
  range: UsageRange
  from: string | null
  to: string
  totalCalls: number
  inputTokens: number
  outputTokens: number
  cacheReadTokens: number
  cacheWriteTokens: number
  totalTokens: number
  cacheHitRate: number
  totalCost: number
  storedCost: number
  currency: string
  byPurpose: Record<string, PurposeStat>
}

export interface RecentUsage {
  id: number
  ts: string
  purpose: string
  model: string
  inputTokens: number
  outputTokens: number
  cacheReadTokens: number
  totalTokens: number
  cost: number
  latencyMs: number
  status: string
}

export interface DailyPoint {
  day: string
  calls: number
  tokens: number
  cost: number
}

export interface QbankSummary {
  questions: number
  categories: number
  documents: number
  chunks: number
  unclassifiedChunks: number
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

export async function fetchUsageSummary(range: UsageRange): Promise<UsageSummary> {
  return parse<UsageSummary>(await fetch(`/api/usage/summary?range=${range}`))
}

export async function fetchRecentUsage(limit = 10): Promise<RecentUsage[]> {
  return parse<RecentUsage[]>(await fetch(`/api/usage/recent?limit=${limit}`))
}

export async function fetchDailyUsage(days = 14): Promise<DailyPoint[]> {
  return parse<DailyPoint[]>(await fetch(`/api/usage/daily?days=${days}`))
}

export async function fetchQbankSummary(): Promise<QbankSummary> {
  return parse<QbankSummary>(await fetch('/api/generate/summary'))
}
