import type { ReactNode } from 'react'

interface PageHeadProps {
  tag: string
  title: string
  desc: string
  actions?: ReactNode
}

export function PageHead({ tag, title, desc, actions }: PageHeadProps) {
  return (
    <div className="page-head">
      <div>
        <p className="mono-label">// {tag}</p>
        <h1 className="page-title">{title}</h1>
        <p className="page-desc">{desc}</p>
      </div>
      {actions ? <div className="page-head-actions">{actions}</div> : null}
    </div>
  )
}
