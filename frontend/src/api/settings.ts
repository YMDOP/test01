export type SettingsMap = Record<string, string>

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

export async function fetchSettings(): Promise<SettingsMap> {
  return parse<SettingsMap>(await fetch('/api/settings'))
}

export async function saveSettings(patch: SettingsMap): Promise<SettingsMap> {
  return parse<SettingsMap>(
    await fetch('/api/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(patch),
    }),
  )
}

export async function uploadBackground(file: File): Promise<{ version: string; enabled: string }> {
  const form = new FormData()
  form.append('file', file)
  return parse<{ version: string; enabled: string }>(
    await fetch('/api/settings/background', { method: 'POST', body: form }),
  )
}

export async function deleteBackground(): Promise<void> {
  await fetch('/api/settings/background', { method: 'DELETE' })
}

export function backgroundUrl(version: string): string {
  return `/api/settings/background?v=${encodeURIComponent(version)}`
}
