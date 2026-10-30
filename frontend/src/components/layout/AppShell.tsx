import { useEffect, useMemo, useState } from 'react'
import { Outlet, useNavigate } from 'react-router-dom'

import { BackgroundLayer } from '@/components/BackgroundLayer'
import { CommandPalette, type PaletteCommand } from '@/components/CommandPalette'
import { Sidebar } from '@/components/layout/Sidebar'
import { TopBar } from '@/components/layout/TopBar'
import { NAV_ITEMS } from '@/navigation'

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  const tag = target.tagName.toLowerCase()
  return tag === 'input' || tag === 'textarea' || target.isContentEditable
}

export function AppShell() {
  const navigate = useNavigate()
  const [paletteOpen, setPaletteOpen] = useState(false)

  const commands = useMemo<PaletteCommand[]>(() => {
    const navCommands = NAV_ITEMS.map((item) => ({
      id: item.path,
      label: `跳转 · ${item.label}`,
      hint: item.hint,
      run: () => navigate(item.path),
    }))
    return [
      ...navCommands,
      {
        id: 'background',
        label: '调整外观 · 背景图',
        hint: 'Settings',
        run: () => navigate('/settings'),
      },
    ]
  }, [navigate])

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      const meta = event.ctrlKey || event.metaKey
      if (meta && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setPaletteOpen((open) => !open)
        return
      }
      if (isTypingTarget(event.target)) return
      if (event.key >= '1' && event.key <= '7') {
        const target = NAV_ITEMS[Number(event.key) - 1]
        if (target) {
          event.preventDefault()
          navigate(target.path)
        }
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [navigate])

  return (
    <>
      <BackgroundLayer />
      <div className="atmosphere" aria-hidden="true" />
      <div className="app-shell">
        <Sidebar />
        <div className="main">
          <TopBar onOpenPalette={() => setPaletteOpen(true)} />
          <main className="content">
            <Outlet />
          </main>
        </div>
      </div>
      <CommandPalette open={paletteOpen} commands={commands} onClose={() => setPaletteOpen(false)} />
    </>
  )
}
