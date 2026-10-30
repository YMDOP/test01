import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'

import { AppShell } from '@/components/layout/AppShell'
import { DashboardPage } from '@/pages/DashboardPage'
import { InterviewPage } from '@/pages/InterviewPage'
import { LibraryPage } from '@/pages/LibraryPage'
import { PracticePage } from '@/pages/PracticePage'
import { SettingsPage } from '@/pages/SettingsPage'
import { StatsPage } from '@/pages/StatsPage'
import { WrongBookPage } from '@/pages/WrongBookPage'
import { SettingsProvider } from '@/hooks/useSettings'

export default function App() {
  return (
    <BrowserRouter>
      <SettingsProvider>
        <Routes>
          <Route element={<AppShell />}>
            <Route index element={<DashboardPage />} />
            <Route path="library" element={<LibraryPage />} />
            <Route path="practice" element={<PracticePage />} />
            <Route path="interview" element={<InterviewPage />} />
            <Route path="wrongbook" element={<WrongBookPage />} />
            <Route path="stats" element={<StatsPage />} />
            <Route path="settings" element={<SettingsPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </SettingsProvider>
    </BrowserRouter>
  )
}
