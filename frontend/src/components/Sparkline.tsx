interface SparklineProps {
  points: number[]
  height?: number
  /** 没有数据时显示的占位文案 */
  placeholder?: string
}

/**
 * 自绘折线，不引入图表库。points 为空时显示占位而不是空白。
 */
export function Sparkline({ points, height = 64, placeholder = '暂无数据' }: SparklineProps) {
  if (points.length < 2) {
    return (
      <div
        style={{
          height,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 11,
          color: 'var(--faint)',
        }}
      >
        {placeholder}
      </div>
    )
  }

  const width = 100
  const max = Math.max(...points)
  const min = Math.min(...points)
  const span = max - min || 1
  const step = width / (points.length - 1)

  const coords = points.map((value, index) => {
    const x = index * step
    const y = height - ((value - min) / span) * (height - 8) - 4
    return `${x.toFixed(2)},${y.toFixed(2)}`
  })

  const line = `M ${coords.join(' L ')}`
  const area = `${line} L ${width},${height} L 0,${height} Z`

  return (
    <svg
      className="sparkline"
      viewBox={`0 0 ${width} ${height}`}
      preserveAspectRatio="none"
      role="img"
      aria-label="趋势图"
    >
      <defs>
        <linearGradient id="spark-fill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.28" />
          <stop offset="100%" stopColor="var(--accent)" stopOpacity="0" />
        </linearGradient>
      </defs>
      <line className="sparkline-axis" x1="0" y1={height - 1} x2={width} y2={height - 1} />
      <path className="sparkline-area" d={area} />
      <path className="sparkline-line" d={line} />
    </svg>
  )
}
