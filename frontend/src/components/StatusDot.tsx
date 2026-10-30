export type StatusTone = 'ok' | 'warn' | 'error' | 'running' | 'idle'

interface StatusDotProps {
  tone?: StatusTone
  label?: string
}

export function StatusDot({ tone = 'idle', label }: StatusDotProps) {
  return (
    <span className="status">
      <span className={`status-dot ${tone === 'idle' ? '' : tone}`} />
      {label ? <span>{label}</span> : null}
    </span>
  )
}
