import { backgroundUrl } from '@/api/settings'
import { useSettings } from '@/hooks/useSettings'

/**
 * 背景三层：图片 → 压暗 → 暗角。
 * 所有参数走 CSS 变量，设置页改参数时无需重渲染。
 */
export function BackgroundLayer() {
  const { settings } = useSettings()
  const enabled = settings['ui.background.enabled'] === 'true'
  const version = settings['ui.background.version'] ?? '0'
  const vignette = settings['ui.background.vignette'] !== 'false'

  return (
    <div className="bg-layer" aria-hidden="true">
      {enabled ? (
        <div
          className="bg-image"
          style={{ backgroundImage: `url("${backgroundUrl(version)}")` }}
        />
      ) : null}
      <div className="bg-veil" />
      {vignette ? <div className="bg-vignette" /> : null}
    </div>
  )
}
