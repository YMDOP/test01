export interface NavItem {
  path: string
  index: string
  label: string
  hint: string
}

export const NAV_ITEMS: NavItem[] = [
  { path: '/', index: '01', label: '仪表盘', hint: 'Dashboard' },
  { path: '/library', index: '02', label: '题库中心', hint: 'Library' },
  { path: '/practice', index: '03', label: '练习模式', hint: 'Practice' },
  { path: '/interview', index: '04', label: '模拟面试', hint: 'Interview' },
  { path: '/wrongbook', index: '05', label: '错题本', hint: 'Wrong Book' },
  { path: '/stats', index: '06', label: '进度统计', hint: 'Stats' },
  { path: '/settings', index: '07', label: '设置', hint: 'Settings' },
]
