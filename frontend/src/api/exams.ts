export interface ExamQuestion {
  questionId: number
  orderNo: number
  type: 'SA' | 'MC' | 'TF'
  stem: string
  optionsJson: string | null
  maxScore: number
}

export interface ExamView {
  examId: number
  status: string
  startedAt: string
  durationMinutes: number
  maxScore: number
  items: ExamQuestion[]
}

export interface ReportItem {
  questionId: number
  orderNo: number
  type: string
  stem: string
  optionsJson: string | null
  userAnswer: string | null
  standardAnswer: string
  explanation: string
  score: number
  maxScore: number
  hitPoints: string[]
  missedPoints: string[]
  suggestion: string
}

export interface Report {
  examId: number
  status: string
  totalScore: number
  maxScore: number
  durationSec: number
  startedAt: string
  submittedAt: string
  items: ReportItem[]
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

export async function createExam(): Promise<ExamView> {
  return parse<ExamView>(await fetch('/api/exams', { method: 'POST' }))
}

export async function fetchExam(id: number): Promise<ExamView> {
  return parse<ExamView>(await fetch(`/api/exams/${id}`))
}

export async function saveAnswer(id: number, questionId: number, answer: string): Promise<void> {
  await fetch(`/api/exams/${id}/answer`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ questionId, answer }),
  })
}

export async function submitExam(id: number): Promise<Report> {
  return parse<Report>(await fetch(`/api/exams/${id}/submit`, { method: 'POST' }))
}

export async function fetchResult(id: number): Promise<Report> {
  return parse<Report>(await fetch(`/api/exams/${id}/result`))
}
