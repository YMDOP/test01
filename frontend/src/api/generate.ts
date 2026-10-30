export interface Estimate {
  chunkCount: number
  estimatedCalls: number
  estimatedCost: number
  threshold: number
  needConfirm: boolean
}

export interface JobView {
  id: string
  status: string
  stage: string | null
  progress: number
  totalChunks: number
  totalCategories: number
  doneCategories: number
  questionCount: number
  estimatedCost: number
  message: string | null
}

export interface CreateResult {
  jobId: string | null
  needConfirm: boolean
  estimate: Estimate
}

export interface ResetResult {
  questionsDeleted: number
  aiUsageKept: number
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

export async function fetchEstimate(): Promise<Estimate> {
  return parse<Estimate>(await fetch('/api/generate/estimate'))
}

export async function createJob(maxChunks: number, confirm: boolean): Promise<CreateResult> {
  return parse<CreateResult>(
    await fetch('/api/generate/jobs', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ maxChunks, confirm }),
    }),
  )
}

export async function fetchJob(id: string): Promise<JobView> {
  return parse<JobView>(await fetch(`/api/generate/jobs/${id}`))
}

export async function resetLibrary(keepCategories: boolean): Promise<ResetResult> {
  return parse<ResetResult>(
    await fetch('/api/generate/reset', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ keepCategories }),
    }),
  )
}

/** SSE 订阅：progress / done / error 三种事件。 */
export function subscribeJob(id: string): EventSource {
  return new EventSource(`/api/generate/jobs/${id}/events`)
}
