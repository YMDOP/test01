export interface HealthInfo {
  status: string
  application: string
  version: string
  dataDir: string
  javaVersion: string
  time: string
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T | null
}

export async function fetchHealth(): Promise<HealthInfo> {
  const res = await fetch('/api/health')
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }
  const body = (await res.json()) as ApiResponse<HealthInfo>
  if (body.code !== 0 || !body.data) {
    throw new Error(body.message || '健康检查失败')
  }
  return body.data
}
