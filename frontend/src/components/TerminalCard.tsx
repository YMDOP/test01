import type { ReactNode } from 'react'

interface TerminalCardProps {
  title?: string
  subtitle?: string
  actions?: ReactNode
  children: ReactNode
  corners?: boolean
  tight?: boolean
  className?: string
}

export function TerminalCard({
  title,
  subtitle,
  actions,
  children,
  corners = false,
  tight = false,
  className = '',
}: TerminalCardProps) {
  const hasHead = Boolean(title || subtitle || actions)
  return (
    <section className={`card${corners ? ' corners' : ''} ${className}`.trim()}>
      {hasHead && (
        <header className="card-head">
          {title ? <h2 className="card-title">{title}</h2> : null}
          {subtitle ? <span className="card-subtitle">{subtitle}</span> : null}
          {actions ? <div className="card-actions">{actions}</div> : null}
        </header>
      )}
      <div className={`card-body${tight ? ' tight' : ''}`}>{children}</div>
    </section>
  )
}
