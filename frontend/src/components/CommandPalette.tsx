import { useEffect, useMemo, useRef, useState } from 'react'

import { Kbd } from '@/components/Kbd'

export interface PaletteCommand {
  id: string
  label: string
  hint?: string
  run: () => void
}

interface CommandPaletteProps {
  open: boolean
  commands: PaletteCommand[]
  onClose: () => void
}

export function CommandPalette({ open, commands, onClose }: CommandPaletteProps) {
  const [query, setQuery] = useState('')
  const [activeIndex, setActiveIndex] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)

  const filtered = useMemo(() => {
    const keyword = query.trim().toLowerCase()
    if (!keyword) return commands
    return commands.filter(
      (command) =>
        command.label.toLowerCase().includes(keyword) ||
        (command.hint ?? '').toLowerCase().includes(keyword),
    )
  }, [commands, query])

  useEffect(() => {
    if (open) {
      setQuery('')
      setActiveIndex(0)
      window.setTimeout(() => inputRef.current?.focus(), 10)
    }
  }, [open])

  useEffect(() => {
    setActiveIndex(0)
  }, [query])

  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
      } else if (event.key === 'ArrowDown') {
        event.preventDefault()
        setActiveIndex((index) => (filtered.length === 0 ? 0 : (index + 1) % filtered.length))
      } else if (event.key === 'ArrowUp') {
        event.preventDefault()
        setActiveIndex((index) =>
          filtered.length === 0 ? 0 : (index - 1 + filtered.length) % filtered.length,
        )
      } else if (event.key === 'Enter') {
        event.preventDefault()
        const target = filtered[activeIndex]
        if (target) {
          target.run()
          onClose()
        }
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, filtered, activeIndex, onClose])

  if (!open) return null

  return (
    <div
      className="palette-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div className="palette" role="dialog" aria-modal="true" aria-label="命令面板">
        <div className="palette-search">
          <span className="prompt" aria-hidden="true">
            &gt;
          </span>
          <input
            ref={inputRef}
            className="palette-input"
            value={query}
            placeholder="跳转页面、执行命令…"
            onChange={(event) => setQuery(event.target.value)}
            aria-label="命令搜索"
          />
          <Kbd>ESC</Kbd>
        </div>
        <div className="palette-list">
          {filtered.length === 0 ? (
            <div className="palette-empty">没有匹配的命令</div>
          ) : (
            filtered.map((command, index) => (
              <div
                key={command.id}
                className={`palette-item${index === activeIndex ? ' active' : ''}`}
                onMouseEnter={() => setActiveIndex(index)}
                onMouseDown={(event) => {
                  event.preventDefault()
                  command.run()
                  onClose()
                }}
              >
                <span>{command.label}</span>
                {command.hint ? <span className="hint">{command.hint}</span> : null}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  )
}
