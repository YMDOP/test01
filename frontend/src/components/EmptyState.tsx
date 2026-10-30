import type { ReactNode } from 'react'

interface EmptyStateProps {
  mark?: string
  title: string
  description?: string
  action?: ReactNode
}

export function EmptyState({ mark = '[]', title, description, action }: EmptyStateProps) {
  return (
    <div className="empty">
      <div className="empty-mark" aria-hidden="true">
        {mark}
      </div>
      <p className="empty-title">{title}</p>
      {description ? <p className="empty-desc">{description}</p> : null}
      {action ? <div className="empty-action">{action}</div> : null}
    </div>
  )
}
