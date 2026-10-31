import type { QuestionView } from '@/api/questions'

export interface PickRequest {
  categoryIds?: number[]
  types?: string[]
  mastery?: string[]
  count?: number
  order?: 'RANDOM' | 'OLDEST'
  excludeRecentDays?: number
  includeAnswer?: boolean
}

export interface PracticeView {
  sessionId: number
  status: string
  items: QuestionView[]
  total: number
  answered: number
}

export interface RateResult {
  questionId: number
  masteryStatus: string
  answered: number
  total: number
  known: number
  unknown: number
  vague: number
}

export interface PracticeSummary {
  sessionId: number
  total: number
  known: number
  unknown: number
  vague: number
  elapsedSeconds: number
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

export async function startPractice(request: PickRequest): Promise<PracticeView> {
  return parse<PracticeView>(
    await fetch('/api/practice/sessions', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    }),
  )
}

export async function rateQuestion(
  sessionId: number,
  questionId: number,
  rating: 'KNOWN' | 'UNKNOWN' | 'VAGUE',
): Promise<RateResult> {
  return parse<RateResult>(
    await fetch(`/api/practice/sessions/${sessionId}/rate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ questionId, rating }),
    }),
  )
}

export async function finishPractice(sessionId: number): Promise<PracticeSummary> {
  return parse<PracticeSummary>(
    await fetch(`/api/practice/sessions/${sessionId}/finish`, { method: 'POST' }),
  )
}
