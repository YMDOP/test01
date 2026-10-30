interface MeterBarProps {
  label: string
  value: number
  max?: number
  tone?: 'accent' | 'warn' | 'danger' | 'info'
  suffix?: string
}

export function MeterBar({ label, value, max = 100, tone = 'accent', suffix = '%' }: MeterBarProps) {
  const ratio = max <= 0 ? 0 : Math.min(1, Math.max(0, value / max))
  const toneClass = tone === 'accent' ? '' : tone
  return (
    <div className="meter">
      <div className="meter-head">
        <span className="meter-label">{label}</span>
        <span className="meter-value">
          {Math.round(ratio * 100)}
          {suffix}
        </span>
      </div>
      <div className="meter-track">
        <div className={`meter-fill ${toneClass}`.trim()} style={{ width: `${ratio * 100}%` }} />
      </div>
    </div>
  )
}
