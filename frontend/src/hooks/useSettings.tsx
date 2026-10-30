import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'

import {
  deleteBackground,
  fetchSettings,
  saveSettings,
  uploadBackground,
  type SettingsMap,
} from '@/api/settings'

interface SettingsContextValue {
  settings: SettingsMap
  loading: boolean
  error: string
  update: (patch: SettingsMap) => Promise<void>
  upload: (file: File) => Promise<void>
  remove: () => Promise<void>
  refresh: () => Promise<void>
}

const DEFAULTS: SettingsMap = {
  'ui.background.enabled': 'false',
  'ui.background.brightness': '0.48',
  'ui.background.blur': '0',
  'ui.background.veil': '0.62',
  'ui.background.vignette': 'true',
  'ui.background.version': '0',
}

const SettingsContext = createContext<SettingsContextValue | null>(null)

function applyCssVars(settings: SettingsMap) {
  const root = document.documentElement.style
  root.setProperty('--bg-enabled', settings['ui.background.enabled'] === 'true' ? '1' : '0')
  root.setProperty('--bg-brightness', settings['ui.background.brightness'] ?? '0.48')
  root.setProperty('--bg-veil', settings['ui.background.veil'] ?? '0.62')
  root.setProperty('--bg-blur', `${settings['ui.background.blur'] ?? '0'}px`)
}

export function SettingsProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<SettingsMap>(DEFAULTS)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const pending = useRef<number | null>(null)

  const refresh = useCallback(async () => {
    try {
      const data = await fetchSettings()
      setSettings((prev) => ({ ...DEFAULTS, ...prev, ...data }))
      setError('')
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    applyCssVars(settings)
  }, [settings])

  /** 本地立即生效，落到后端做 200ms 合并，避免拖动滑块刷请求。 */
  const update = useCallback(async (patch: SettingsMap) => {
    setSettings((prev) => ({ ...prev, ...patch }))
    if (pending.current !== null) {
      window.clearTimeout(pending.current)
    }
    pending.current = window.setTimeout(() => {
      void saveSettings(patch)
        .then((data) => setSettings((prev) => ({ ...DEFAULTS, ...prev, ...data })))
        .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)))
    }, 200)
  }, [])

  const upload = useCallback(
    async (file: File) => {
      const result = await uploadBackground(file)
      setSettings((prev) => ({
        ...prev,
        'ui.background.enabled': 'true',
        'ui.background.version': result.version,
      }))
    },
    [],
  )

  const remove = useCallback(async () => {
    await deleteBackground()
    setSettings((prev) => ({
      ...prev,
      'ui.background.enabled': 'false',
      'ui.background.version': '0',
    }))
  }, [])

  const value = useMemo<SettingsContextValue>(
    () => ({ settings, loading, error, update, upload, remove, refresh }),
    [settings, loading, error, update, upload, remove, refresh],
  )

  return <SettingsContext.Provider value={value}>{children}</SettingsContext.Provider>
}

export function useSettings(): SettingsContextValue {
  const ctx = useContext(SettingsContext)
  if (!ctx) {
    throw new Error('useSettings 必须在 SettingsProvider 内使用')
  }
  return ctx
}
