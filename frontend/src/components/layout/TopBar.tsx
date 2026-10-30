import { useLocation } from 'react-router-dom'

import { Kbd } from '@/components/Kbd'
import { StatusDot } from '@/components/StatusDot'
import { useSettings } from '@/hooks/useSettings'
import { NAV_ITEMS } from '@/navigation'

interface TopBarProps {
  onOpenPalette: () => void
}

export function TopBar({ onOpenPalette }: TopBarProps) {
  const location = useLocation()
  const { error, loading } = useSettings()

  const current =
    NAV_ITEMS.find((item) =>
      item.path === '/' ? location.pathname === '/' : location.pathname.startsWith(item.path),
    ) ?? NAV_ITEMS[0]

  const tone = error ? 'error' : loading ? 'running' : 'ok'
  const label = error ? 'BACKEND OFFLINE' : loading ? 'SYNCING' : 'LOCAL'

  return (
    <header className="topbar">
      <div className="breadcrumb">
        <span>//</span>
        <span>workspace</span>
        <span>/</span>
        <strong>{current.hint}</strong>
      </div>

      <div className="topbar-spacer" />

      <StatusDot tone={tone} label={label} />

      <button type="button" className="topbar-action" onClick={onOpenPalette}>
        <span>命令</span>
        <Kbd>Ctrl</Kbd>
        <Kbd>K</Kbd>
      </button>
    </header>
  )
}
