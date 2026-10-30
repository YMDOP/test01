import { NavLink } from 'react-router-dom'

import { Kbd } from '@/components/Kbd'
import { NAV_ITEMS } from '@/navigation'

export function Sidebar() {
  return (
    <aside className="sidebar">
      <div className="brand">
        <span className="brand-mark" aria-hidden="true">
          &gt;_
        </span>
        <span className="brand-name">
          Interview<em>Forge</em>
        </span>
        <span className="brand-version">v0.1</span>
      </div>

      <nav className="nav" aria-label="主导航">
        <div className="nav-section">Workspace</div>
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            end={item.path === '/'}
            className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
          >
            <span className="nav-index">{item.index}</span>
            <span className="nav-label">{item.label}</span>
          </NavLink>
        ))}
      </nav>

      <div className="sidebar-footer">
        <div className="row">
          <span>命令面板</span>
          <Kbd>Ctrl</Kbd>
          <Kbd>K</Kbd>
        </div>
        <div className="row">
          <span>快速切换</span>
          <Kbd>1</Kbd>
          <span>—</span>
          <Kbd>7</Kbd>
        </div>
      </div>
    </aside>
  )
}
