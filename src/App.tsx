import React from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { PlayerProvider } from './components/PlayerProvider'
import NavBar from './components/NavBar'
import PlayerPage from './pages/PlayerPage'

export default function App() {
  return (
    <PlayerProvider>
      <BrowserRouter>
        <NavBar />
        <Routes>
          <Route path="/player" element={<PlayerPage />} />
          <Route path="*" element={<Navigate to="/player" replace />} />
        </Routes>
      </BrowserRouter>
    </PlayerProvider>
  )
}